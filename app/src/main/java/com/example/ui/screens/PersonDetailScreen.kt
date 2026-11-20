package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CommissionMode
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.CupAmountText
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person,
    operations: List<Operation>,
    onBack: () -> Unit,
    onOpenPdf: () -> Unit,
    onEditPerson: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeletePerson: () -> Unit,
    onQuickOperation: (OperationType) -> Unit,
    onEditOperation: (Operation) -> Unit,
    onDeleteOperation: (Operation) -> Unit
) {
    BackHandler { onBack() }

    val summary = remember(operations) { FinanceCalculator.computeSummary(operations) }
    val sortedOps = remember(operations) {
        operations.sortedWith(FinanceCalculator.operationDescendingComparator)
    }
    val semantic = LocalSemanticColors.current

    var showDeletePersonConfirm by remember { mutableStateOf(false) }
    var opToDelete by remember { mutableStateOf<Operation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a Inicio"
                        )
                    }
                },
                title = {
                    Text(
                        text = person.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenPdf,
                        modifier = Modifier.testTag("detail_pdf_button")
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Comprobante PDF")
                    }
                    IconButton(
                        onClick = onEditPerson,
                        modifier = Modifier.testTag("detail_edit_person_button")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar titular")
                    }
                    IconButton(
                        onClick = onToggleArchive,
                        modifier = Modifier.testTag("detail_archive_person_button")
                    ) {
                        Icon(
                            imageVector = if (person.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = if (person.archived) "Reactivar" else "Archivar"
                        )
                    }
                    IconButton(
                        onClick = { showDeletePersonConfirm = true },
                        modifier = Modifier.testTag("detail_delete_person_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Eliminar titular",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Person metadata info
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Comisión vigente: ${Formatters.formatRate(person.commissionRate)} · Modalidad predeterminada: ${person.defaultCommissionMode.shortLabel}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            fontWeight = FontWeight.Medium
                        )
                        if (!person.receiptName.isNullOrBlank()) {
                            Text(
                                text = "Nombre para comprobantes PDF: ${person.receiptName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (person.archived) {
                            Text(
                                text = "Titular archivado (solo lectura para nuevas operaciones).",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.amberWarningText
                            )
                        }
                    }
                }
            }

            // Prominent red alert if porEntregar < 0
            if (summary.porEntregar < -0.005) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Alerta entregado de más",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Entregaste de más: hay un excedente entregado de ${Formatters.formatCup(abs(summary.porEntregar))}.",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Pending balance cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailBalanceCard(
                        label = "Pendiente por extraer",
                        amount = summary.pendientePorExtraer,
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    val isNegative = summary.porEntregar < -0.005
                    DetailBalanceCard(
                        label = if (isNegative) "Entregado de más" else "Por entregar",
                        amount = if (isNegative) abs(summary.porEntregar) else summary.porEntregar,
                        valueColor = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        containerColor = if (isNegative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Row of totals: Recibido, Extraído, Entregado
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompactStat("Recibido", summary.totalRecibido, Modifier.weight(1f))
                        CompactStat("Extraído", summary.totalExtraido, Modifier.weight(1f))
                        CompactStat("Entregado", summary.totalEntregado, Modifier.weight(1f))
                    }
                }
            }

            // 4 Commission cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comisiones del titular", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailBalanceCard(
                            label = "Generada",
                            amount = summary.comisionGenerada,
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        DetailBalanceCard(
                            label = "Descontada",
                            amount = summary.comisionDescontada,
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailBalanceCard(
                            label = "Cobrada",
                            amount = summary.comisionCobrada,
                            valueColor = semantic.positiveText,
                            modifier = Modifier.weight(1f)
                        )
                        val pending = summary.comisionPendienteCobro > 0.005
                        DetailBalanceCard(
                            label = "Pendiente de cobro",
                            amount = summary.comisionPendienteCobro,
                            valueColor = if (pending) semantic.orangePendingText else MaterialTheme.colorScheme.onSurface,
                            containerColor = if (pending) semantic.orangePendingBg else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4 Quick Action Buttons (pastel tones) if not archived
            if (!person.archived) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Acciones rápidas", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PastelActionButton(
                                text = "Recibí dinero",
                                bg = semantic.actionReceivedBg,
                                fg = semantic.actionReceivedFg,
                                onClick = { onQuickOperation(OperationType.RECIBIDO) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_recibido")
                            )
                            PastelActionButton(
                                text = "Extraje",
                                bg = semantic.actionExtractionBg,
                                fg = semantic.actionExtractionFg,
                                onClick = { onQuickOperation(OperationType.EXTRACCION) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_extraccion")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PastelActionButton(
                                text = "Entregué",
                                bg = semantic.actionDeliveryBg,
                                fg = semantic.actionDeliveryFg,
                                onClick = { onQuickOperation(OperationType.ENTREGA) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_entrega")
                            )
                            PastelActionButton(
                                text = "Cobré comisión",
                                bg = semantic.actionCommissionBg,
                                fg = semantic.actionCommissionFg,
                                onClick = { onQuickOperation(OperationType.COBRO_COMISION) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_action_cobro_comision")
                            )
                        }
                    }
                }
            }

            // Operations History Header
            item {
                Text(
                    text = "Historial de operaciones (${sortedOps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (sortedOps.isEmpty()) {
                item {
                    Text(
                        text = "No hay operaciones registradas para este titular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(sortedOps, key = { it.id }) { op ->
                    OperationHistoryCard(
                        operation = op,
                        onEdit = { onEditOperation(op) },
                        onDelete = { opToDelete = op }
                    )
                }
            }
        }
    }

    if (showDeletePersonConfirm) {
        val opCount = operations.size
        val msg = if (opCount > 0) {
            "¿Seguro que deseas eliminar a \"${person.name}\"? Se eliminarán también $opCount operaciones asociadas de forma permanente."
        } else {
            "¿Seguro que deseas eliminar a \"${person.name}\"? Esta acción no se puede deshacer."
        }
        ConfirmationDialog(
            title = "Eliminar titular",
            message = msg,
            confirmText = "Eliminar",
            isDestructive = true,
            onConfirm = {
                showDeletePersonConfirm = false
                onDeletePerson()
            },
            onDismiss = { showDeletePersonConfirm = false }
        )
    }

    opToDelete?.let { target ->
        ConfirmationDialog(
            title = "Eliminar operación",
            message = "¿Eliminar esta operación de ${target.type.label} por ${Formatters.formatCup(target.amount)}?",
            confirmText = "Eliminar",
            isDestructive = true,
            onConfirm = {
                opToDelete = null
                onDeleteOperation(target)
            },
            onDismiss = { opToDelete = null }
        )
    }
}

@Composable
private fun DetailBalanceCard(
    label: String,
    amount: Double,
    valueColor: Color,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CupAmountText(
                amount = amount,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor
            )
        }
    }
}

@Composable
private fun CompactStat(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CupAmountText(
            amount = amount,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun PastelActionButton(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = bg,
            contentColor = fg
        ),
        modifier = modifier.heightIn(min = 50.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun OperationHistoryCard(
    operation: Operation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    var showBills by remember { mutableStateOf(false) }

    val (badgeBg, badgeFg) = when (operation.type) {
        OperationType.RECIBIDO -> semantic.actionReceivedBg to semantic.actionReceivedFg
        OperationType.EXTRACCION -> semantic.actionExtractionBg to semantic.actionExtractionFg
        OperationType.ENTREGA -> semantic.actionDeliveryBg to semantic.actionDeliveryFg
        OperationType.COBRO_COMISION -> semantic.actionCommissionBg to semantic.actionCommissionFg
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = operation.type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeFg,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = Formatters.formatDateTimeDisplay(operation.date),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CupAmountText(
                    amount = operation.amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_op_${operation.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Editar operación",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_op_${operation.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Eliminar operación",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (operation.type == OperationType.EXTRACCION) {
                val mode = operation.commissionMode ?: CommissionMode.DESCONTADA
                val comm = FinanceCalculator.effectiveCommission(operation)
                val rateLabel = operation.commissionRateSnapshot?.let { " (${Formatters.formatRate(it)})" }.orEmpty()
                Text(
                    text = "Comisión: ${Formatters.formatCup(comm)}$rateLabel · ${mode.shortLabel}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!operation.note.isNullOrBlank()) {
                Text(
                    text = operation.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!operation.billBreakdown.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBills = !showBills }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Desglose de billetes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = if (showBills) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (showBills) {
                            Spacer(Modifier.height(6.dp))
                            operation.billBreakdown.entries
                                .filter { it.value > 0 }
                                .sortedByDescending { it.key }
                                .forEach { (denom, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$count × ${Formatters.formatCup(denom.toDouble())}",
                                            style = MaterialTheme.typography.bodySmall.tabular()
                                        )
                                        Text(
                                            text = Formatters.formatCup((denom.toLong() * count).toDouble()),
                                            style = MaterialTheme.typography.bodySmall.tabular(),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
