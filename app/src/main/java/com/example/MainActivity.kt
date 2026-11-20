package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.CommissionMovement
import com.example.domain.NoticeActionType
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.ui.MainViewModel
import com.example.ui.components.CommissionMovementModal
import com.example.ui.components.DbStatusBadge
import com.example.ui.components.NoticeCenterBottomSheet
import com.example.ui.components.OperationEditorModal
import com.example.ui.components.PdfPrivacyDialog
import com.example.ui.components.PersonFormModal
import com.example.ui.screens.CommissionsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PersonDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.ControlExtraccionesTheme
import com.example.ui.theme.tabular
import kotlinx.coroutines.launch

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    INICIO("Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    COMISIONES("Comisiones", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    HERRAMIENTAS("Herramientas", Icons.Filled.Build, Icons.Outlined.Build),
    AJUSTES("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ControlExtraccionesApp
        setContent {
            ControlExtraccionesTheme {
                val mainViewModel: MainViewModel = viewModel(
                    factory = MainViewModel.factory(app.container.repository)
                )
                ControlExtraccionesRoot(viewModel = mainViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlExtraccionesRoot(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.INICIO) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }

    // Modals state
    var showPersonForm by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }

    var showOpEditor by remember { mutableStateOf(false) }
    var editingOperation by remember { mutableStateOf<Operation?>(null) }
    var opInitialPersonId by remember { mutableStateOf<String?>(null) }
    var opInitialType by remember { mutableStateOf<OperationType?>(null) }

    var showMovementModal by remember { mutableStateOf(false) }
    var editingMovement by remember { mutableStateOf<CommissionMovement?>(null) }

    var pdfTargetPerson by remember { mutableStateOf<Person?>(null) }
    var showNoticeCenter by remember { mutableStateOf(false) }

    fun showMessage(msg: String) {
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val detailPerson = remember(uiState.persons, selectedPersonId) {
        selectedPersonId?.let { id -> uiState.persons.firstOrNull { it.id == id } }
    }

    if (detailPerson != null) {
        val personOps = remember(uiState.operations, detailPerson.id) {
            uiState.operations.filter { it.personId == detailPerson.id }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            PersonDetailScreen(
                person = detailPerson,
                operations = personOps,
                onBack = { selectedPersonId = null },
                onOpenPdf = { pdfTargetPerson = detailPerson },
                onEditPerson = {
                    editingPerson = detailPerson
                    showPersonForm = true
                },
                onToggleArchive = {
                    viewModel.toggleArchivePerson(detailPerson)
                    showMessage(
                        if (detailPerson.archived) "Titular reactivado." else "Titular archivado."
                    )
                },
                onDeletePerson = {
                    val idToDelete = detailPerson.id
                    selectedPersonId = null
                    viewModel.deletePerson(idToDelete)
                    showMessage("Titular eliminado.")
                },
                onQuickOperation = { type ->
                    editingOperation = null
                    opInitialPersonId = detailPerson.id
                    opInitialType = type
                    showOpEditor = true
                },
                onEditOperation = { op ->
                    editingOperation = op
                    opInitialPersonId = op.personId
                    opInitialType = op.type
                    showOpEditor = true
                },
                onDeleteOperation = { op ->
                    viewModel.deleteOperation(op.id)
                    showMessage("Operación eliminada.")
                }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Text(
                            text = when (currentTab) {
                                MainTab.INICIO -> "Control de Extracciones"
                                MainTab.COMISIONES -> "Comisiones"
                                MainTab.HERRAMIENTAS -> "Herramientas"
                                MainTab.AJUSTES -> "Ajustes"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            DbStatusBadge(
                                status = uiState.dbStatus,
                                onClick = {
                                    scope.launch {
                                        val msg = viewModel.checkConnectionAndSyncWithFeedback()
                                        showMessage(msg)
                                    }
                                }
                            )

                            val noticeCount = uiState.visibleNotices.size
                            IconButton(
                                onClick = { showNoticeCenter = true },
                                modifier = Modifier.testTag("topbar_notice_bell_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (noticeCount > 0) {
                                            Badge {
                                                Text(
                                                    text = if (noticeCount > 99) "99+" else noticeCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall.tabular()
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (noticeCount > 0) {
                                            Icons.Outlined.NotificationsActive
                                        } else {
                                            Icons.Outlined.Notifications
                                        },
                                        contentDescription = if (noticeCount > 0) {
                                            "Centro de avisos ($noticeCount activos)"
                                        } else {
                                            "Centro de avisos"
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentTab == MainTab.INICIO && uiState.activePersonSummaries.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingOperation = null
                            opInitialPersonId = uiState.activePersonSummaries.firstOrNull()?.person?.id
                            opInitialType = OperationType.EXTRACCION
                            showOpEditor = true
                        },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Registrar", fontWeight = FontWeight.SemiBold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("home_fab_register")
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, maxLines = 1) },
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            val topPad = innerPadding.calculateTopPadding()
            val bottomPad = innerPadding.calculateBottomPadding()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad)
            ) {
                when (currentTab) {
                    MainTab.INICIO -> HomeScreen(
                        uiState = uiState,
                        onGoToBackup = { currentTab = MainTab.AJUSTES },
                        onOpenCommissionMovement = {
                            editingMovement = null
                            showMovementModal = true
                        },
                        onAddPerson = {
                            editingPerson = null
                            showPersonForm = true
                        },
                        onSelectPerson = { id -> selectedPersonId = id },
                        bottomPadding = bottomPad
                    )

                    MainTab.COMISIONES -> CommissionsScreen(
                        uiState = uiState,
                        onOpenNewMovement = {
                            editingMovement = null
                            showMovementModal = true
                        },
                        onEditMovement = { mov ->
                            editingMovement = mov
                            showMovementModal = true
                        },
                        onDeleteMovement = { mov ->
                            viewModel.deleteCommissionMovement(mov.id)
                            showMessage("Movimiento eliminado.")
                        },
                        bottomPadding = bottomPad
                    )

                    MainTab.HERRAMIENTAS -> ToolsScreen(
                        uiState = uiState,
                        onUpdateBillCounter = { counts, expected ->
                            viewModel.updateBillCounter(counts, expected)
                        },
                        onClearBillCounter = { viewModel.clearBillCounter() },
                        onRecordCalcHistory = { expr, res ->
                            viewModel.addCalculatorHistory(expr, res)
                        },
                        onClearCalcHistory = { viewModel.clearCalculatorHistory() },
                        bottomPadding = bottomPad
                    )

                    MainTab.AJUSTES -> SettingsScreen(
                        uiState = uiState,
                        onSetDailyLimit = { viewModel.setDailyCardLimit(it) },
                        onToggleDenomination = { viewModel.toggleDenomination(it) },
                        onUpdateNotificationPrefs = { viewModel.setNotificationPreferences(it) },
                        onSetWorkspaceId = { viewModel.setSyncWorkspaceId(it) },
                        onCheckConnection = { viewModel.checkConnectionAndSyncWithFeedback() },
                        onSyncNow = { viewModel.syncNowWithFeedback() },
                        onAddPerson = {
                            editingPerson = null
                            showPersonForm = true
                        },
                        onEditPerson = { p ->
                            editingPerson = p
                            showPersonForm = true
                        },
                        onToggleArchivePerson = { p ->
                            viewModel.toggleArchivePerson(p)
                            showMessage(if (p.archived) "Titular reactivado." else "Titular archivado.")
                        },
                        onDeletePerson = { p ->
                            viewModel.deletePerson(p.id)
                            showMessage("Titular eliminado.")
                        },
                        onBuildBackupJson = { viewModel.exportBackupJsonAndMark() },
                        onBuildCsvString = { viewModel.exportCsvString() },
                        onParseBackupJson = { raw -> viewModel.parseBackupJson(raw) },
                        onRestoreBackup = { payload -> viewModel.restoreFromBackup(payload) },
                        onMessage = { showMessage(it) },
                        bottomPadding = bottomPad
                    )
                }
            }
        }
    }

    if (showNoticeCenter) {
        NoticeCenterBottomSheet(
            notices = uiState.visibleNotices,
            hiddenCount = uiState.hiddenNoticeCount,
            onDismissNotice = { viewModel.dismissNotice(it) },
            onRestoreHidden = { viewModel.restoreHiddenNotices() },
            onAction = { notice ->
                showNoticeCenter = false
                when (notice.actionType) {
                    NoticeActionType.VIEW_PERSON -> {
                        if (notice.targetPersonId != null) {
                            selectedPersonId = notice.targetPersonId
                        }
                    }
                    NoticeActionType.GO_TO_COMMISSIONS -> {
                        selectedPersonId = null
                        currentTab = MainTab.COMISIONES
                    }
                    NoticeActionType.GO_TO_SETTINGS,
                    NoticeActionType.GO_TO_BACKUP -> {
                        selectedPersonId = null
                        currentTab = MainTab.AJUSTES
                    }
                    NoticeActionType.SYNC_NOW -> {
                        scope.launch {
                            val msg = viewModel.syncNowWithFeedback()
                            showMessage(msg)
                        }
                    }
                }
            },
            onOpenSettings = {
                selectedPersonId = null
                currentTab = MainTab.AJUSTES
            },
            onDismissSheet = { showNoticeCenter = false }
        )
    }

    if (showPersonForm) {
        PersonFormModal(
            editingPerson = editingPerson,
            onSave = { name, receiptName, rate, mode ->
                viewModel.savePerson(editingPerson, name, receiptName, rate, mode)
                showPersonForm = false
                showMessage(if (editingPerson == null) "Titular creado." else "Titular actualizado.")
            },
            onDismiss = { showPersonForm = false }
        )
    }

    if (showOpEditor) {
        OperationEditorModal(
            editingOperation = editingOperation,
            initialPersonId = opInitialPersonId,
            initialType = opInitialType,
            persons = uiState.persons,
            personSummaries = uiState.personSummaries,
            allOperations = uiState.operations,
            dailyCardLimit = uiState.settings.dailyCardLimit,
            visibleDenominations = uiState.settings.visibleDenominations,
            calculatorHistory = uiState.calculatorHistory,
            onRecordCalcHistory = { expr, res -> viewModel.addCalculatorHistory(expr, res) },
            onClearCalcHistory = { viewModel.clearCalculatorHistory() },
            onSave = { personId, type, date, amount, rateSnap, mode, commAmount, note, bills ->
                viewModel.saveOperation(
                    editingOp = editingOperation,
                    personId = personId,
                    type = type,
                    date = date,
                    amount = amount,
                    commissionRateSnapshot = rateSnap,
                    commissionMode = mode,
                    commissionAmount = commAmount,
                    note = note,
                    billBreakdown = bills
                )
                showOpEditor = false
                showMessage(if (editingOperation == null) "Operación registrada." else "Operación actualizada.")
            },
            onDismiss = { showOpEditor = false }
        )
    }

    if (showMovementModal) {
        CommissionMovementModal(
            editingMovement = editingMovement,
            wallet = uiState.commissionWallet,
            onSave = { date, amount, category, note ->
                viewModel.saveCommissionMovement(
                    editingMovement = editingMovement,
                    date = date,
                    amount = amount,
                    category = category,
                    note = note
                )
                showMovementModal = false
                showMessage(if (editingMovement == null) "Movimiento guardado." else "Movimiento actualizado.")
            },
            onDismiss = { showMovementModal = false }
        )
    }

    pdfTargetPerson?.let { person ->
        val personOps = remember(uiState.operations, person.id) {
            uiState.operations.filter { it.personId == person.id }
        }
        PdfPrivacyDialog(
            person = person,
            operations = personOps,
            onSaveReceiptName = { newName ->
                viewModel.updatePersonReceiptName(person, newName)
            },
            onDismiss = { pdfTargetPerson = null },
            onMessage = { showMessage(it) }
        )
    }
}
