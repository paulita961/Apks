package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.CommissionMode
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.LedgerSummary
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.PdfReceiptFormat
import com.example.domain.Person
import com.example.export.AppVersionInfo
import java.io.File
import java.io.FileOutputStream

object ReceiptPdfGenerator {

    private const val PAGE_WIDTH = 612 // Letter width in points (8.5 in * 72)
    private const val PAGE_HEIGHT = 792 // Letter height in points (11 in * 72)
    private const val MARGIN_X = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_X * 2f // 532f
    private const val TOP_Y = 44f
    private const val FOOTER_LINE_Y = 744f
    private const val MAX_CONTENT_Y = 728f

    data class PdfGenerationRequest(
        val person: Person,
        val operations: List<Operation>,
        val displayName: String?,
        val omitName: Boolean,
        val format: PdfReceiptFormat
    )

    private data class PreparedRow(
        val op: Operation,
        val dateText: String,
        val typeText: String,
        val amountText: String,
        val modeText: String,
        val commText: String,
        val noteLines: List<String>,
        val rowHeight: Float
    )

    internal fun filterOperationsForFormat(
        operations: List<Operation>,
        format: PdfReceiptFormat
    ): List<Operation> = when (format) {
        PdfReceiptFormat.SIMPLE -> operations
            .filter { it.type != OperationType.COBRO_COMISION }
            .sortedWith(FinanceCalculator.operationDescendingComparator)
        PdfReceiptFormat.WITH_COMMISSIONS -> operations
            .sortedWith(FinanceCalculator.operationDescendingComparator)
    }

    private fun paginatePreparedRows(
        preparedRows: List<PreparedRow>,
        firstPageY: Float
    ): List<List<PreparedRow>> {
        val pages = mutableListOf<List<PreparedRow>>()
        if (preparedRows.isEmpty()) {
            pages.add(emptyList())
            return pages
        }
        var idx = 0
        var currentY = firstPageY
        var currentChunk = mutableListOf<PreparedRow>()
        while (idx < preparedRows.size) {
            val row = preparedRows[idx]
            if (currentY + row.rowHeight > MAX_CONTENT_Y && currentChunk.isNotEmpty()) {
                pages.add(currentChunk)
                currentChunk = mutableListOf()
                currentY = TOP_Y + 26f + 22f
            }
            currentChunk.add(row)
            currentY += row.rowHeight
            idx++
        }
        if (currentChunk.isNotEmpty()) {
            pages.add(currentChunk)
        }
        return pages
    }

    fun generatePdf(context: Context, request: PdfGenerationRequest): Result<File> {
        return runCatching {
            val filteredOps = filterOperationsForFormat(request.operations, request.format)

            val summary = FinanceCalculator.computeSummary(request.operations)
            val issuedAt = Formatters.formatDateTimeDisplay(Formatters.currentIsoDateTime())

            // Paints
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F4C5C")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF")
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 8.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val cellBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 8.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 0.8f
            }
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F4C5C")
                style = Paint.Style.FILL
            }
            val zebraBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }

            val noteMaxWidth = if (request.format == PdfReceiptFormat.SIMPLE) 172f else 112f
            val preparedRows = filteredOps.map { op ->
                val dateTxt = Formatters.formatDateTimeDisplay(op.date)
                val typeTxt = when (op.type) {
                    OperationType.RECIBIDO -> "Recibido"
                    OperationType.EXTRACCION -> "Extracción"
                    OperationType.ENTREGA -> "Entrega"
                    OperationType.COBRO_COMISION -> "Cobro comisión"
                }
                val amountTxt = Formatters.formatCup(op.amount)
                val modeTxt = if (op.type == OperationType.EXTRACCION) {
                    op.commissionMode?.shortLabel ?: CommissionMode.DESCONTADA.shortLabel
                } else "—"
                val commTxt = if (op.type == OperationType.EXTRACCION) {
                    Formatters.formatCup(FinanceCalculator.effectiveCommission(op))
                } else "—"
                val noteRaw = op.note?.trim().orEmpty().ifEmpty { "—" }
                val wrapped = wrapText(noteRaw, cellPaint, noteMaxWidth)
                val rowHeight = maxOf(22f, 10f + wrapped.size * 11.5f)
                PreparedRow(
                    op = op,
                    dateText = dateTxt,
                    typeText = typeTxt,
                    amountText = amountTxt,
                    modeText = modeTxt,
                    commText = commTxt,
                    noteLines = wrapped,
                    rowHeight = rowHeight
                )
            }

            val safeDisplayName = if (!request.omitName) request.displayName?.trim()?.takeIf { it.isNotEmpty() } else null
            val hasNameHeader = safeDisplayName != null
            var firstPageY = TOP_Y
            firstPageY += 22f // Title
            firstPageY += 16f // Subtitle / emitted
            if (hasNameHeader) firstPageY += 18f // Titular line
            firstPageY += 12f // Divider + gap
            firstPageY += 16f // Cash summary title
            firstPageY += 96f // Cash summary 2 rows of cards (44 + 8 + 44)
            if (request.format == PdfReceiptFormat.WITH_COMMISSIONS) {
                firstPageY += 16f // gap
                firstPageY += 16f // Commission summary title
                firstPageY += 54f // Commission cards row
            }
            firstPageY += 18f // gap
            firstPageY += 16f // History section title
            firstPageY += 22f // Table header

            // Paginate rows into pages
            val pages = paginatePreparedRows(preparedRows, firstPageY)

            val totalPages = pages.size
            val document = PdfDocument()
            try {
                pages.forEachIndexed { pageIndex, pageRows ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    var y = TOP_Y
                    if (pageIndex == 0) {
                        canvas.drawText("Comprobante de Operaciones", MARGIN_X, y + 14f, titlePaint)
                        y += 22f
                        val formatLabel = if (request.format == PdfReceiptFormat.SIMPLE) {
                            "Formato simple (sin detalle de comisiones)"
                        } else {
                            "Formato completo (con comisiones)"
                        }
                        canvas.drawText("Emitido: $issuedAt  ·  $formatLabel", MARGIN_X, y + 10f, subtitlePaint)
                        y += 16f

                        if (safeDisplayName != null) {
                            canvas.drawText(
                                "Titular: $safeDisplayName",
                                MARGIN_X,
                                y + 11f,
                                cellBoldPaint.apply { textSize = 11f }
                            )
                            cellBoldPaint.textSize = 8.8f
                            y += 18f
                        }

                        canvas.drawLine(MARGIN_X, y + 2f, MARGIN_X + CONTENT_WIDTH, y + 2f, linePaint)
                        y += 12f

                        // Cash Summary
                        canvas.drawText("Resumen de efectivo (CUP)", MARGIN_X, y + 10f, sectionPaint)
                        y += 16f

                        drawCashSummaryGrid(
                            canvas = canvas,
                            startY = y,
                            summary = summary,
                            cardBgPaint = cardBgPaint,
                            cardBorderPaint = cardBorderPaint,
                            labelPaint = labelPaint,
                            valuePaint = valuePaint
                        )
                        y += 96f

                        if (request.format == PdfReceiptFormat.WITH_COMMISSIONS) {
                            y += 16f
                            canvas.drawText(
                                "Resumen de comisiones (Tasa actual: ${Formatters.formatRate(request.person.commissionRate)} · Predeterminada: ${request.person.defaultCommissionMode.shortLabel})",
                                MARGIN_X,
                                y + 10f,
                                sectionPaint
                            )
                            y += 16f
                            drawCommissionSummaryGrid(
                                canvas = canvas,
                                startY = y,
                                summary = summary,
                                cardBgPaint = cardBgPaint,
                                cardBorderPaint = cardBorderPaint,
                                labelPaint = labelPaint,
                                valuePaint = valuePaint
                            )
                            y += 54f
                        }

                        y += 18f
                        canvas.drawText(
                            "Historial de operaciones (${filteredOps.size})",
                            MARGIN_X,
                            y + 10f,
                            sectionPaint
                        )
                        y += 16f
                    } else {
                        canvas.drawText(
                            "Historial de operaciones (continuación)",
                            MARGIN_X,
                            y + 12f,
                            sectionPaint
                        )
                        y += 26f
                    }

                    // Table header
                    drawTableHeader(
                        canvas = canvas,
                        y = y,
                        format = request.format,
                        headerBgPaint = headerBgPaint,
                        tableHeaderPaint = tableHeaderPaint
                    )
                    y += 22f

                    if (pageRows.isEmpty()) {
                        canvas.drawText(
                            "No hay operaciones registradas para mostrar en este formato.",
                            MARGIN_X + 8f,
                            y + 16f,
                            subtitlePaint
                        )
                    } else {
                        pageRows.forEachIndexed { rowIdx, row ->
                            drawTableRow(
                                canvas = canvas,
                                y = y,
                                row = row,
                                isEven = rowIdx % 2 == 1,
                                format = request.format,
                                zebraBgPaint = zebraBgPaint,
                                linePaint = linePaint,
                                cellPaint = cellPaint,
                                cellBoldPaint = cellBoldPaint
                            )
                            y += row.rowHeight
                        }
                    }

                    // Mandatory footer on every page
                    canvas.drawLine(MARGIN_X, FOOTER_LINE_Y, MARGIN_X + CONTENT_WIDTH, FOOTER_LINE_Y, linePaint)
                    val leftFooter = "Documento generado desde la app Control de Extracciones (v${AppVersionInfo.APP_VERSION})  |  $issuedAt"
                    val rightFooter = "Página ${pageIndex + 1} de $totalPages"
                    canvas.drawText(leftFooter, MARGIN_X, FOOTER_LINE_Y + 15f, footerPaint)
                    val rightWidth = footerPaint.measureText(rightFooter)
                    canvas.drawText(rightFooter, MARGIN_X + CONTENT_WIDTH - rightWidth, FOOTER_LINE_Y + 15f, footerPaint)

                    document.finishPage(page)
                }

                val outDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
                val timestampSlug = Formatters.currentIsoDateTime().replace(":", "-").replace("T", "_")
                val fileName = "Comprobante_${timestampSlug}.pdf"
                val outFile = File(outDir, fileName)
                FileOutputStream(outFile).use { fos ->
                    document.writeTo(fos)
                }
                outFile
            } finally {
                document.close()
            }
        }
    }

    private fun drawCashSummaryGrid(
        canvas: Canvas,
        startY: Float,
        summary: LedgerSummary,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        val gap = 8f
        val col3W = (CONTENT_WIDTH - gap * 2f) / 3f
        val col2W = (CONTENT_WIDTH - gap) / 2f
        val rowH = 44f

        val row1 = listOf(
            "Recibido" to Formatters.formatCup(summary.totalRecibido),
            "Extraído" to Formatters.formatCup(summary.totalExtraido),
            "Entregado" to Formatters.formatCup(summary.totalEntregado)
        )
        row1.forEachIndexed { idx, (label, valStr) ->
            val left = MARGIN_X + idx * (col3W + gap)
            drawSummaryBox(canvas, left, startY, col3W, rowH, label, valStr, cardBgPaint, cardBorderPaint, labelPaint, valuePaint, Color.parseColor("#0F172A"))
        }

        val row2Y = startY + rowH + 8f
        val porEntregarLabel = if (summary.porEntregar < 0) "Entregado de más" else "Por entregar"
        val porEntregarColor = if (summary.porEntregar < 0) Color.parseColor("#DC2626") else Color.parseColor("#0F4C5C")
        val row2 = listOf(
            Triple("Pendiente por extraer", Formatters.formatCup(summary.pendientePorExtraer), Color.parseColor("#0F172A")),
            Triple(porEntregarLabel, Formatters.formatCup(kotlin.math.abs(summary.porEntregar)), porEntregarColor)
        )
        row2.forEachIndexed { idx, (label, valStr, color) ->
            val left = MARGIN_X + idx * (col2W + gap)
            drawSummaryBox(canvas, left, row2Y, col2W, rowH, label, valStr, cardBgPaint, cardBorderPaint, labelPaint, valuePaint, color)
        }
    }

    private fun drawCommissionSummaryGrid(
        canvas: Canvas,
        startY: Float,
        summary: LedgerSummary,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        val gap = 8f
        val colW = (CONTENT_WIDTH - gap * 3f) / 4f
        val rowH = 44f
        val items = listOf(
            Triple("Generada", Formatters.formatCup(summary.comisionGenerada), Color.parseColor("#0F172A")),
            Triple("Descontada", Formatters.formatCup(summary.comisionDescontada), Color.parseColor("#0F172A")),
            Triple("Cobrada", Formatters.formatCup(summary.comisionCobrada), Color.parseColor("#059669")),
            Triple(
                "Pendiente",
                Formatters.formatCup(summary.comisionPendienteCobro),
                if (summary.comisionPendienteCobro > 0.005) Color.parseColor("#EA580C") else Color.parseColor("#0F172A")
            )
        )
        items.forEachIndexed { idx, (label, valStr, color) ->
            val left = MARGIN_X + idx * (colW + gap)
            drawSummaryBox(canvas, left, startY, colW, rowH, label, valStr, cardBgPaint, cardBorderPaint, labelPaint, valuePaint, color)
        }
    }

    private fun drawSummaryBox(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: String,
        bgPaint: Paint,
        borderPaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        valueColor: Int
    ) {
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
        canvas.drawText(label, x + 10f, y + 16f, labelPaint)
        val savedColor = valuePaint.color
        valuePaint.color = valueColor
        canvas.drawText(value, x + 10f, y + 34f, valuePaint)
        valuePaint.color = savedColor
    }

    private fun drawTableHeader(
        canvas: Canvas,
        y: Float,
        format: PdfReceiptFormat,
        headerBgPaint: Paint,
        tableHeaderPaint: Paint
    ) {
        canvas.drawRect(MARGIN_X, y, MARGIN_X + CONTENT_WIDTH, y + 22f, headerBgPaint)
        val textY = y + 14.5f
        if (format == PdfReceiptFormat.SIMPLE) {
            canvas.drawText("Fecha y hora", MARGIN_X + 8f, textY, tableHeaderPaint)
            canvas.drawText("Tipo", MARGIN_X + 130f, textY, tableHeaderPaint)
            canvas.drawText("Monto (CUP)", MARGIN_X + 240f, textY, tableHeaderPaint)
            canvas.drawText("Nota", MARGIN_X + 350f, textY, tableHeaderPaint)
        } else {
            canvas.drawText("Fecha y hora", MARGIN_X + 6f, textY, tableHeaderPaint)
            canvas.drawText("Tipo", MARGIN_X + 106f, textY, tableHeaderPaint)
            canvas.drawText("Monto", MARGIN_X + 186f, textY, tableHeaderPaint)
            canvas.drawText("Modalidad", MARGIN_X + 268f, textY, tableHeaderPaint)
            canvas.drawText("Comisión", MARGIN_X + 340f, textY, tableHeaderPaint)
            canvas.drawText("Nota", MARGIN_X + 416f, textY, tableHeaderPaint)
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        y: Float,
        row: PreparedRow,
        isEven: Boolean,
        format: PdfReceiptFormat,
        zebraBgPaint: Paint,
        linePaint: Paint,
        cellPaint: Paint,
        cellBoldPaint: Paint
    ) {
        if (isEven) {
            canvas.drawRect(MARGIN_X, y, MARGIN_X + CONTENT_WIDTH, y + row.rowHeight, zebraBgPaint)
        }
        val textY = y + 14f
        if (format == PdfReceiptFormat.SIMPLE) {
            canvas.drawText(row.dateText, MARGIN_X + 8f, textY, cellPaint)
            canvas.drawText(row.typeText, MARGIN_X + 130f, textY, cellPaint)
            canvas.drawText(row.amountText, MARGIN_X + 240f, textY, cellBoldPaint)
            row.noteLines.forEachIndexed { idx, line ->
                canvas.drawText(line, MARGIN_X + 350f, textY + idx * 11.5f, cellPaint)
            }
        } else {
            canvas.drawText(row.dateText, MARGIN_X + 6f, textY, cellPaint)
            canvas.drawText(row.typeText, MARGIN_X + 106f, textY, cellPaint)
            canvas.drawText(row.amountText, MARGIN_X + 186f, textY, cellBoldPaint)
            canvas.drawText(row.modeText, MARGIN_X + 268f, textY, cellPaint)
            canvas.drawText(row.commText, MARGIN_X + 340f, textY, cellPaint)
            row.noteLines.forEachIndexed { idx, line ->
                canvas.drawText(line, MARGIN_X + 416f, textY + idx * 11.5f, cellPaint)
            }
        }
        canvas.drawLine(MARGIN_X, y + row.rowHeight, MARGIN_X + CONTENT_WIDTH, y + row.rowHeight, linePaint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("—")
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                }
                if (paint.measureText(word) > maxWidth) {
                    var remaining = word
                    while (remaining.isNotEmpty()) {
                        var cut = remaining.length
                        while (cut > 1 && paint.measureText(remaining.substring(0, cut)) > maxWidth) {
                            cut--
                        }
                        lines.add(remaining.substring(0, cut))
                        remaining = remaining.substring(cut)
                    }
                    current = StringBuilder()
                } else {
                    current = StringBuilder(word)
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.isEmpty()) listOf("—") else lines
    }
}
