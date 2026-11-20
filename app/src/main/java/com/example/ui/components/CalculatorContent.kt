package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.CalculatorHistoryItem
import com.example.domain.Formatters
import com.example.domain.Person
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import kotlin.math.abs

private enum class CalcSubMode(val label: String) {
    BASIC("Básica"),
    COMMISSION("Comisión")
}

@Composable
fun CalculatorContent(
    persons: List<Person>,
    history: List<CalculatorHistoryItem>,
    onRecordHistory: (expression: String, result: String) -> Unit,
    onClearHistory: () -> Unit,
    isPickerMode: Boolean = false,
    onUseResult: ((Double) -> Unit)? = null,
    bottomContentPadding: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(CalcSubMode.BASIC) }

    Column(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            CalcSubMode.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mode == item,
                    onClick = { mode = item },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CalcSubMode.entries.size
                    ),
                    modifier = Modifier.testTag("calc_mode_${item.name.lowercase()}")
                ) {
                    Text(item.label)
                }
            }
        }

        when (mode) {
            CalcSubMode.BASIC -> BasicCalculatorPane(
                history = history,
                onRecordHistory = onRecordHistory,
                onClearHistory = onClearHistory,
                isPickerMode = isPickerMode,
                onUseResult = onUseResult,
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            CalcSubMode.COMMISSION -> CommissionCalculatorPane(
                persons = persons,
                isPickerMode = isPickerMode,
                onUseResult = onUseResult,
                bottomContentPadding = bottomContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun BasicCalculatorPane(
    history: List<CalculatorHistoryItem>,
    onRecordHistory: (expression: String, result: String) -> Unit,
    onClearHistory: () -> Unit,
    isPickerMode: Boolean,
    onUseResult: ((Double) -> Unit)?,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    var expression by remember { mutableStateOf("0") }
    var lastResult by remember { mutableStateOf<Double?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewValue = remember(expression) { evaluateExpression(expression) }

    fun appendToken(token: String) {
        errorMsg = null
        val ops = setOf("+", "−", "×", "÷")
        if (expression == "0" && token in "0".."9") {
            expression = token
            return
        }
        if (token in ops) {
            if (expression.isEmpty()) return
            val last = expression.last().toString()
            expression = if (last in ops) {
                expression.dropLast(1) + token
            } else {
                expression + token
            }
            return
        }
        if (token == "%") {
            if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
                expression += "%"
            }
            return
        }
        if (token == ".") {
            val lastSegment = expression.split('+', '−', '×', '÷').lastOrNull().orEmpty()
            if (!lastSegment.contains('.')) {
                expression += if (lastSegment.isEmpty()) "0." else "."
            }
            return
        }
        expression += token
    }

    fun onEquals() {
        val evaluated = evaluateExpression(expression)
        if (evaluated == null || evaluated.isNaN() || evaluated.isInfinite()) {
            errorMsg = "Expresión inválida o división entre cero"
        } else {
            val formatted = formatCalcResult(evaluated)
            if (expression != formatted) {
                onRecordHistory(expression, formatted)
            }
            expression = formatted
            lastResult = Formatters.round2(evaluated)
            errorMsg = null
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_display")
                    )
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (previewValue != null && !previewValue.isNaN() && !previewValue.isInfinite()) {
                        Text(
                            text = "= ${Formatters.formatCup(previewValue)}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            val rows = listOf(
                listOf("C", "⌫", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("00", "0", ".", "=")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val isEquals = key == "="
                            val isClearOrDel = key == "C" || key == "⌫"
                            val isOp = key in setOf("÷", "×", "−", "+", "%")
                            Box(modifier = Modifier.weight(1f)) {
                                when {
                                    isEquals -> Button(
                                        onClick = { onEquals() },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("calc_key_equals")
                                    ) {
                                        Text("=", style = MaterialTheme.typography.titleLarge)
                                    }
                                    key == "⌫" -> FilledTonalButton(
                                        onClick = {
                                            errorMsg = null
                                            expression = if (expression.length <= 1) "0" else expression.dropLast(1)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("calc_key_backspace")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                            contentDescription = "Borrar dígito"
                                        )
                                    }
                                    isClearOrDel || isOp -> FilledTonalButton(
                                        onClick = {
                                            if (key == "C") {
                                                expression = "0"
                                                lastResult = null
                                                errorMsg = null
                                            } else {
                                                appendToken(key)
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = if (key == "C") {
                                            ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        } else {
                                            ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("calc_key_$key")
                                    ) {
                                        Text(key, style = MaterialTheme.typography.titleMedium)
                                    }
                                    else -> OutlinedCard(
                                        onClick = { appendToken(key) },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("calc_key_$key")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(54.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge.tabular(),
                                                fontWeight = FontWeight.SemiBold
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

        if (isPickerMode && onUseResult != null) {
            item {
                val usable = previewValue?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
                    ?: lastResult?.takeIf { it > 0.0 }
                Button(
                    onClick = {
                        if (usable != null) onUseResult(Formatters.round2(usable))
                    },
                    enabled = usable != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("use_calc_result_button")
                ) {
                    Text(
                        text = if (usable != null) {
                            "Usar resultado (${Formatters.formatCup(usable)})"
                        } else {
                            "Ingresa un monto válido para usar"
                        },
                        style = MaterialTheme.typography.labelLarge.tabular()
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historial reciente (${history.size}/10)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("clear_calc_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Borrar historial",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Borrar historial")
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "Las últimas 10 operaciones que calcules aparecerán aquí para reutilizarlas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(history, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expression = item.result
                            lastResult = item.result.toDoubleOrNull()
                            errorMsg = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.expression,
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "= ${item.result}",
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommissionCalculatorPane(
    persons: List<Person>,
    isPickerMode: Boolean,
    onUseResult: ((Double) -> Unit)?,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    val activePersons = remember(persons) { persons.filter { !it.archived } }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var rateText by remember { mutableStateOf("5") }
    var amountText by remember { mutableStateOf("") }
    val semantic = LocalSemanticColors.current

    val amountVal = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val rateVal = (rateText.replace(',', '.').toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val commission = Formatters.round2(amountVal * rateVal / 100.0)
    val entregaDescontada = Formatters.round2((amountVal - commission).coerceAtLeast(0.0))
    val entregaAparte = Formatters.round2(amountVal)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activePersons.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedPerson?.let {
                        "${it.name} (${Formatters.formatRate(it.commissionRate)})"
                    } ?: "Porcentaje manual",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tomar % de un titular (opcional)") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Porcentaje manual") },
                        onClick = {
                            selectedPerson = null
                            dropdownExpanded = false
                        }
                    )
                    activePersons.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${Formatters.formatRate(p.commissionRate)})") },
                            onClick = {
                                selectedPerson = p
                                rateText = if (p.commissionRate % 1.0 == 0.0) {
                                    p.commissionRate.toInt().toString()
                                } else {
                                    p.commissionRate.toString()
                                }
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val cleaned = input.replace(',', '.')
                    if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) {
                        amountText = cleaned
                    }
                },
                label = { Text("Monto (CUP)") },
                placeholder = { Text("20000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.tabular(),
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("comm_calc_amount_input")
            )

            OutlinedTextField(
                value = rateText,
                onValueChange = { input ->
                    val cleaned = input.replace(',', '.')
                    if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                        rateText = cleaned
                        selectedPerson = null
                    }
                },
                label = { Text("% Comisión") },
                suffix = { Text("%") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.tabular(),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("comm_calc_rate_input")
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Comisión calculada (${Formatters.formatRate(rateVal)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                CupAmountText(
                    amount = commission,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = semantic.actionDeliveryBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Entrega si la descuento",
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.actionDeliveryFg
                    )
                    CupAmountText(
                        amount = entregaDescontada,
                        style = MaterialTheme.typography.titleLarge,
                        color = semantic.actionDeliveryFg
                    )
                    Text(
                        text = "Restando ${Formatters.formatCup(commission)}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = semantic.actionDeliveryFg.copy(alpha = 0.8f)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = semantic.actionCommissionBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Entrega si la paga aparte",
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.actionCommissionFg
                    )
                    CupAmountText(
                        amount = entregaAparte,
                        style = MaterialTheme.typography.titleLarge,
                        color = semantic.actionCommissionFg
                    )
                    Text(
                        text = "Cobra aparte ${Formatters.formatCup(commission)}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = semantic.actionCommissionFg.copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (isPickerMode && onUseResult != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onUseResult(entregaDescontada) },
                    enabled = entregaDescontada > 0.0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Usar descontada")
                }
                Button(
                    onClick = { onUseResult(amountVal) },
                    enabled = amountVal > 0.0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Usar monto")
                }
            }
        }
    }
}

private fun formatCalcResult(value: Double): String {
    val rounded = Formatters.round2(value)
    return if (abs(rounded - rounded.toLong()) < 0.0001) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

internal fun evaluateExpression(expr: String): Double? {
    val cleaned = expr.trim()
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(" ", "")
    if (cleaned.isEmpty()) return null

    // Tokenize numbers (with optional trailing %) and operators
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < cleaned.length) {
        val ch = cleaned[i]
        if (ch.isDigit() || ch == '.') {
            val sb = StringBuilder()
            while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) {
                sb.append(cleaned[i])
                i++
            }
            if (i < cleaned.length && cleaned[i] == '%') {
                sb.append('%')
                i++
            }
            tokens.add(sb.toString())
        } else if (ch in setOf('+', '-', '*', '/')) {
            if (ch == '-' && (tokens.isEmpty() || tokens.last() in setOf("+", "-", "*", "/"))) {
                // Unary minus
                i++
                val sb = StringBuilder("-")
                while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) {
                    sb.append(cleaned[i])
                    i++
                }
                if (sb.length == 1) return null
                if (i < cleaned.length && cleaned[i] == '%') {
                    sb.append('%')
                    i++
                }
                tokens.add(sb.toString())
            } else {
                tokens.add(ch.toString())
                i++
            }
        } else {
            return null
        }
    }

    if (tokens.isEmpty() || tokens.last() in setOf("+", "-", "*", "/")) return null

    fun parseNum(tok: String): Pair<Double, Boolean>? {
        val isPct = tok.endsWith("%")
        val raw = if (isPct) tok.dropLast(1) else tok
        val v = raw.toDoubleOrNull() ?: return null
        return v to isPct
    }

    // First pass: evaluate * and /
    val values = mutableListOf<Double>()
    val ops = mutableListOf<String>()

    val first = parseNum(tokens[0]) ?: return null
    values.add(if (first.second) first.first / 100.0 else first.first)

    var idx = 1
    while (idx + 1 < tokens.size) {
        val op = tokens[idx]
        val rightPair = parseNum(tokens[idx + 1]) ?: return null
        if (op == "*" || op == "/") {
            val left = values.removeAt(values.lastIndex)
            val rightVal = if (rightPair.second) rightPair.first / 100.0 else rightPair.first
            if (op == "/" && abs(rightVal) < 1e-12) return null
            values.add(if (op == "*") left * rightVal else left / rightVal)
        } else {
            ops.add(op)
            val rightVal = if (rightPair.second) {
                // For + or -, B% means B% of the accumulated left side
                val base = values.last()
                base * (rightPair.first / 100.0)
            } else {
                rightPair.first
            }
            values.add(rightVal)
        }
        idx += 2
    }

    // Second pass: + and -
    var acc = values[0]
    for (j in ops.indices) {
        acc = if (ops[j] == "+") acc + values[j + 1] else acc - values[j + 1]
    }
    return acc
}
