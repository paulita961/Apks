package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.components.BillCounterContent
import com.example.ui.components.CalculatorContent

private enum class ToolTab(val label: String) {
    BILL_COUNTER("Contador de billetes"),
    CALCULATOR("Calculadora")
}

@Composable
fun ToolsScreen(
    uiState: MainUiState,
    onUpdateBillCounter: (Map<Int, Int>, String) -> Unit,
    onClearBillCounter: () -> Unit,
    onRecordCalcHistory: (String, String) -> Unit,
    onClearCalcHistory: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    var activeTool by rememberSaveable { mutableStateOf(ToolTab.BILL_COUNTER) }

    Column(modifier = modifier.fillMaxSize()) {
        // Sticky top selector
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                ToolTab.entries.forEachIndexed { idx, tab ->
                    SegmentedButton(
                        selected = activeTool == tab,
                        onClick = { activeTool = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = ToolTab.entries.size
                        ),
                        modifier = Modifier.testTag("tools_tab_${tab.name.lowercase()}")
                    ) {
                        Text(tab.label, maxLines = 1)
                    }
                }
            }
        }

        when (activeTool) {
            ToolTab.BILL_COUNTER -> BillCounterContent(
                visibleDenominations = uiState.settings.visibleDenominations,
                counts = uiState.billCounterState.counts,
                expectedAmountText = uiState.billCounterState.expectedAmount,
                onCountsChanged = { newCounts ->
                    onUpdateBillCounter(newCounts, uiState.billCounterState.expectedAmount)
                },
                onExpectedChanged = { newExpected ->
                    onUpdateBillCounter(uiState.billCounterState.counts, newExpected)
                },
                onClearAll = onClearBillCounter,
                isPickerMode = false,
                bottomContentPadding = bottomPadding + 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            ToolTab.CALCULATOR -> CalculatorContent(
                persons = uiState.persons,
                history = uiState.calculatorHistory,
                onRecordHistory = onRecordCalcHistory,
                onClearHistory = onClearCalcHistory,
                isPickerMode = false,
                bottomContentPadding = bottomPadding + 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
