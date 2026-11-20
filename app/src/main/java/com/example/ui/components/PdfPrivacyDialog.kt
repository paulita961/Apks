package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.Operation
import com.example.domain.PdfReceiptFormat
import com.example.domain.Person
import com.example.pdf.ReceiptPdfGenerator
import com.example.ui.theme.LocalSemanticColors
import java.io.File
import java.io.FileInputStream

@Composable
fun PdfPrivacyDialog(
    person: Person,
    operations: List<Operation>,
    onSaveReceiptName: (String) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val semantic = LocalSemanticColors.current

    // Prefill ONLY with receiptName if present, NEVER with internal person.name
    var pdfName by remember(person) { mutableStateOf(person.receiptName.orEmpty()) }
    var omitName by remember(person) { mutableStateOf(false) }
    var saveAsReceiptName by remember(person) { mutableStateOf(false) }
    var selectedFormat by remember(person) { mutableStateOf(PdfReceiptFormat.SIMPLE) }
    var generatedFile by remember { mutableStateOf<File?>(null) }

    val canSaveNewReceiptName = !omitName &&
        pdfName.trim().isNotEmpty() &&
        pdfName.trim() != person.receiptName.orEmpty()

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = generatedFile
        if (uri != null && file != null && file.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
            }.onSuccess {
                onMessage("PDF guardado correctamente en el dispositivo.")
            }.onFailure {
                onMessage("No se pudo guardar el PDF: ${it.localizedMessage}")
            }
        }
    }

    if (generatedFile == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("Generar comprobante PDF", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Privacy Guarantee Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PrivacyTip,
                            contentDescription = "Privacidad",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Privacidad: tu alias interno en la app es \"${person.name}\" y nunca se incluirá automáticamente en el PDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    OutlinedTextField(
                        value = pdfName,
                        onValueChange = { pdfName = it },
                        enabled = !omitName,
                        label = { Text("Nombre que aparecerá en el PDF") },
                        placeholder = { Text("Opcional (nombre real o público)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_display_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = omitName,
                            onCheckedChange = { omitName = it },
                            modifier = Modifier.testTag("pdf_omit_name_checkbox")
                        )
                        Text(
                            text = "Omitir nombre en el comprobante",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (canSaveNewReceiptName) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAsReceiptName,
                                onCheckedChange = { saveAsReceiptName = it },
                                modifier = Modifier.testTag("pdf_save_receipt_name_checkbox")
                            )
                            Text(
                                text = "Guardar como nombre para comprobantes de este titular",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Formato del comprobante",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Card(
                            onClick = { selectedFormat = PdfReceiptFormat.SIMPLE },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selectedFormat == PdfReceiptFormat.SIMPLE) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFormat == PdfReceiptFormat.SIMPLE) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.testTag("pdf_format_simple")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFormat == PdfReceiptFormat.SIMPLE,
                                    onClick = { selectedFormat = PdfReceiptFormat.SIMPLE }
                                )
                                Column {
                                    Text("Simple", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Solo saldos de efectivo e historial sin comisiones ni pagos de comisión.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Card(
                            onClick = { selectedFormat = PdfReceiptFormat.WITH_COMMISSIONS },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selectedFormat == PdfReceiptFormat.WITH_COMMISSIONS) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFormat == PdfReceiptFormat.WITH_COMMISSIONS) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.testTag("pdf_format_commissions")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFormat == PdfReceiptFormat.WITH_COMMISSIONS,
                                    onClick = { selectedFormat = PdfReceiptFormat.WITH_COMMISSIONS }
                                )
                                Column {
                                    Text("Con comisiones", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Saldos, resumen de comisiones, porcentaje acordado y columna de comisión.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (canSaveNewReceiptName && saveAsReceiptName) {
                            onSaveReceiptName(pdfName.trim())
                        }
                        val result = ReceiptPdfGenerator.generatePdf(
                            context = context,
                            request = ReceiptPdfGenerator.PdfGenerationRequest(
                                person = person,
                                operations = operations,
                                displayName = pdfName.trim().takeIf { it.isNotEmpty() },
                                omitName = omitName,
                                format = selectedFormat
                            )
                        )
                        result.onSuccess { file ->
                            generatedFile = file
                        }.onFailure { err ->
                            onMessage("Error al generar PDF: ${err.localizedMessage}")
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("generate_pdf_confirm_button")
                ) {
                    Text("Generar PDF", fontWeight = FontWeight.SemiBold)
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
    } else {
        val file = generatedFile!!
        val uri = remember(file) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = semantic.positiveText,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("PDF listo", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Archivo generado: ${file.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Compartir comprobante PDF")
                                )
                            }.onFailure {
                                onMessage("No se encontró aplicación para compartir.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("share_pdf_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir PDF")
                    }

                    OutlinedButton(
                        onClick = {
                            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(viewIntent)
                            }.onFailure {
                                onMessage("No hay visor de PDF instalado en el dispositivo.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("open_pdf_button")
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir con visor PDF")
                    }

                    OutlinedButton(
                        onClick = { savePdfLauncher.launch(file.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_pdf_device_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar en descargas / carpeta")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}
