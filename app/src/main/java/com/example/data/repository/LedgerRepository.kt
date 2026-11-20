package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.db.AppDatabase
import com.example.data.db.SyncTombstoneEntity
import com.example.data.db.toDomain
import com.example.data.db.toEntity
import com.example.data.prefs.PreferencesDataStore
import com.example.data.sync.SupabaseSyncService
import com.example.data.sync.SyncMergeEngine
import com.example.data.sync.SyncStateSnapshot
import com.example.domain.AppSettings
import com.example.domain.BillCounterState
import com.example.domain.CalculatorHistoryItem
import com.example.domain.CommissionMovement
import com.example.domain.DbConnectionStatus
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.NotificationPreferences
import com.example.domain.Operation
import com.example.domain.Person
import com.example.domain.SyncEntityType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LedgerRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: PreferencesDataStore,
    private val syncService: SupabaseSyncService
) {
    private val personDao = database.personDao()
    private val operationDao = database.operationDao()
    private val movementDao = database.commissionMovementDao()
    private val tombstoneDao = database.syncTombstoneDao()
    private val backupTxDao = database.backupTransactionDao()
    private val syncMutex = Mutex()

    val personsFlow: Flow<List<Person>> = personDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val operationsFlow: Flow<List<Operation>> = operationDao.observeAll().map { list ->
        list.map { it.toDomain() }.sortedWith(FinanceCalculator.operationDescendingComparator)
    }

    val commissionMovementsFlow: Flow<List<CommissionMovement>> = movementDao.observeAll().map { list ->
        list.map { it.toDomain() }.sortedWith(FinanceCalculator.movementDescendingComparator)
    }

    val settingsFlow: Flow<AppSettings> = preferences.settingsFlow
    val calculatorHistoryFlow: Flow<List<CalculatorHistoryItem>> = preferences.calculatorHistoryFlow
    val billCounterStateFlow: Flow<BillCounterState> = preferences.billCounterStateFlow

    suspend fun initDefaults() {
        preferences.ensureFirstUsedInitialized()
        val cutoff = Formatters.nowEpochMs() - SyncMergeEngine.TOMBSTONE_RETENTION_MS
        tombstoneDao.purgeOlderThan(cutoff)
    }

    fun isSupabaseConfigured(): Boolean = syncService.isConfigured()

    fun isNetworkAvailable(): Boolean = syncService.isNetworkAvailable()

    suspend fun checkSupabaseConnection(): Pair<DbConnectionStatus, String?> =
        syncService.checkConnection()

    fun observeNetworkAvailable(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(syncService.isNetworkAvailable())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(syncService.isNetworkAvailable())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    suspend fun upsertPerson(person: Person) {
        personDao.upsert(person.toEntity())
        preferences.setHasPendingSync(true)
    }

    suspend fun deletePerson(personId: String) {
        val settings = preferences.getSettingsOnce()
        val wsId = settings.syncWorkspaceId
        val now = Formatters.nowEpochMs()
        val opIds = operationDao.getIdsForPerson(personId)
        val tombstones = buildList {
            add(SyncTombstoneEntity(wsId, SyncEntityType.PERSON.wireValue, personId, now))
            opIds.forEach { opId ->
                add(SyncTombstoneEntity(wsId, SyncEntityType.OPERATION.wireValue, opId, now))
            }
        }
        tombstoneDao.upsertAll(tombstones)
        personDao.deleteById(personId)
        preferences.setHasPendingSync(true)
    }

    suspend fun upsertOperation(operation: Operation) {
        operationDao.upsert(operation.toEntity())
        preferences.setHasPendingSync(true)
    }

    suspend fun deleteOperation(operationId: String) {
        val settings = preferences.getSettingsOnce()
        val wsId = settings.syncWorkspaceId
        val now = Formatters.nowEpochMs()
        tombstoneDao.upsert(
            SyncTombstoneEntity(wsId, SyncEntityType.OPERATION.wireValue, operationId, now)
        )
        operationDao.deleteById(operationId)
        preferences.setHasPendingSync(true)
    }

    suspend fun upsertCommissionMovement(movement: CommissionMovement) {
        movementDao.upsert(movement.toEntity())
        preferences.setHasPendingSync(true)
    }

    suspend fun deleteCommissionMovement(movementId: String) {
        val settings = preferences.getSettingsOnce()
        val wsId = settings.syncWorkspaceId
        val now = Formatters.nowEpochMs()
        tombstoneDao.upsert(
            SyncTombstoneEntity(wsId, SyncEntityType.COMMISSION_MOVEMENT.wireValue, movementId, now)
        )
        movementDao.deleteById(movementId)
        preferences.setHasPendingSync(true)
    }

    suspend fun setDailyCardLimit(limit: Double?) {
        preferences.setDailyCardLimit(limit)
    }

    suspend fun setVisibleDenominations(denominations: List<Int>) {
        preferences.setVisibleDenominations(denominations)
    }

    suspend fun setNotificationPreferences(notifications: NotificationPreferences) {
        preferences.setNotificationPreferences(notifications)
    }

    suspend fun setSyncWorkspaceId(workspaceId: String) {
        preferences.setSyncWorkspaceId(workspaceId)
        preferences.setHasPendingSync(true)
    }

    suspend fun markBackupNow(): String {
        val now = Formatters.currentIsoTimestamp()
        preferences.setLastBackupAt(now)
        return now
    }

    suspend fun addCalculatorHistoryItem(item: CalculatorHistoryItem) {
        preferences.addCalculatorHistoryItem(item)
    }

    suspend fun clearCalculatorHistory() {
        preferences.clearCalculatorHistory()
    }

    suspend fun updateBillCounterState(counts: Map<Int, Int>, expectedAmount: String) {
        preferences.updateBillCounterState(counts, expectedAmount)
    }

    suspend fun syncWithSupabase(): Result<Unit> = syncMutex.withLock {
        val settings = preferences.getSettingsOnce()
        val wsId = Formatters.sanitizeWorkspaceId(settings.syncWorkspaceId)
        val localSnapshot = SyncStateSnapshot(
            workspaceId = wsId,
            persons = personDao.getAllOnce().map { it.toDomain() },
            operations = operationDao.getAllOnce().map { it.toDomain() },
            movements = movementDao.getAllOnce().map { it.toDomain() },
            tombstones = tombstoneDao.getByWorkspace(wsId).mapNotNull { it.toDomain() },
            settings = settings
        )

        val syncResult = syncService.syncBidirectional(localSnapshot)
        syncResult.map { merged ->
            backupTxDao.applyMergedSyncData(
                personDao = personDao,
                operationDao = operationDao,
                movementDao = movementDao,
                tombstoneDao = tombstoneDao,
                workspaceId = wsId,
                persons = merged.mergedPersons.map { it.toEntity() },
                operations = merged.mergedOperations.map { it.toEntity() },
                movements = merged.mergedMovements.map { it.toEntity() },
                tombstones = merged.mergedTombstones.map { it.toEntity() }
            )
            preferences.applySettingsFromSyncOrBackup(
                settings = merged.mergedSettings,
                markPendingSync = false,
                syncedAtEpoch = merged.mergedSettings.lastSyncedAt
            )
        }
    }

    suspend fun snapshotForBackup(): BackupSnapshot {
        val persons = personDao.getAllOnce().map { it.toDomain() }
        val operations = operationDao.getAllOnce().map { it.toDomain() }
            .sortedWith(FinanceCalculator.operationDescendingComparator)
        val movements = movementDao.getAllOnce().map { it.toDomain() }
            .sortedWith(FinanceCalculator.movementDescendingComparator)
        val settings = preferences.getSettingsOnce()
        return BackupSnapshot(persons, operations, movements, settings)
    }

    suspend fun replaceAllFromBackup(
        persons: List<Person>,
        operations: List<Operation>,
        movements: List<CommissionMovement>,
        settings: AppSettings
    ) {
        val currentSettings = preferences.getSettingsOnce()
        val now = Formatters.nowEpochMs()
        val wsId = Formatters.sanitizeWorkspaceId(
            settings.syncWorkspaceId.ifBlank { currentSettings.syncWorkspaceId }
        )

        // Create tombstones for any previously existing local IDs removed by restore
        val newPersonIds = persons.map { it.id }.toSet()
        val newOpIds = operations.map { it.id }.toSet()
        val newMovIds = movements.map { it.id }.toSet()

        val oldPersons = personDao.getAllOnce().filter { it.id !in newPersonIds }
        val oldOps = operationDao.getAllOnce().filter { it.id !in newOpIds }
        val oldMovs = movementDao.getAllOnce().filter { it.id !in newMovIds }

        val tombstones = buildList {
            oldPersons.forEach { add(SyncTombstoneEntity(wsId, SyncEntityType.PERSON.wireValue, it.id, now)) }
            oldOps.forEach { add(SyncTombstoneEntity(wsId, SyncEntityType.OPERATION.wireValue, it.id, now)) }
            oldMovs.forEach { add(SyncTombstoneEntity(wsId, SyncEntityType.COMMISSION_MOVEMENT.wireValue, it.id, now)) }
        }

        backupTxDao.replaceAllData(
            personDao = personDao,
            operationDao = operationDao,
            movementDao = movementDao,
            persons = persons.map { it.toEntity() },
            operations = operations.map { it.toEntity() },
            movements = movements.map { it.toEntity() }
        )
        if (tombstones.isNotEmpty()) {
            tombstoneDao.upsertAll(tombstones)
        }
        preferences.applySettingsFromSyncOrBackup(
            settings = settings.copy(
                syncWorkspaceId = wsId,
                settingsUpdatedAt = now
            ),
            markPendingSync = true,
            syncedAtEpoch = currentSettings.lastSyncedAt
        )
    }
}

data class BackupSnapshot(
    val persons: List<Person>,
    val operations: List<Operation>,
    val movements: List<CommissionMovement>,
    val settings: AppSettings
)
