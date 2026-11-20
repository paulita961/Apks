package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LedgerRepository
import com.example.domain.AppNotice
import com.example.domain.AppSettings
import com.example.domain.BillCounterState
import com.example.domain.CalculatorHistoryItem
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.CommissionWallet
import com.example.domain.CupDenominations
import com.example.domain.DbConnectionStatus
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.LedgerSummary
import com.example.domain.NotificationPreferences
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.domain.PersonSummary
import com.example.export.BackupManager
import com.example.export.BackupPayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val isLoading: Boolean = true,
    val persons: List<Person> = emptyList(),
    val operations: List<Operation> = emptyList(),
    val commissionMovements: List<CommissionMovement> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val calculatorHistory: List<CalculatorHistoryItem> = emptyList(),
    val billCounterState: BillCounterState = BillCounterState(),
    val globalSummary: LedgerSummary = LedgerSummary(),
    val personSummaries: List<PersonSummary> = emptyList(),
    val activePersonSummaries: List<PersonSummary> = emptyList(),
    val archivedPersonSummaries: List<PersonSummary> = emptyList(),
    val commissionWallet: CommissionWallet = CommissionWallet(),
    val showBackupBanner: Boolean = false,
    val isSupabaseConfigured: Boolean = false,
    val dbStatus: DbConnectionStatus = DbConnectionStatus.CHECKING,
    val dbErrorMessage: String? = null,
    val allNotices: List<AppNotice> = emptyList(),
    val visibleNotices: List<AppNotice> = emptyList(),
    val hiddenNoticeCount: Int = 0
)

class MainViewModel(
    private val repository: LedgerRepository
) : ViewModel() {

    private val _dbStatus = MutableStateFlow(
        if (repository.isSupabaseConfigured()) DbConnectionStatus.CHECKING
        else DbConnectionStatus.NOT_CONFIGURED
    )
    private val _dbErrorMessage = MutableStateFlow<String?>(null)
    private val _dismissedNoticeIds = MutableStateFlow<Set<String>>(emptySet())
    private var debounceSyncJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initDefaults()
            if (repository.isSupabaseConfigured()) {
                checkConnectionAndSync()
            }
        }
        viewModelScope.launch {
            var previousAvailable: Boolean? = null
            repository.observeNetworkAvailable()
                .distinctUntilChanged()
                .collect { available ->
                    if (!repository.isSupabaseConfigured()) {
                        _dbStatus.value = DbConnectionStatus.NOT_CONFIGURED
                        _dbErrorMessage.value = null
                    } else if (!available) {
                        _dbStatus.value = DbConnectionStatus.OFFLINE
                        _dbErrorMessage.value = "Sin conexión a internet."
                    } else if (previousAvailable == false) {
                        // Recuperó conectividad: comprobar y sincronizar automáticamente
                        checkConnectionAndSync()
                    }
                    previousAvailable = available
                }
        }
    }

    private data class CoreStreams(
        val persons: List<Person>,
        val operations: List<Operation>,
        val movements: List<CommissionMovement>,
        val settings: AppSettings
    )

    private data class SyncUiMeta(
        val status: DbConnectionStatus,
        val errorMessage: String?,
        val dismissedIds: Set<String>
    )

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            repository.personsFlow,
            repository.operationsFlow,
            repository.commissionMovementsFlow,
            repository.settingsFlow
        ) { persons, ops, movements, settings ->
            CoreStreams(persons, ops, movements, settings)
        },
        repository.calculatorHistoryFlow,
        repository.billCounterStateFlow,
        combine(_dbStatus, _dbErrorMessage, _dismissedNoticeIds) { status, error, dismissed ->
            SyncUiMeta(status, error, dismissed)
        }
    ) { core, calcHistory, billState, syncMeta ->
        val globalSummary = FinanceCalculator.computeSummary(core.operations)
        val personSummaries = FinanceCalculator.computePersonSummaries(core.persons, core.operations)
            .sortedBy { it.person.name.lowercase() }
        val activeSummaries = personSummaries.filter { !it.person.archived }
        val archivedSummaries = personSummaries.filter { it.person.archived }
        val wallet = FinanceCalculator.computeCommissionWallet(
            comisionCobrada = globalSummary.comisionCobrada,
            movements = core.movements
        )
        val referenceTimestamp = core.settings.lastBackupAt ?: core.settings.firstUsedAt
        val daysElapsed = Formatters.daysSince(referenceTimestamp)
        val reminderDays = core.settings.notifications.backupReminderDays
        val showBanner = reminderDays > 0 &&
            core.operations.isNotEmpty() &&
            referenceTimestamp.isNotBlank() &&
            daysElapsed > reminderDays.toLong()

        val configured = repository.isSupabaseConfigured()
        val allNotices = FinanceCalculator.evaluateNotices(
            personSummaries = personSummaries,
            operations = core.operations,
            settings = core.settings,
            isSupabaseConfigured = configured
        )
        val visibleNotices = allNotices.filter { it.id !in syncMeta.dismissedIds }
        val hiddenCount = (allNotices.size - visibleNotices.size).coerceAtLeast(0)

        MainUiState(
            isLoading = false,
            persons = core.persons,
            operations = core.operations,
            commissionMovements = core.movements,
            settings = core.settings,
            calculatorHistory = calcHistory,
            billCounterState = billState,
            globalSummary = globalSummary,
            personSummaries = personSummaries,
            activePersonSummaries = activeSummaries,
            archivedPersonSummaries = archivedSummaries,
            commissionWallet = wallet,
            showBackupBanner = showBanner,
            isSupabaseConfigured = configured,
            dbStatus = syncMeta.status,
            dbErrorMessage = syncMeta.errorMessage,
            allNotices = allNotices,
            visibleNotices = visibleNotices,
            hiddenNoticeCount = hiddenCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            isSupabaseConfigured = repository.isSupabaseConfigured(),
            dbStatus = if (repository.isSupabaseConfigured()) {
                DbConnectionStatus.CHECKING
            } else {
                DbConnectionStatus.NOT_CONFIGURED
            }
        )
    )

    private fun scheduleDebouncedSync() {
        if (!repository.isSupabaseConfigured()) return
        debounceSyncJob?.cancel()
        debounceSyncJob = viewModelScope.launch {
            delay(2000L)
            if (repository.isNetworkAvailable()) {
                performSyncInternal()
            } else {
                _dbStatus.value = DbConnectionStatus.OFFLINE
                _dbErrorMessage.value = "Sin conexión a internet."
            }
        }
    }

    fun checkConnectionAndSync() {
        viewModelScope.launch {
            checkConnectionAndSyncInternal()
        }
    }

    suspend fun checkConnectionAndSyncWithFeedback(): String {
        val status = checkConnectionAndSyncInternal()
        return when (status) {
            DbConnectionStatus.CONNECTED -> "Base de datos activa y sincronizada."
            DbConnectionStatus.NOT_CONFIGURED -> "Sincronización no configurada en credenciales."
            DbConnectionStatus.OFFLINE -> "Sin conexión a internet. Tus cambios siguen guardados localmente."
            DbConnectionStatus.ERROR -> _dbErrorMessage.value ?: "Error al conectar con la base de datos."
            DbConnectionStatus.CHECKING -> "Comprobando conexión..."
            DbConnectionStatus.SYNCING -> "Sincronizando..."
        }
    }

    private suspend fun checkConnectionAndSyncInternal(): DbConnectionStatus {
        if (!repository.isSupabaseConfigured()) {
            _dbStatus.value = DbConnectionStatus.NOT_CONFIGURED
            _dbErrorMessage.value = null
            return DbConnectionStatus.NOT_CONFIGURED
        }
        if (!repository.isNetworkAvailable()) {
            _dbStatus.value = DbConnectionStatus.OFFLINE
            _dbErrorMessage.value = "Sin conexión a internet."
            return DbConnectionStatus.OFFLINE
        }
        _dbStatus.value = DbConnectionStatus.CHECKING
        _dbErrorMessage.value = null
        val (status, errorMsg) = repository.checkSupabaseConnection()
        if (status == DbConnectionStatus.CONNECTED) {
            return performSyncInternal()
        } else {
            _dbStatus.value = status
            _dbErrorMessage.value = errorMsg
            return status
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            performSyncInternal()
        }
    }

    suspend fun syncNowWithFeedback(): String {
        val resultStatus = performSyncInternal()
        return when (resultStatus) {
            DbConnectionStatus.CONNECTED -> "Sincronización completada correctamente."
            DbConnectionStatus.NOT_CONFIGURED -> "Sincronización en la nube no configurada."
            DbConnectionStatus.OFFLINE -> "Sin conexión a internet. Los datos locales se subirán al volver la red."
            DbConnectionStatus.ERROR -> _dbErrorMessage.value ?: "No se pudo sincronizar con Supabase."
            else -> "Sincronización en progreso."
        }
    }

    private suspend fun performSyncInternal(): DbConnectionStatus {
        if (!repository.isSupabaseConfigured()) {
            _dbStatus.value = DbConnectionStatus.NOT_CONFIGURED
            _dbErrorMessage.value = null
            return DbConnectionStatus.NOT_CONFIGURED
        }
        if (!repository.isNetworkAvailable()) {
            _dbStatus.value = DbConnectionStatus.OFFLINE
            _dbErrorMessage.value = "Sin conexión a internet."
            return DbConnectionStatus.OFFLINE
        }
        _dbStatus.value = DbConnectionStatus.SYNCING
        _dbErrorMessage.value = null
        val result = repository.syncWithSupabase()
        return result.fold(
            onSuccess = {
                _dbStatus.value = DbConnectionStatus.CONNECTED
                _dbErrorMessage.value = null
                DbConnectionStatus.CONNECTED
            },
            onFailure = { err ->
                val offline = !repository.isNetworkAvailable()
                val nextStatus = if (offline) DbConnectionStatus.OFFLINE else DbConnectionStatus.ERROR
                _dbStatus.value = nextStatus
                _dbErrorMessage.value = err.localizedMessage ?: "Error al sincronizar"
                nextStatus
            }
        )
    }

    fun dismissNotice(noticeId: String) {
        _dismissedNoticeIds.value = _dismissedNoticeIds.value + noticeId
    }

    fun restoreHiddenNotices() {
        _dismissedNoticeIds.value = emptySet()
    }

    fun savePerson(
        editingPerson: Person?,
        name: String,
        receiptName: String?,
        commissionRate: Double,
        defaultMode: CommissionMode
    ) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return@launch
            val cleanReceipt = receiptName?.trim()?.takeIf { it.isNotEmpty() }
            val clampedRate = commissionRate.coerceIn(0.0, 100.0)
            val now = Formatters.nowEpochMs()
            val person = if (editingPerson != null) {
                editingPerson.copy(
                    name = cleanName,
                    receiptName = cleanReceipt,
                    commissionRate = Formatters.round2(clampedRate),
                    defaultCommissionMode = defaultMode,
                    updatedAt = now
                )
            } else {
                Person(
                    id = UUID.randomUUID().toString(),
                    name = cleanName,
                    receiptName = cleanReceipt,
                    commissionRate = Formatters.round2(clampedRate),
                    defaultCommissionMode = defaultMode,
                    archived = false,
                    createdAt = now,
                    updatedAt = now
                )
            }
            repository.upsertPerson(person)
            scheduleDebouncedSync()
        }
    }

    fun toggleArchivePerson(person: Person) {
        viewModelScope.launch {
            repository.upsertPerson(
                person.copy(
                    archived = !person.archived,
                    updatedAt = Formatters.nowEpochMs()
                )
            )
            scheduleDebouncedSync()
        }
    }

    fun deletePerson(personId: String) {
        viewModelScope.launch {
            repository.deletePerson(personId)
            scheduleDebouncedSync()
        }
    }

    fun updatePersonReceiptName(person: Person, receiptName: String) {
        viewModelScope.launch {
            val clean = receiptName.trim().takeIf { it.isNotEmpty() }
            repository.upsertPerson(
                person.copy(
                    receiptName = clean,
                    updatedAt = Formatters.nowEpochMs()
                )
            )
            scheduleDebouncedSync()
        }
    }

    fun saveOperation(
        editingOp: Operation?,
        personId: String,
        type: OperationType,
        date: String,
        amount: Double,
        commissionRateSnapshot: Double?,
        commissionMode: CommissionMode?,
        commissionAmount: Double?,
        note: String?,
        billBreakdown: Map<Int, Int>?
    ) {
        viewModelScope.launch {
            val cleanBreakdown = if (type == OperationType.EXTRACCION || type == OperationType.ENTREGA) {
                billBreakdown?.filterValues { it > 0 }?.takeIf { it.isNotEmpty() }
            } else null
            val now = Formatters.nowEpochMs()

            val op = Operation(
                id = editingOp?.id ?: UUID.randomUUID().toString(),
                personId = personId,
                type = type,
                date = Formatters.normalizeIsoDateTime(date),
                amount = Formatters.round2(amount),
                commissionRateSnapshot = if (type == OperationType.EXTRACCION) commissionRateSnapshot else null,
                commissionMode = if (type == OperationType.EXTRACCION) commissionMode else null,
                commissionAmount = if (type == OperationType.EXTRACCION) commissionAmount?.let { Formatters.round2(it) } else null,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                billBreakdown = cleanBreakdown,
                createdAt = editingOp?.createdAt ?: now,
                updatedAt = now
            )
            repository.upsertOperation(op)
            scheduleDebouncedSync()
        }
    }

    fun deleteOperation(operationId: String) {
        viewModelScope.launch {
            repository.deleteOperation(operationId)
            scheduleDebouncedSync()
        }
    }

    fun saveCommissionMovement(
        editingMovement: CommissionMovement?,
        date: String,
        amount: Double,
        category: CommissionMovementCategory,
        note: String?
    ) {
        viewModelScope.launch {
            val now = Formatters.nowEpochMs()
            val mov = CommissionMovement(
                id = editingMovement?.id ?: UUID.randomUUID().toString(),
                date = Formatters.normalizeIsoDateTime(date),
                amount = Formatters.round2(amount),
                category = category,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = editingMovement?.createdAt ?: now,
                updatedAt = now
            )
            repository.upsertCommissionMovement(mov)
            scheduleDebouncedSync()
        }
    }

    fun deleteCommissionMovement(movementId: String) {
        viewModelScope.launch {
            repository.deleteCommissionMovement(movementId)
            scheduleDebouncedSync()
        }
    }

    fun setDailyCardLimit(limit: Double?) {
        viewModelScope.launch {
            repository.setDailyCardLimit(limit?.let { Formatters.round2(it) })
            scheduleDebouncedSync()
        }
    }

    fun toggleDenomination(denomination: Int) {
        viewModelScope.launch {
            val current = uiState.value.settings.visibleDenominations
            val updated = if (denomination in current) {
                if (current.size <= 1) current else current - denomination
            } else {
                CupDenominations.ALL.filter { it == denomination || it in current }
            }
            repository.setVisibleDenominations(updated)
            scheduleDebouncedSync()
        }
    }

    fun setNotificationPreferences(notifications: NotificationPreferences) {
        viewModelScope.launch {
            repository.setNotificationPreferences(notifications)
            scheduleDebouncedSync()
        }
    }

    fun setSyncWorkspaceId(workspaceId: String) {
        viewModelScope.launch {
            repository.setSyncWorkspaceId(workspaceId)
            if (repository.isSupabaseConfigured() && repository.isNetworkAvailable()) {
                performSyncInternal()
            }
        }
    }

    fun updateBillCounter(counts: Map<Int, Int>, expectedAmount: String) {
        viewModelScope.launch {
            repository.updateBillCounterState(counts, expectedAmount)
        }
    }

    fun clearBillCounter() {
        viewModelScope.launch {
            repository.updateBillCounterState(emptyMap(), "")
        }
    }

    fun addCalculatorHistory(expression: String, result: String) {
        viewModelScope.launch {
            repository.addCalculatorHistoryItem(
                CalculatorHistoryItem(
                    id = UUID.randomUUID().toString(),
                    expression = expression,
                    result = result,
                    timestamp = Formatters.currentIsoDateTime()
                )
            )
        }
    }

    fun clearCalculatorHistory() {
        viewModelScope.launch {
            repository.clearCalculatorHistory()
        }
    }

    suspend fun exportBackupJsonAndMark(): String {
        val snapshot = repository.snapshotForBackup()
        val now = repository.markBackupNow()
        scheduleDebouncedSync()
        return BackupManager.exportBackupJson(snapshot, now)
    }

    suspend fun exportCsvString(): String {
        val snapshot = repository.snapshotForBackup()
        return BackupManager.exportCsv(snapshot)
    }

    fun parseBackupJson(raw: String): Result<BackupPayload> =
        BackupManager.parseAndValidateBackupJson(raw)

    suspend fun restoreFromBackup(payload: BackupPayload) {
        repository.replaceAllFromBackup(
            persons = payload.persons,
            operations = payload.operations,
            movements = payload.commissionMovements,
            settings = payload.settings
        )
        scheduleDebouncedSync()
    }

    companion object {
        fun factory(repository: LedgerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
    }
}
