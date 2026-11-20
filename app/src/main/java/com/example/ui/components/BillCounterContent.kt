package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.CupDenominations
import com.example.domain.Formatters
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import kotlin.math.abs

@Composable
fun BillCounterContent(
    visibleDenominations: List<Int>,
    counts: Map<Int, Int>,
    expectedAmountText: String,
    onCountsChanged: (Map<Int, Int>) -> Unit,
    onExpectedChanged: (String) -> Unit,
    onClearAll: () -> Unit,
    isPickerMode: Boolean = false,
    saveBreakdownWithOp: Boolean = true,
    onSaveBreakdownChanged: ((Boolean) -> Unit)? = null,
    onUseTotal: ((total: Double, breakdown: Map<Int, Int>?) -> Unit)? = null,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    val activeDenoms = remember(visibleDenominations, counts) {
        val set = (visibleDenominations + counts.filterValues { it > 0 }.keys).toSet()
        CupDenominations.ALL.filter { it in set }
    }

    val totalCounted = remember(counts) {
        counts.entries.sumOf { (denom, qty) -> denom.toLong() * qty.toLong() }.toDouble()
    }
    val totalBills = remember(counts) {
        counts.values.sum()
    }
    val expectedValue = expectedAmountText.replace(',', '.').toDoubleOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        // Pinned / Sticky Header Summary
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total contado",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CupAmountText(
                            amount = totalCounted,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$totalBills billetes en total",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = totalBills > 0 || expectedAmountText.isNotBlank(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bill_counter_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Limpiar contador",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Limpiar")
                    }
                }

                OutlinedTextField(
                    value = expectedAmountText,
                    onValueChange = { input ->
                        val cleaned = input.replace(',', '.')
                        if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                            onExpectedChanged(cleaned)
                        }
                    },
                    label = { Text("Monto esperado (CUP)") },
                    placeholder = { Text("Opcional para cuadrar efectivo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bill_counter_expected_input")
                )

                if (expectedValue != null && expectedValue >= 0.0) {
                    val diff = Formatters.round2(totalCounted - expectedValue)
                    val isExact = abs(diff) < 0.01
                    val bgColor = if (isExact) semantic.positiveBg else MaterialTheme.colorScheme.errorContainer
                    val fgColor = if (isExact) semantic.positiveText else MaterialTheme.colorScheme.error
                    val statusText = when {
                        isExact -> "Cuadra exacto"
                        diff > 0 -> "Sobra ${Formatters.formatCup(diff)}"
                        else -> "Falta ${Formatters.formatCup(abs(diff))}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isExact) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                            contentDescription = statusText,
                            tint = fgColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = fgColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isPickerMode && onUseTotal != null) {
                    if (onSaveBreakdownChanged != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveBreakdownWithOp,
                                onCheckedChange = onSaveBreakdownChanged,
                                modifier = Modifier.testTag("save_breakdown_checkbox")
                            )
                            Text(
                                text = "Guardar desglose de billetes junto con la operación",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val cleanMap = counts.filterValues { it > 0 }.takeIf { it.isNotEmpty() }
                            onUseTotal(totalCounted, if (saveBreakdownWithOp) cleanMap else null)
                        },
                        enabled = totalCounted > 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("use_bill_total_button")
                    ) {
                        Text(
                            text = "Usar este total (${Formatters.formatCup(totalCounted)})",
                            style = MaterialTheme.typography.labelLarge.tabular()
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeDenoms, key = { it }) { denom ->
                val count = counts[denom] ?: 0
                DenominationRow(
                    denomination = denom,
                    count = count,
                    onCountChange = { newCount ->
                        val mutable = counts.toMutableMap()
                        if (newCount <= 0) mutable.remove(denom) else mutable[denom] = newCount
                        onCountsChanged(mutable)
                    }
                )
            }
        }
    }

    if (showClearConfirm) {
        ConfirmationDialog(
            title = "Limpiar contador",
            message = "¿Deseas poner en cero todas las denominaciones y borrar el monto esperado?",
            confirmText = "Limpiar",
            isDestructive = true,
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun DenominationRow(
    denomination: Int,
    count: Int,
    onCountChange: (Int) -> Unit
) {
    val subtotal = denomination.toLong() * count.toLong()
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (count > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (count > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
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
                    text = "${Formatters.formatNumber(denomination.toDouble())} CUP",
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (count > 0) "= ${Formatters.formatCup(subtotal.toDouble())}" else "0 CUP",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { if (count > 0) onCountChange(count - 1) },
                    enabled = count > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("denom_minus_$denomination")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Restar billete de $denomination"
                    )
                }

                OutlinedTextField(
                    value = if (count == 0) "" else count.toString(),
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(6)
                        onCountChange(digits.toIntOrNull() ?: 0)
                    },
                    placeholder = {
                        Text(
                            text = "0",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.tabular().copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(74.dp)
                        .testTag("denom_input_$denomination")
                )

                FilledTonalIconButton(
                    onClick = { onCountChange(count + 1) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("denom_plus_$denomination")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Sumar billete de $denomination"
                    )
                }
            }
        }
    }
}
