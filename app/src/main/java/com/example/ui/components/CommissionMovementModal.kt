package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.CommissionWallet
import com.example.domain.Formatters
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular

@Composable
fun CommissionMovementModal(
    editingMovement: CommissionMovement?,
    wallet: CommissionWallet,
    onSave: (date: String, amount: Double, category: CommissionMovementCategory, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialIso = editingMovement?.date ?: Formatters.currentIsoDateTime()
    var datePart by remember(editingMovement) { mutableStateOf(Formatters.extractDatePart(initialIso)) }
    var timePart by remember(editingMovement) { mutableStateOf(Formatters.extractTimePart(initialIso)) }
    var category by remember(editingMovement) {
        mutableStateOf(editingMovement?.category ?: CommissionMovementCategory.GASTO)
    }
    var amountText by remember(editingMovement) {
        mutableStateOf(
            if (editingMovement != null) {
                if (editingMovement.amount % 1.0 == 0.0) editingMovement.amount.toInt().toString()
                else editingMovement.amount.toString()
            } else ""
        )
    }
    var note by remember(editingMovement) { mutableStateOf(editingMovement?.note.orEmpty()) }
    var amountError by remember { mutableStateOf<String?>(null) }

    val semantic = LocalSemanticColors.current

    // Compute baseline wallet excluding the movement being edited
    val baseWallet = remember(wallet, editingMovement) {
        if (editingMovement == null) {
            wallet
        } else {
            val prevAmt = editingMovement.amount
            when (editingMovement.category) {
                CommissionMovementCategory.GASTO -> wallet.copy(
                    comisionDisponible = Formatters.round2(wallet.comisionDisponible + prevAmt),
                    totalGastado = Formatters.round2(wallet.totalGastado - prevAmt)
                )
                CommissionMovementCategory.AHORRO -> wallet.copy(
                    comisionDisponible = Formatters.round2(wallet.comisionDisponible + prevAmt),
                    ahorroActual = Formatters.round2(wallet.ahorroActual - prevAmt)
                )
                CommissionMovementCategory.USO_AHORRO -> wallet.copy(
                    ahorroActual = Formatters.round2(wallet.ahorroActual + prevAmt),
                    totalGastado = Formatters.round2(wallet.totalGastado - prevAmt)
                )
            }
        }
    }

    val amountVal = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val sourceAvailable = when (category) {
        CommissionMovementCategory.GASTO,
        CommissionMovementCategory.AHORRO -> baseWallet.comisionDisponible
        CommissionMovementCategory.USO_AHORRO -> baseWallet.ahorroActual
    }

    val previewDisponible = when (category) {
        CommissionMovementCategory.GASTO,
        CommissionMovementCategory.AHORRO -> Formatters.round2(baseWallet.comisionDisponible - amountVal)
        CommissionMovementCategory.USO_AHORRO -> baseWallet.comisionDisponible
    }
    val previewAhorro = when (category) {
        CommissionMovementCategory.GASTO -> baseWallet.ahorroActual
        CommissionMovementCategory.AHORRO -> Formatters.round2(baseWallet.ahorroActual + amountVal)
        CommissionMovementCategory.USO_AHORRO -> Formatters.round2(baseWallet.ahorroActual - amountVal)
    }

    val exceedsSource = amountVal > sourceAvailable + 0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingMovement == null) "Usar o ahorrar comisión" else "Editar movimiento",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Categoría",
                        style = MaterialTheme.typography.labelLarge
                    )
                    CommissionMovementCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("comm_mov_cat_${item.wireValue}")
                        )
                    }
                }

                DateTimePickerRow(
                    datePart = datePart,
                    timePart = timePart,
                    onDateSelected = { datePart = it },
                    onTimeSelected = { timePart = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val cleaned = input.replace(',', '.')
                            if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                                amountText = cleaned
                                amountError = null
                            }
                        },
                        label = { Text("Monto (CUP)") },
                        singleLine = true,
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleMedium.tabular(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comm_mov_amount_input")
                    )

                    OutlinedButton(
                        onClick = {
                            val fill = sourceAvailable.coerceAtLeast(0.0)
                            amountText = if (fill % 1.0 == 0.0) fill.toLong().toString() else fill.toString()
                            amountError = null
                        },
                        enabled = sourceAvailable > 0.0,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .heightIn(min = 48.dp)
                            .testTag("comm_mov_all_button")
                    ) {
                        Text("Todo")
                    }
                }

                if (exceedsSource && amountVal > 0.0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(semantic.amberWarningBg, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Saldo insuficiente",
                            tint = semantic.amberWarningText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val srcName = if (category == CommissionMovementCategory.USO_AHORRO) "en ahorro" else "disponible en mano"
                        Text(
                            text = "El monto supera el saldo $srcName (${Formatters.formatCup(sourceAvailable)}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.amberWarningText
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Vista previa después de guardar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Disponible:", style = MaterialTheme.typography.bodySmall)
                            CupAmountText(
                                amount = previewDisponible,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (previewDisponible < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("En ahorro:", style = MaterialTheme.typography.bodySmall)
                            CupAmountText(
                                amount = previewAhorro,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (previewAhorro < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("comm_mov_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.replace(',', '.').toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        amountError = "Ingresa un monto mayor que cero"
                        return@Button
                    }
                    onSave(
                        Formatters.combineDateAndTime(datePart, timePart),
                        parsed,
                        category,
                        note.trim().takeIf { it.isNotEmpty() }
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_comm_mov_button")
            ) {
                Text("Guardar", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}
