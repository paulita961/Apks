package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.CupDenominations
import com.example.domain.DbConnectionStatus
import com.example.domain.Formatters
import com.example.domain.NotificationPreferences
import com.example.domain.Person
import com.example.export.AppVersionInfo
import com.example.export.BackupPayload
import com.example.ui.MainUiState
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.DbStatusDot
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onSetDailyLimit: (Double?) -> Unit,
    onToggleDenomination: (Int) -> Unit,
    onUpdateNotificationPrefs: (NotificationPreferences) -> Unit,
    onSetWorkspaceId: (String) -> Unit,
    onCheckConnection: suspend () -> String,
    onSyncNow: suspend () -> String,
    onAddPerson: () -> Unit,
    onEditPerson: (Person) -> Unit,
    onToggleArchivePerson: (Person) -> Unit,
    onDeletePerson: (Person) -> Unit,
    onBuildBackupJson: suspend () -> String,
    onBuildCsvString: suspend () -> String,
    onParseBackupJson: (String) -> Result<BackupPayload>,
    onRestoreBackup: suspend (BackupPayload) -> Unit,
    onMessage: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current

    var limitText by remember(uiState.settings.dailyCardLimit) {
        mutableStateOf(
            uiState.settings.dailyCardLimit?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            }.orEmpty()
        )
    }

    var workspaceInput by remember(uiState.settings.syncWorkspaceId) {
        mutableStateOf(uiState.settings.syncWorkspaceId)
    }

    var personToDelete by remember { mutableStateOf<Pair<Person, Int>?>(null) }
    var pendingRestorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var changelogExpanded by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onBuildBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("No se pudo abrir el destino")
                    }
                }.onSuccess {
                    onMessage("Respaldo JSON exportado correctamente.")
                }.onFailure {
                    onMessage("Error al exportar respaldo: ${it.localizedMessage}")
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val rawResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        } ?: error("No se pudo leer el archivo")
                    }
                }
                rawResult.onSuccess { raw ->
                    onParseBackupJson(raw).onSuccess { payload ->
                        pendingRestorePayload = payload
                    }.onFailure { err ->
                        onMessage("Respaldo inválido: ${err.localizedMessage}")
                    }
                }.onFailure { err ->
                    onMessage("No se pudo leer el archivo: ${err.localizedMessage}")
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val csv = onBuildCsvString()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("No se pudo abrir el destino")
                    }
                }.onSuccess {
                    onMessage("Archivo CSV exportado correctamente.")
                }.onFailure {
                    onMessage("Error al exportar CSV: ${it.localizedMessage}")
                }
            }
        }
    }

    val notif = uiState.settings.notifications

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Límite diario de la tarjeta
        item {
            SettingsSectionCard(title = "Límite diario de la tarjeta") {
                Text(
                    text = "Configura un tope diario en CUP para recibir un aviso al registrar o editar extracciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { input ->
                        val cleaned = input.replace(',', '.')
                        if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                            limitText = cleaned
                        }
                    },
                    label = { Text("Límite diario (CUP)") },
                    placeholder = { Text("Sin límite configurado") },
                    leadingIcon = { Icon(Icons.Outlined.CreditCard, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_daily_limit_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            limitText = ""
                            onSetDailyLimit(null)
                            onMessage("Límite diario eliminado.")
                        },
                        enabled = uiState.settings.dailyCardLimit != null || limitText.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Limpiar")
                    }
                    Button(
                        onClick = {
                            val parsed = limitText.replace(',', '.').toDoubleOrNull()
                            if (parsed == null || parsed <= 0.0) {
                                onSetDailyLimit(null)
                                limitText = ""
                                onMessage("Límite diario desactivado.")
                            } else {
                                onSetDailyLimit(parsed)
                                onMessage("Límite diario guardado: ${Formatters.formatCup(parsed)}.")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_save_limit_button")
                    ) {
                        Text("Guardar límite")
                    }
                }
            }
        }

        // 2. Centro de avisos (v1.9.0)
        item {
            SettingsSectionCard(title = "Centro de avisos") {
                SettingSwitchRow(
                    title = "Activar centro de avisos",
                    subtitle = "Muestra alertas priorizadas dentro de la app sin usar permisos del sistema.",
                    checked = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(enabled = it)) },
                    testTag = "settings_notif_master_switch"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                SettingSwitchRow(
                    title = "Entregas en exceso",
                    subtitle = "Alerta roja si se entregó más dinero del disponible.",
                    checked = notif.overDeliveryEnabled,
                    enabled = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(overDeliveryEnabled = it)) },
                    testTag = "settings_notif_over_delivery"
                )

                SettingSwitchRow(
                    title = "Entregas pendientes",
                    subtitle = "Avisa si hay efectivo por entregar tras superar el umbral.",
                    checked = notif.pendingDeliveryEnabled,
                    enabled = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(pendingDeliveryEnabled = it)) },
                    testTag = "settings_notif_pending_delivery"
                )

                if (notif.enabled && notif.pendingDeliveryEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Umbral de entrega pendiente",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(12, 24, 48, 72).forEach { hours ->
                                FilterChip(
                                    selected = notif.pendingDeliveryHours == hours,
                                    onClick = {
                                        onUpdateNotificationPrefs(notif.copy(pendingDeliveryHours = hours))
                                    },
                                    label = { Text("$hours h") },
                                    modifier = Modifier.testTag("settings_pending_hours_$hours")
                                )
                            }
                        }
                    }
                }

                SettingSwitchRow(
                    title = "Comisiones aparte por cobrar",
                    subtitle = "Recuerda comisiones en modalidad aparte pendientes de cobro.",
                    checked = notif.pendingCommissionEnabled,
                    enabled = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(pendingCommissionEnabled = it)) },
                    testTag = "settings_notif_pending_commission"
                )

                SettingSwitchRow(
                    title = "Límite diario de tarjeta",
                    subtitle = "Alerta al alcanzar el 80 % (ámbar) o 100 % (rojo) del tope diario.",
                    checked = notif.dailyLimitEnabled,
                    enabled = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(dailyLimitEnabled = it)) },
                    testTag = "settings_notif_daily_limit"
                )

                SettingSwitchRow(
                    title = "Cambios pendientes por sincronizar",
                    subtitle = "Indica si existen cambios locales pendientes cuando hay nube configurada.",
                    checked = notif.pendingSyncEnabled,
                    enabled = notif.enabled,
                    onCheckedChange = { onUpdateNotificationPrefs(notif.copy(pendingSyncEnabled = it)) },
                    testTag = "settings_notif_pending_sync"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Recordatorio de respaldo JSON",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val options = listOf(
                            7 to "7 días",
                            15 to "15 días",
                            30 to "30 días",
                            0 to "Desactivado"
                        )
                        options.forEach { (days, label) ->
                            FilterChip(
                                selected = notif.backupReminderDays == days,
                                enabled = notif.enabled,
                                onClick = {
                                    onUpdateNotificationPrefs(notif.copy(backupReminderDays = days))
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("settings_backup_days_$days")
                            )
                        }
                    }
                }
            }
        }

        // 3. Gestionar titulares
        item {
            SettingsSectionCard(title = "Gestionar titulares (${uiState.personSummaries.size})") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agrega, edita, archiva o elimina titulares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onAddPerson,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_add_person_button")
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo")
                    }
                }

                if (uiState.personSummaries.isEmpty()) {
                    Text(
                        text = "No hay titulares registrados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.personSummaries.forEach { ps ->
                            val person = ps.person
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (person.archived) "${person.name} (Archivado)" else person.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${Formatters.formatRate(person.commissionRate)} · ${person.defaultCommissionMode.shortLabel} · ${ps.operationCount} ops",
                                            style = MaterialTheme.typography.bodySmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { onEditPerson(person) }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Editar titular", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onToggleArchivePerson(person) }) {
                                            Icon(
                                                imageVector = if (person.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                                contentDescription = if (person.archived) "Reactivar" else "Archivar"
                                            )
                                        }
                                        IconButton(onClick = { personToDelete = person to ps.operationCount }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar titular", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Denominaciones en el contador
        item {
            SettingsSectionCard(title = "Denominaciones en el contador (CUP)") {
                Text(
                    text = "Selecciona qué billetes aparecen en el contador de herramientas (14 denominaciones soportadas).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CupDenominations.ALL.forEach { denom ->
                        val active = denom in uiState.settings.visibleDenominations
                        FilterChip(
                            selected = active,
                            onClick = { onToggleDenomination(denom) },
                            label = {
                                Text(
                                    text = Formatters.formatNumber(denom.toDouble()),
                                    style = MaterialTheme.typography.labelMedium.tabular()
                                )
                            },
                            modifier = Modifier.testTag("settings_denom_$denom")
                        )
                    }
                }
            }
        }

        // 5. Sincronización y respaldo (v1.8.0 - v1.8.1 + JSON/CSV)
        item {
            SettingsSectionCard(title = "Sincronización y respaldo") {
                val isBusy = uiState.dbStatus == DbConnectionStatus.CHECKING ||
                    uiState.dbStatus == DbConnectionStatus.SYNCING

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DbStatusDot(status = uiState.dbStatus)
                                Text(
                                    text = "Estado Supabase: ${uiState.dbStatus.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (uiState.settings.hasPendingSync && uiState.isSupabaseConfigured) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = semantic.amberWarningBg
                                ) {
                                    Text(
                                        text = "Cambios locales",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semantic.amberWarningText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        val lastSyncText = uiState.settings.lastSyncedAt?.let {
                            "Última sincronización: ${Formatters.formatEpochDisplay(it)}"
                        } ?: "Sin sincronización previa con la nube."
                        Text(
                            text = lastSyncText,
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!uiState.dbErrorMessage.isNullOrBlank()) {
                            Text(
                                text = uiState.dbErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.dbStatus == DbConnectionStatus.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = workspaceInput,
                    onValueChange = { workspaceInput = it },
                    label = { Text("Nombre de espacio compartido") },
                    placeholder = { Text("principal") },
                    supportingText = {
                        Text("Se usará como: ${Formatters.sanitizeWorkspaceId(workspaceInput)}")
                    },
                    leadingIcon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_workspace_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clean = Formatters.sanitizeWorkspaceId(workspaceInput)
                            workspaceInput = clean
                            onSetWorkspaceId(clean)
                            onMessage("Espacio compartido actualizado a \"$clean\".")
                        },
                        enabled = workspaceInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_save_workspace_button")
                    ) {
                        Text("Guardar espacio")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val msg = onCheckConnection()
                                onMessage(msg)
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_check_connection_button")
                    ) {
                        Text("Comprobar")
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            val msg = onSyncNow()
                            onMessage(msg)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_sync_now_button")
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.dbStatus == DbConnectionStatus.SYNCING) "Sincronizando..." else "Sincronizar ahora")
                }

                if (!uiState.isSupabaseConfigured) {
                    Text(
                        text = "Modo 100 % offline activo. Si configuras SUPABASE_URL y SUPABASE_ANON_KEY en los secretos de compilación, la app sincronizará automáticamente con tu base web.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                val lastBackupLabel = uiState.settings.lastBackupAt?.let {
                    "Último respaldo JSON: ${Formatters.formatDateTimeDisplay(it)}"
                } ?: "Nunca has realizado un respaldo JSON todavía."

                Text(
                    text = lastBackupLabel,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val dateSlug = Formatters.currentIsoDate()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            exportJsonLauncher.launch("respaldo_extracciones_$dateSlug.json")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_export_json_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val json = onBuildBackupJson()
                                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val file = File(dir, "respaldo_extracciones_$dateSlug.json")
                                    withContext(Dispatchers.IO) { file.writeText(json, Charsets.UTF_8) }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Compartir respaldo JSON"))
                                }.onFailure {
                                    onMessage("No se pudo compartir el respaldo: ${it.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_share_json_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Compartir JSON", modifier = Modifier.size(18.dp))
                    }
                }

                OutlinedButton(
                    onClick = {
                        importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_import_json_button")
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importar respaldo JSON")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            exportCsvLauncher.launch("extracciones_$dateSlug.csv")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_export_csv_button")
                    ) {
                        Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar a CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val csv = onBuildCsvString()
                                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val file = File(dir, "extracciones_$dateSlug.csv")
                                    withContext(Dispatchers.IO) { file.writeText(csv, Charsets.UTF_8) }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Compartir CSV"))
                                }.onFailure {
                                    onMessage("No se pudo compartir el CSV: ${it.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_share_csv_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Compartir CSV", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 6. Control de versiones
        item {
            SettingsSectionCard(title = "Control de versiones") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Control de Extracciones v${AppVersionInfo.APP_VERSION} (Build ${AppVersionInfo.VERSION_CODE})",
                        style = MaterialTheme.typography.titleSmall.tabular()
                    )
                }
                Text(
                    text = "versionCode: ${AppVersionInfo.VERSION_CODE} · Compilación: ${AppVersionInfo.BUILD_DATE} · Esquema: v${AppVersionInfo.SCHEMA_VERSION}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { changelogExpanded = !changelogExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_toggle_changelog")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Historial de cambios", style = MaterialTheme.typography.labelLarge)
                        Icon(
                            imageVector = if (changelogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                if (changelogExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChangelogEntry(
                            version = "v1.9.0 (${AppVersionInfo.BUILD_DATE})",
                            notes = "• Centro de avisos configurable con campana y contador en la barra superior.\n" +
                                "• 6 alertas priorizadas: entregas en exceso, entregas pendientes configurables (12-72 h), comisiones aparte, límite diario (80%/100%), cambios pendientes por sincronizar y recordatorio JSON.\n" +
                                "• Opción de ocultar avisos temporalmente durante la sesión y restaurarlos."
                        )
                        ChangelogEntry(
                            version = "v1.8.0 - v1.8.1",
                            notes = "• Sincronización bidireccional offline-primero con Supabase (Last-Write-Wins + marcas de borrado de 180 días).\n" +
                                "• Sincronización automática al abrir, al recuperar conexión y tras cambios locales, más botón manual y espacio compartido.\n" +
                                "• Indicador discreto en barra superior y Ajustes con verificación real de conexión."
                        )
                        ChangelogEntry(
                            version = "v1.7.2 - v1.7.3",
                            notes = "• Tarjeta principal destacada de Saldo neto disponible (Recibidas − Usadas) en Inicio.\n" +
                                "• Cuadrícula 2×2 alineada para las tarjetas secundarias y mayor espacio para acciones."
                        )
                        ChangelogEntry(
                            version = "v1.0.0",
                            notes = "• Gestión 100 % offline con Room y DataStore en CUP.\n" +
                                "• Titulares con alias interno privado, % editable y modalidades descontada/aparte.\n" +
                                "• Cartera dinámica de comisiones, contador de 14 denominaciones CUP, calculadoras, PDF nativos y respaldo JSON/CSV."
                        )
                    }
                }
            }
        }
    }

    personToDelete?.let { (person, opCount) ->
        val msg = if (opCount > 0) {
            "¿Eliminar a \"${person.name}\"? Se eliminarán también $opCount operaciones asociadas de forma permanente."
        } else {
            "¿Eliminar a \"${person.name}\"?"
        }
        ConfirmationDialog(
            title = "Eliminar titular",
            message = msg,
            confirmText = "Eliminar",
            isDestructive = true,
            onConfirm = {
                personToDelete = null
                onDeletePerson(person)
            },
            onDismiss = { personToDelete = null }
        )
    }

    pendingRestorePayload?.let { payload ->
        ConfirmationDialog(
            title = "Importar respaldo JSON",
            message = "Esto reemplazará todos los datos actuales con el respaldo seleccionado:\n" +
                "• ${payload.persons.size} titulares\n" +
                "• ${payload.operations.size} operaciones\n" +
                "• ${payload.commissionMovements.size} movimientos de comisión\n\n" +
                "¿Deseas continuar?",
            confirmText = "Reemplazar datos",
            isDestructive = true,
            onConfirm = {
                val toRestore = payload
                pendingRestorePayload = null
                scope.launch {
                    onRestoreBackup(toRestore)
                    onMessage("Respaldo restaurado correctamente.")
                }
            },
            onDismiss = { pendingRestorePayload = null }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ChangelogEntry(
    version: String,
    notes: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = version,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
