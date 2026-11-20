package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.CommissionMode
import com.example.domain.Person

@Composable
fun PersonFormModal(
    editingPerson: Person?,
    onSave: (name: String, receiptName: String?, rate: Double, defaultMode: CommissionMode) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(editingPerson) { mutableStateOf(editingPerson?.name.orEmpty()) }
    var receiptName by remember(editingPerson) { mutableStateOf(editingPerson?.receiptName.orEmpty()) }
    var rateText by remember(editingPerson) {
        mutableStateOf(
            if (editingPerson != null) {
                if (editingPerson.commissionRate % 1.0 == 0.0) editingPerson.commissionRate.toInt().toString()
                else editingPerson.commissionRate.toString()
            } else "5"
        )
    }
    var defaultMode by remember(editingPerson) {
        mutableStateOf(editingPerson?.defaultCommissionMode ?: CommissionMode.DESCONTADA)
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingPerson == null) "Nuevo titular" else "Editar titular",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Alias o nombre interno *") },
                    placeholder = { Text("Ej. Tarjeta Juan / Casa") },
                    supportingText = {
                        Text(nameError ?: "Visible solo dentro de la app, nunca se incluye automáticamente en PDF.")
                    },
                    isError = nameError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("person_name_input")
                )

                OutlinedTextField(
                    value = receiptName,
                    onValueChange = { receiptName = it },
                    label = { Text("Nombre para comprobantes PDF (opcional)") },
                    placeholder = { Text("Nombre real o público") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("person_receipt_name_input")
                )

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { input ->
                        val cleaned = input.replace(',', '.')
                        if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                            rateText = cleaned
                            rateError = null
                        }
                    },
                    label = { Text("Comisión (%)") },
                    suffix = { Text("%") },
                    isError = rateError != null,
                    supportingText = rateError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("person_rate_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Modalidad predeterminada",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = defaultMode == CommissionMode.DESCONTADA,
                            onClick = { defaultMode = CommissionMode.DESCONTADA },
                            label = { Text("Descontada") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("person_mode_descontada")
                        )
                        FilterChip(
                            selected = defaultMode == CommissionMode.APARTE,
                            onClick = { defaultMode = CommissionMode.APARTE },
                            label = { Text("Aparte") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("person_mode_aparte")
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        nameError = "Ingresa un alias o nombre interno"
                        return@Button
                    }
                    val parsedRate = rateText.toDoubleOrNull()
                    if (parsedRate == null || parsedRate < 0.0 || parsedRate > 100.0) {
                        rateError = "Ingresa un porcentaje entre 0 y 100"
                        return@Button
                    }
                    onSave(trimmedName, receiptName.trim().takeIf { it.isNotEmpty() }, parsedRate, defaultMode)
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_person_button")
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
