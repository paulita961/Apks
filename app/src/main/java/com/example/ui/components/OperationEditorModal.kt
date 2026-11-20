package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.CalculatorHistoryItem
import com.example.domain.CommissionMode
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.domain.PersonSummary
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperationEditorModal(
    editingOperation: Operation?,
    initialPersonId: String?,
    initialType: OperationType?,
    persons: List<Person>,
    personSummaries: List<PersonSummary>,
    allOperations: List<Operation>,
    dailyCardLimit: Double?,
    visibleDenominations: List<Int>,
    calculatorHistory: List<CalculatorHistoryItem>,
    onRecordCalcHistory: (String, String) -> Unit,
    onClearCalcHistory: () -> Unit,
    onSave: (
        personId: String,
        type: OperationType,
        date: String,
        amount: Double,
        rateSnapshot: Double?,
        commissionMode: CommissionMode?,
        commissionAmount: Double?,
        note: String?,
        billBreakdown: Map<Int, Int>?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val selectablePersons = remember(persons, editingOperation) {
        persons.filter { !it.archived || it.id == editingOperation?.personId }
    }
    val initialPerson = remember(selectablePersons, editingOperation, initialPersonId) {
        selectablePersons.firstOrNull { it.id == (editingOperation?.personId ?: initialPersonId) }
            ?: selectablePersons.firstOrNull()
    }

    var selectedPersonId by remember(initialPerson) { mutableStateOf(initialPerson?.id.orEmpty()) }
    var personDropdownOpen by remember { mutableStateOf(false) }

    val selectedPerson = remember(selectablePersons, selectedPersonId) {
        selectablePersons.firstOrNull { it.id == selectedPersonId }
    }
    val selectedPersonSummary = remember(personSummaries, selectedPersonId) {
        personSummaries.firstOrNull { it.person.id == selectedPersonId }?.summary
    }

    var selectedType by remember(editingOperation, initialType) {
        mutableStateOf(editingOperation?.type ?: initialType ?: OperationType.EXTRACCION)
    }

    val initialIso = editingOperation?.date ?: Formatters.currentIsoDateTime()
    var datePart by remember(editingOperation) { mutableStateOf(Formatters.extractDatePart(initialIso)) }
    var timePart by remember(editingOperation) { mutableStateOf(Formatters.extractTimePart(initialIso)) }

    var amountManuallyEdited by remember(editingOperation) { mutableStateOf(editingOperation != null) }

    fun suggestedAmountFor(type: OperationType, personId: String): String {
        if (editingOperation != null) return ""
        val sum = personSummaries.firstOrNull { it.person.id == personId }?.summary ?: return ""
        val suggested = when (type) {
            OperationType.ENTREGA -> sum.porEntregar
            OperationType.COBRO_COMISION -> sum.comisionPendienteCobro
            else -> 0.0
        }
        return if (suggested > 0.005) {
            val rounded = Formatters.round2(suggested)
            if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        } else ""
    }

    var amountText by remember(editingOperation, initialType, initialPerson) {
        mutableStateOf(
            if (editingOperation != null) {
                val a = editingOperation.amount
                if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()
            } else {
                suggestedAmountFor(
                    initialType ?: OperationType.EXTRACCION,
                    initialPerson?.id.orEmpty()
                )
            }
        )
    }

    var commissionMode by remember(editingOperation, initialPerson) {
        mutableStateOf(
            editingOperation?.commissionMode
                ?: initialPerson?.defaultCommissionMode
                ?: CommissionMode.DESCONTADA
        )
    }

    val activeRate = remember(editingOperation, selectedPerson) {
        editingOperation?.commissionRateSnapshot ?: selectedPerson?.commissionRate ?: 5.0
    }

    var commissionManuallyEdited by remember(editingOperation) {
        mutableStateOf(
            editingOperation != null &&
                editingOperation.type == OperationType.EXTRACCION &&
                editingOperation.commissionAmount != null
        )
    }

    var commissionText by remember(editingOperation) {
        mutableStateOf(
            if (editingOperation != null && editingOperation.type == OperationType.EXTRACCION) {
                val c = FinanceCalculator.effectiveCommission(editingOperation)
                if (c % 1.0 == 0.0) c.toLong().toString() else c.toString()
            } else {
                val initialAmt = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val c = Formatters.round2(initialAmt * activeRate / 100.0)
                if (c > 0.0) (if (c % 1.0 == 0.0) c.toLong().toString() else c.toString()) else ""
            }
        )
    }

    var noteText by remember(editingOperation) { mutableStateOf(editingOperation?.note.orEmpty()) }
    var billBreakdown by remember(editingOperation) { mutableStateOf(editingOperation?.billBreakdown) }
    var amountError by remember { mutableStateOf<String?>(null) }

    var showBillCounterPicker by remember { mutableStateOf(false) }
    var showCalculatorPicker by remember { mutableStateOf(false) }
    var pickerBillCounts by remember { mutableStateOf(editingOperation?.billBreakdown ?: emptyMap()) }
    var pickerSaveBreakdown by remember { mutableStateOf(true) }

    val semantic = LocalSemanticColors.current

    fun recalculateCommission(amtStr: String, rate: Double) {
        if (!commissionManuallyEdited) {
            val amt = amtStr.replace(',', '.').toDoubleOrNull() ?: 0.0
            val c = Formatters.round2(amt * rate / 100.0)
            commissionText = if (amt > 0.0) {
                if (c % 1.0 == 0.0) c.toLong().toString() else c.toString()
            } else ""
        }
    }

    val parsedAmount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedCommission = if (selectedType == OperationType.EXTRACCION) {
        commissionText.replace(',', '.').toDoubleOrNull()
            ?: Formatters.round2(parsedAmount * activeRate / 100.0)
    } else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    title = {
                        Text(
                            text = if (editingOperation == null) "Registrar operación" else "Editar operación",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val amt = amountText.replace(',', '.').toDoubleOrNull()
                                if (amt == null || amt <= 0.0) {
                                    amountError = "Ingresa un monto mayor que cero"
                                    return@TextButton
                                }
                                if (selectedPersonId.isEmpty()) return@TextButton
                                val commAmt = if (selectedType == OperationType.EXTRACCION) {
                                    (commissionText.replace(',', '.').toDoubleOrNull()
                                        ?: Formatters.round2(amt * activeRate / 100.0)).coerceAtLeast(0.0)
                                } else null
                                onSave(
                                    selectedPersonId,
                                    selectedType,
                                    Formatters.combineDateAndTime(datePart, timePart),
                                    amt,
                                    if (selectedType == OperationType.EXTRACCION) activeRate else null,
                                    if (selectedType == OperationType.EXTRACCION) commissionMode else null,
                                    commAmt,
                                    noteText.trim().takeIf { it.isNotEmpty() },
                                    if (selectedType == OperationType.EXTRACCION || selectedType == OperationType.ENTREGA) billBreakdown else null
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("op_save_top_button")
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Operation Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tipo de operación", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OperationType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    if (!amountManuallyEdited) {
                                        val suggested = suggestedAmountFor(type, selectedPersonId)
                                        amountText = suggested
                                        recalculateCommission(suggested, activeRate)
                                    }
                                },
                                label = { Text(type.label) },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("op_type_${type.wireValue}")
                            )
                        }
                    }
                }

                // Person Selector
                ExposedDropdownMenuBox(
                    expanded = personDropdownOpen,
                    onExpandedChange = { personDropdownOpen = !personDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedPerson?.name ?: "Seleccionar titular",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Titular") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personDropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("op_person_selector")
                    )
                    ExposedDropdownMenu(
                        expanded = personDropdownOpen,
                        onDismissRequest = { personDropdownOpen = false }
                    ) {
                        selectablePersons.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text("${p.name} (${Formatters.formatRate(p.commissionRate)})")
                                },
                                onClick = {
                                    selectedPersonId = p.id
                                    personDropdownOpen = false
                                    if (editingOperation == null) {
                                        commissionMode = p.defaultCommissionMode
                                        if (!amountManuallyEdited) {
                                            val suggested = suggestedAmountFor(selectedType, p.id)
                                            amountText = suggested
                                            recalculateCommission(suggested, p.commissionRate)
                                        } else {
                                            recalculateCommission(amountText, p.commissionRate)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Date & Time
                DateTimePickerRow(
                    datePart = datePart,
                    timePart = timePart,
                    onDateSelected = { datePart = it },
                    onTimeSelected = { timePart = it }
                )

                // Amount Field + Auxiliary Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val cleaned = input.replace(',', '.')
                            if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                                amountText = cleaned
                                amountManuallyEdited = true
                                amountError = null
                                recalculateCommission(cleaned, activeRate)
                            }
                        },
                        label = { Text("Monto (CUP) *") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleLarge.tabular(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("op_amount_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedType == OperationType.EXTRACCION || selectedType == OperationType.ENTREGA) {
                            OutlinedButton(
                                onClick = {
                                    pickerBillCounts = billBreakdown ?: emptyMap()
                                    showBillCounterPicker = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("op_open_bill_counter")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Contar billetes", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = { showCalculatorPicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("op_open_calculator")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Calculadora", maxLines = 1)
                        }
                    }

                    if (!billBreakdown.isNullOrEmpty() &&
                        (selectedType == OperationType.EXTRACCION || selectedType == OperationType.ENTREGA)
                    ) {
                        InputChip(
                            selected = true,
                            onClick = { showBillCounterPicker = true },
                            label = {
                                Text(
                                    text = "Billetes: ${Formatters.formatBillBreakdownCompact(billBreakdown)}",
                                    style = MaterialTheme.typography.bodySmall.tabular()
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { billBreakdown = null },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Quitar desglose",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                // Extraction-specific fields & warnings
                if (selectedType == OperationType.EXTRACCION) {
                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Modalidad de comisión", style = MaterialTheme.typography.labelLarge)

                        Card(
                            onClick = { commissionMode = CommissionMode.DESCONTADA },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (commissionMode == CommissionMode.DESCONTADA) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (commissionMode == CommissionMode.DESCONTADA) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = commissionMode == CommissionMode.DESCONTADA,
                                    onClick = { commissionMode = CommissionMode.DESCONTADA }
                                )
                                Text(
                                    text = "La descuento del efectivo (Entrego el resto)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Card(
                            onClick = { commissionMode = CommissionMode.APARTE },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (commissionMode == CommissionMode.APARTE) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (commissionMode == CommissionMode.APARTE) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = commissionMode == CommissionMode.APARTE,
                                    onClick = { commissionMode = CommissionMode.APARTE }
                                )
                                Text(
                                    text = "La paga el titular aparte (Entrego todo el efectivo)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commissionText,
                            onValueChange = { input ->
                                val cleaned = input.replace(',', '.')
                                if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                                    commissionText = cleaned
                                    commissionManuallyEdited = true
                                }
                            },
                            label = { Text("Comisión de esta extracción (CUP)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.titleMedium.tabular(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("op_commission_input")
                        )

                        if (commissionManuallyEdited) {
                            TextButton(
                                onClick = {
                                    commissionManuallyEdited = false
                                    recalculateCommission(amountText, activeRate)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = "Restablecer porcentaje",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(Formatters.formatRate(activeRate))
                            }
                        }
                    }

                    // Preview of this extraction's impact
                    val toDeliverNow = if (commissionMode == CommissionMode.DESCONTADA) {
                        Formatters.round2(parsedAmount - parsedCommission)
                    } else {
                        Formatters.round2(parsedAmount)
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Por entregar de esta extracción",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                CupAmountText(
                                    amount = toDeliverNow,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (commissionMode == CommissionMode.APARTE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Comisión pendiente de cobro",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.orangePendingText
                                    )
                                    CupAmountText(
                                        amount = parsedCommission,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = semantic.orangePendingText
                                    )
                                }
                            }
                        }
                    }

                    // Daily Card Limit Info Card
                    if (dailyCardLimit != null && dailyCardLimit > 0.0) {
                        val alreadyToday = FinanceCalculator.extractedOnDate(
                            operations = allOperations,
                            isoDate = datePart,
                            excludeOperationId = editingOperation?.id
                        )
                        val remainingAfter = Formatters.round2(dailyCardLimit - alreadyToday - parsedAmount)
                        val isOverLimit = remainingAfter < -0.005

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOverLimit) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isOverLimit) "Límite diario de tarjeta superado" else "Límite diario de la tarjeta",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Límite configurado:", style = MaterialTheme.typography.bodySmall)
                                    CupAmountText(amount = dailyCardLimit, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ya extraído el ${Formatters.formatDateShort(datePart)}:", style = MaterialTheme.typography.bodySmall)
                                    CupAmountText(amount = alreadyToday, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isOverLimit) "Excedido por:" else "Disponible después:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    CupAmountText(
                                        amount = kotlin.math.abs(remainingAfter),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isOverLimit) MaterialTheme.colorScheme.error else semantic.positiveText
                                    )
                                }
                            }
                        }
                    }

                    // Non-blocking amber warning if extracted > received for this person
                    if (selectedPersonSummary != null && parsedAmount > 0.0) {
                        val prevOpAmount = if (editingOperation != null &&
                            editingOperation.personId == selectedPersonId &&
                            editingOperation.type == OperationType.EXTRACCION
                        ) editingOperation.amount else 0.0
                        val projectedExtracted = Formatters.round2(
                            selectedPersonSummary.totalExtraido - prevOpAmount + parsedAmount
                        )
                        if (projectedExtracted > selectedPersonSummary.totalRecibido + 0.005) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(semantic.amberWarningBg, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = "Advertencia",
                                    tint = semantic.amberWarningText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Aviso: lo extraído (${Formatters.formatCup(projectedExtracted)}) supera lo recibido de este titular (${Formatters.formatCup(selectedPersonSummary.totalRecibido)}).",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = semantic.amberWarningText
                                )
                            }
                        }
                    }
                }

                // Optional Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota (opcional)") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("op_note_input")
                )

                Button(
                    onClick = {
                        val amt = amountText.replace(',', '.').toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            amountError = "Ingresa un monto mayor que cero"
                            return@Button
                        }
                        if (selectedPersonId.isEmpty()) return@Button
                        val commAmt = if (selectedType == OperationType.EXTRACCION) {
                            (commissionText.replace(',', '.').toDoubleOrNull()
                                ?: Formatters.round2(amt * activeRate / 100.0)).coerceAtLeast(0.0)
                        } else null
                        onSave(
                            selectedPersonId,
                            selectedType,
                            Formatters.combineDateAndTime(datePart, timePart),
                            amt,
                            if (selectedType == OperationType.EXTRACCION) activeRate else null,
                            if (selectedType == OperationType.EXTRACCION) commissionMode else null,
                            commAmt,
                            noteText.trim().takeIf { it.isNotEmpty() },
                            if (selectedType == OperationType.EXTRACCION || selectedType == OperationType.ENTREGA) billBreakdown else null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("op_save_bottom_button")
                ) {
                    Text(
                        text = if (editingOperation == null) "Guardar operación" else "Actualizar operación",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showBillCounterPicker) {
        Dialog(
            onDismissRequest = { showBillCounterPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { showBillCounterPicker = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar contador")
                            }
                        },
                        title = { Text("Contar billetes") }
                    )
                    var pickerExpected by remember { mutableStateOf(amountText) }
                    BillCounterContent(
                        visibleDenominations = visibleDenominations,
                        counts = pickerBillCounts,
                        expectedAmountText = pickerExpected,
                        onCountsChanged = { pickerBillCounts = it },
                        onExpectedChanged = { pickerExpected = it },
                        onClearAll = {
                            pickerBillCounts = emptyMap()
                            pickerExpected = ""
                        },
                        isPickerMode = true,
                        saveBreakdownWithOp = pickerSaveBreakdown,
                        onSaveBreakdownChanged = { pickerSaveBreakdown = it },
                        onUseTotal = { total, breakdown ->
                            val formatted = if (total % 1.0 == 0.0) total.toLong().toString() else total.toString()
                            amountText = formatted
                            amountManuallyEdited = true
                            amountError = null
                            billBreakdown = breakdown
                            recalculateCommission(formatted, activeRate)
                            showBillCounterPicker = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showCalculatorPicker) {
        Dialog(
            onDismissRequest = { showCalculatorPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { showCalculatorPicker = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar calculadora")
                            }
                        },
                        title = { Text("Calculadora") }
                    )
                    CalculatorContent(
                        persons = persons,
                        history = calculatorHistory,
                        onRecordHistory = onRecordCalcHistory,
                        onClearHistory = onClearCalcHistory,
                        isPickerMode = true,
                        onUseResult = { result ->
                            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
                            amountText = formatted
                            amountManuallyEdited = true
                            amountError = null
                            recalculateCommission(formatted, activeRate)
                            showCalculatorPicker = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
