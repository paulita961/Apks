package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.ChartMetricMode
import com.example.domain.CommissionFilterMode
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.MonthlyCommissionStats
import com.example.domain.OperationType
import com.example.ui.MainUiState
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.CupAmountText
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import java.time.LocalDate
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionsScreen(
    uiState: MainUiState,
    onOpenNewMovement: () -> Unit,
    onEditMovement: (CommissionMovement) -> Unit,
    onDeleteMovement: (CommissionMovement) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val wallet = uiState.commissionWallet

    var movementsExpanded by remember { mutableStateOf(false) }
    var movementToDelete by remember { mutableStateOf<CommissionMovement?>(null) }

    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var personDropdownOpen by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(CommissionFilterMode.ALL) }
    var chartMode by remember { mutableStateOf(ChartMetricMode.MODE_SPLIT) }
    var selectedChartMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }

    val selectedPerson = remember(uiState.persons, selectedPersonId) {
        uiState.persons.firstOrNull { it.id == selectedPersonId }
    }

    val personFilteredOps = remember(uiState.operations, selectedPersonId) {
        if (selectedPersonId == null) uiState.operations
        else uiState.operations.filter { it.personId == selectedPersonId }
    }

    val filteredSummary = remember(personFilteredOps) {
        FinanceCalculator.computeSummary(personFilteredOps)
    }

    val extractionsDescontadas = remember(personFilteredOps) {
        personFilteredOps.filter {
            it.type == OperationType.EXTRACCION &&
                (it.commissionMode ?: CommissionMode.DESCONTADA) == CommissionMode.DESCONTADA
        }
    }
    val extractionsAparte = remember(personFilteredOps) {
        personFilteredOps.filter {
            it.type == OperationType.EXTRACCION && it.commissionMode == CommissionMode.APARTE
        }
    }

    val pendingPersons = remember(uiState.personSummaries) {
        uiState.personSummaries
            .filter { it.summary.comisionPendienteCobro > 0.005 }
            .sortedByDescending { it.summary.comisionPendienteCobro }
    }

    val currentYear = remember { LocalDate.now().year }
    val yearMonthlyStats = remember(personFilteredOps, currentYear) {
        FinanceCalculator.computeMonthlyStatsForYear(personFilteredOps, currentYear)
    }
    val allMonthsBreakdown = remember(personFilteredOps) {
        FinanceCalculator.computeAllMonthlyBreakdownDesc(personFilteredOps)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Panel: Mi comisión real (disponible y ahorro)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mi comisión real",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Disponible en mano y ahorro sin alterar históricos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WalletMetricTile(
                            label = "Disponible",
                            amount = wallet.comisionDisponible,
                            color = if (wallet.comisionDisponible < 0) MaterialTheme.colorScheme.error else semantic.positiveText,
                            modifier = Modifier.weight(1f)
                        )
                        WalletMetricTile(
                            label = "En ahorro",
                            amount = wallet.ahorroActual,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        WalletMetricTile(
                            label = "Usada / gastada",
                            amount = wallet.totalGastado,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = onOpenNewMovement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("comm_use_or_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Usar o ahorrar")
                    }

                    if (uiState.commissionMovements.isNotEmpty()) {
                        Surface(
                            onClick = { movementsExpanded = !movementsExpanded },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_commission_movements")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Movimientos de mi comisión (${uiState.commissionMovements.size})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Icon(
                                    imageVector = if (movementsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }

                        if (movementsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.commissionMovements.forEach { mov ->
                                    CommissionMovementRow(
                                        movement = mov,
                                        onEdit = { onEditMovement(mov) },
                                        onDelete = { movementToDelete = mov }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Análisis y filtros", style = MaterialTheme.typography.titleMedium)

                ExposedDropdownMenuBox(
                    expanded = personDropdownOpen,
                    onExpandedChange = { personDropdownOpen = !personDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedPerson?.name ?: "Todos los titulares",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filtrar por titular") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("comm_person_filter")
                    )
                    ExposedDropdownMenu(
                        expanded = personDropdownOpen,
                        onDismissRequest = { personDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todos los titulares") },
                            onClick = {
                                selectedPersonId = null
                                personDropdownOpen = false
                            }
                        )
                        uiState.persons.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (p.archived) "${p.name} (Archivado)" else p.name)
                                },
                                onClick = {
                                    selectedPersonId = p.id
                                    personDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CommissionFilterMode.entries.forEachIndexed { idx, item ->
                        SegmentedButton(
                            selected = filterMode == item,
                            onClick = { filterMode = item },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = CommissionFilterMode.entries.size
                            ),
                            modifier = Modifier.testTag("comm_filter_${item.name.lowercase()}")
                        ) {
                            Text(item.label, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Dynamic Summary Cards according to modality
        item {
            when (filterMode) {
                CommissionFilterMode.ALL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommSummaryTile(
                                title = "Generada",
                                amount = filteredSummary.comisionGenerada,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            CommSummaryTile(
                                title = "Cobrada",
                                amount = filteredSummary.comisionCobrada,
                                color = semantic.positiveText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommSummaryTile(
                                title = "Pendiente cobro",
                                amount = filteredSummary.comisionPendienteCobro,
                                color = if (filteredSummary.comisionPendienteCobro > 0.005) semantic.orangePendingText else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            CommSummaryTile(
                                title = "Total extraído",
                                amount = filteredSummary.totalExtraido,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                CommissionFilterMode.DESCONTADA -> {
                    val extractedDesc = extractionsDescontadas.sumOf { it.amount }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CommSummaryTile(
                            title = "Comisión descontada",
                            amount = filteredSummary.comisionDescontada,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        CommSummaryTile(
                            title = "Extraído (desc.)",
                            amount = Formatters.round2(extractedDesc),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                CommissionFilterMode.APARTE -> {
                    val apartGen = extractionsAparte.sumOf { FinanceCalculator.effectiveCommission(it) }
                    val apartPending = Formatters.round2(apartGen - filteredSummary.cobrosComision)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommSummaryTile(
                                title = "Generada aparte",
                                amount = Formatters.round2(apartGen),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            CommSummaryTile(
                                title = "Cobros aparte",
                                amount = filteredSummary.cobrosComision,
                                color = semantic.positiveText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommSummaryTile(
                                title = "Pendiente aparte",
                                amount = apartPending,
                                color = if (apartPending > 0.005) semantic.orangePendingText else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            CommSummaryTile(
                                title = "Extraído (aparte)",
                                amount = Formatters.round2(extractionsAparte.sumOf { it.amount }),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Orange block: Titulares con comisión pendiente de cobro
        if (pendingPersons.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = semantic.orangePendingBg),
                    border = BorderStroke(1.dp, semantic.orangePendingText.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Titulares con comisión pendiente de cobro",
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.orangePendingText,
                            fontWeight = FontWeight.Bold
                        )
                        pendingPersons.forEach { ps ->
                            Surface(
                                onClick = {
                                    selectedPersonId = if (selectedPersonId == ps.person.id) null else ps.person.id
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(
                                    alpha = if (selectedPersonId == ps.person.id) 1f else 0.78f
                                ),
                                border = if (selectedPersonId == ps.person.id) {
                                    BorderStroke(1.5.dp, semantic.orangePendingText)
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ps.person.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CupAmountText(
                                        amount = ps.summary.comisionPendienteCobro,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = semantic.orangePendingText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Monthly Bar Chart for Current Year
        item {
            MonthlyCommissionChartCard(
                year = currentYear,
                stats = yearMonthlyStats,
                chartMode = chartMode,
                onChartModeChange = { chartMode = it },
                selectedMonth = selectedChartMonth,
                onSelectMonth = { selectedChartMonth = it }
            )
        }

        // Monthly breakdown header
        item {
            Text(
                text = "Desglose por mes (${allMonthsBreakdown.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (allMonthsBreakdown.isEmpty()) {
            item {
                Text(
                    text = "Aún no hay extracciones ni cobros de comisión para el filtro seleccionado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(allMonthsBreakdown, key = { it.yearMonth }) { stat ->
                MonthlyBreakdownCard(stat = stat, filterMode = filterMode)
            }
        }
    }

    movementToDelete?.let { mov ->
        ConfirmationDialog(
            title = "Eliminar movimiento",
            message = "¿Eliminar este movimiento (${mov.category.label}) por ${Formatters.formatCup(mov.amount)}?",
            confirmText = "Eliminar",
            isDestructive = true,
            onConfirm = {
                movementToDelete = null
                onDeleteMovement(mov)
            },
            onDismiss = { movementToDelete = null }
        )
    }
}

@Composable
private fun WalletMetricTile(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CupAmountText(
                amount = amount,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                includeUnit = false
            )
            Text(
                text = "CUP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommSummaryTile(
    title: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CupAmountText(
                amount = amount,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}

@Composable
private fun CommissionMovementRow(
    movement: CommissionMovement,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val badgeColor = when (movement.category) {
        CommissionMovementCategory.GASTO -> MaterialTheme.colorScheme.error
        CommissionMovementCategory.AHORRO -> MaterialTheme.colorScheme.primary
        CommissionMovementCategory.USO_AHORRO -> semantic.amberWarningText
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movement.category.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor
                )
                CupAmountText(
                    amount = movement.amount,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = Formatters.formatDateTimeDisplay(movement.date),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!movement.note.isNullOrBlank()) {
                    Text(
                        text = movement.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar movimiento", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar movimiento", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MonthlyCommissionChartCard(
    year: Int,
    stats: List<MonthlyCommissionStats>,
    chartMode: ChartMetricMode,
    onChartModeChange: (ChartMetricMode) -> Unit,
    selectedMonth: Int,
    onSelectMonth: (Int) -> Unit
) {
    val primaryBarColor = MaterialTheme.colorScheme.primary
    val secondaryBarColor = if (chartMode == ChartMetricMode.MODE_SPLIT) {
        Color(0xFFEA580C)
    } else {
        Color(0xFF059669)
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    val maxVal = remember(stats, chartMode) {
        val m = stats.maxOfOrNull { s ->
            when (chartMode) {
                ChartMetricMode.MODE_SPLIT -> max(s.descontada, s.aparteGenerada)
                ChartMetricMode.STATUS_SPLIT -> max(s.generada, s.cobradaTotal)
            }
        } ?: 0.0
        if (m <= 0.0) 100.0 else m * 1.15
    }

    val selectedStat = stats.getOrNull((selectedMonth - 1).coerceIn(0, 11))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Comisiones mensuales $year",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartMetricMode.entries.forEach { mode ->
                    FilterChip(
                        selected = chartMode == mode,
                        onClick = { onChartModeChange(mode) },
                        label = { Text(mode.label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (labelA, labelB) = if (chartMode == ChartMetricMode.MODE_SPLIT) {
                    "Descontada" to "Aparte"
                } else {
                    "Generada" to "Cobrada"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(primaryBarColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(labelA, style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(secondaryBarColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(labelB, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .pointerInput(stats) {
                        detectTapGestures { offset ->
                            val colWidth = size.width / 12f
                            val tappedIdx = (offset.x / colWidth).toInt().coerceIn(0, 11)
                            onSelectMonth(tappedIdx + 1)
                        }
                    }
            ) {
                val count = 12
                val groupWidth = size.width / count
                val chartHeight = size.height - 6.dp.toPx()

                // Horizontal grid lines (3 lines)
                for (g in 0..2) {
                    val y = chartHeight * (g / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                stats.forEachIndexed { idx, s ->
                    val groupLeft = idx * groupWidth
                    if (idx + 1 == selectedMonth) {
                        drawRoundRect(
                            color = highlightBg,
                            topLeft = Offset(groupLeft + 1.dp.toPx(), 0f),
                            size = Size(groupWidth - 2.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    val (valA, valB) = if (chartMode == ChartMetricMode.MODE_SPLIT) {
                        s.descontada to s.aparteGenerada
                    } else {
                        s.generada to s.cobradaTotal
                    }

                    val barWidth = (groupWidth * 0.30f).coerceAtLeast(4.dp.toPx())
                    val gap = 2.dp.toPx()
                    val pairWidth = barWidth * 2f + gap
                    val pairStart = groupLeft + (groupWidth - pairWidth) / 2f

                    val hA = ((valA / maxVal).toFloat() * chartHeight).coerceIn(0f, chartHeight)
                    val hB = ((valB / maxVal).toFloat() * chartHeight).coerceIn(0f, chartHeight)

                    if (hA > 0f) {
                        drawRoundRect(
                            color = primaryBarColor,
                            topLeft = Offset(pairStart, chartHeight - hA),
                            size = Size(barWidth, hA),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                    if (hB > 0f) {
                        drawRoundRect(
                            color = secondaryBarColor,
                            topLeft = Offset(pairStart + barWidth + gap, chartHeight - hB),
                            size = Size(barWidth, hB),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }

            // Month labels row
            Row(modifier = Modifier.fillMaxWidth()) {
                (1..12).forEach { m ->
                    val isSelected = m == selectedMonth
                    Text(
                        text = Formatters.monthNameShort(m).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMonth(m) }
                    )
                }
            }

            // Selected Month Tooltip Details
            if (selectedStat != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${Formatters.monthNameFull(selectedStat.month)} ${selectedStat.year} · ${selectedStat.extractionCount} extracciones",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (chartMode == ChartMetricMode.MODE_SPLIT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Descontada: ${Formatters.formatCup(selectedStat.descontada)}", style = MaterialTheme.typography.bodySmall.tabular())
                                Text("Aparte: ${Formatters.formatCup(selectedStat.aparteGenerada)}", style = MaterialTheme.typography.bodySmall.tabular())
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Generada: ${Formatters.formatCup(selectedStat.generada)}", style = MaterialTheme.typography.bodySmall.tabular())
                                Text("Cobrada: ${Formatters.formatCup(selectedStat.cobradaTotal)}", style = MaterialTheme.typography.bodySmall.tabular())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyBreakdownCard(
    stat: MonthlyCommissionStats,
    filterMode: CommissionFilterMode
) {
    val semantic = LocalSemanticColors.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Formatters.monthNameFull(stat.month)} ${stat.year}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${stat.extractionCount} extracciones",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Extraído: ${Formatters.formatCup(stat.extractedAmount)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            when (filterMode) {
                CommissionFilterMode.ALL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Generada: ${Formatters.formatCup(stat.generada)}", style = MaterialTheme.typography.bodySmall.tabular())
                        Text(
                            text = "Cobrada: ${Formatters.formatCup(stat.cobradaTotal)}",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = semantic.positiveText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Descontada: ${Formatters.formatCup(stat.descontada)}", style = MaterialTheme.typography.bodySmall.tabular())
                        Text("Aparte gen.: ${Formatters.formatCup(stat.aparteGenerada)}", style = MaterialTheme.typography.bodySmall.tabular())
                    }
                }
                CommissionFilterMode.DESCONTADA -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Comisión descontada:", style = MaterialTheme.typography.bodySmall)
                        CupAmountText(amount = stat.descontada, style = MaterialTheme.typography.labelLarge)
                    }
                }
                CommissionFilterMode.APARTE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Generada aparte: ${Formatters.formatCup(stat.aparteGenerada)}", style = MaterialTheme.typography.bodySmall.tabular())
                        Text(
                            text = "Cobros: ${Formatters.formatCup(stat.cobrosAparte)}",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = semantic.positiveText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
