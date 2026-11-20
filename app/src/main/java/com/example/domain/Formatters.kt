package com.example.domain

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Formatters {
    private val spanishMonthsShort = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    private val spanishMonthsFull = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private val isoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val isoFullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun round2(value: Double): Double {
        return (value * 100.0).roundToLong() / 100.0
    }

    fun formatNumber(value: Double): String {
        val rounded = round2(value)
        val isNegative = rounded < -0.004
        val absVal = abs(rounded)
        val whole = absVal.toLong()
        val frac = ((absVal - whole) * 100.0).roundToLong()

        val adjustedWhole = if (frac >= 100L) whole + 1L else whole
        val adjustedFrac = if (frac >= 100L) 0L else frac

        val wholeStr = adjustedWhole.toString()
        val grouped = buildString {
            val len = wholeStr.length
            for (i in wholeStr.indices) {
                if (i > 0 && (len - i) % 3 == 0) {
                    append(' ')
                }
                append(wholeStr[i])
            }
        }
        val sign = if (isNegative) "-" else ""
        return if (adjustedFrac == 0L) {
            "$sign$grouped"
        } else {
            val fracStr = adjustedFrac.toString().padStart(2, '0')
            "$sign$grouped.$fracStr"
        }
    }

    fun formatCup(value: Double, includeUnit: Boolean = true): String {
        val num = formatNumber(value)
        return if (includeUnit) "$num CUP" else num
    }

    fun formatRate(rate: Double): String {
        val rounded = round2(rate)
        return if (abs(rounded - rounded.toLong()) < 0.005) {
            "${rounded.toLong()}%"
        } else {
            String.format(Locale.US, "%.2f%%", rounded).replace(".00%", "%")
        }
    }

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun currentIsoDateTime(): String = LocalDateTime.now().format(isoDateTimeFormatter)

    fun currentIsoTimestamp(): String = LocalDateTime.now().format(isoFullFormatter)

    fun currentIsoUtcTimestamp(): String = Instant.now().toString()

    fun currentIsoDate(): String = LocalDate.now().toString()

    fun parseEpochMs(raw: Any?, fallback: Long = nowEpochMs()): Long {
        if (raw == null) return fallback
        return when (raw) {
            is Number -> raw.toLong()
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return fallback
                trimmed.toLongOrNull()?.let { return it }
                runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { return it }
                runCatching {
                    LocalDateTime.parse(trimmed.take(19))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()?.let { return it }
                runCatching {
                    LocalDate.parse(trimmed.take(10))
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrElse { fallback }
            }
            else -> fallback
        }
    }

    fun formatEpochDisplay(epochMs: Long): String {
        val ldt = Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val monthShort = spanishMonthsShort.getOrElse(ldt.monthValue - 1) { "mes" }
        val day = ldt.dayOfMonth.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val mm = ldt.minute.toString().padStart(2, '0')
        return "$day $monthShort ${ldt.year} · $hh:$mm"
    }

    fun sanitizeWorkspaceId(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val ascii = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-', '_')
        return ascii.ifEmpty { "principal" }
    }

    fun extractDatePart(isoDateTime: String): String {
        return if (isoDateTime.length >= 10) isoDateTime.substring(0, 10) else currentIsoDate()
    }

    fun extractTimePart(isoDateTime: String): String {
        val tIdx = isoDateTime.indexOf('T')
        return if (tIdx != -1 && isoDateTime.length >= tIdx + 6) {
            isoDateTime.substring(tIdx + 1, tIdx + 6)
        } else {
            "00:00"
        }
    }

    fun normalizeIsoDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return currentIsoDateTime()
        val trimmed = raw.trim()
        return when {
            trimmed.length >= 16 && trimmed[10] == 'T' -> trimmed.substring(0, 16)
            trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-' -> "${trimmed}T00:00"
            else -> runCatching {
                LocalDateTime.parse(trimmed.take(19)).format(isoDateTimeFormatter)
            }.getOrElse { currentIsoDateTime() }
        }
    }

    fun combineDateAndTime(datePart: String, timePart: String): String {
        val safeDate = if (datePart.length >= 10) datePart.substring(0, 10) else currentIsoDate()
        val safeTime = if (timePart.length >= 5) timePart.substring(0, 5) else "00:00"
        return "${safeDate}T$safeTime"
    }

    fun formatDateTimeDisplay(isoDateTime: String): String {
        val normalized = normalizeIsoDateTime(isoDateTime)
        val date = runCatching { LocalDate.parse(extractDatePart(normalized)) }.getOrNull()
            ?: return normalized
        val time = extractTimePart(normalized)
        val monthShort = spanishMonthsShort.getOrElse(date.monthValue - 1) { "mes" }
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return "$day $monthShort ${date.year} · $time"
    }

    fun formatDateShort(isoDateTime: String): String {
        val date = runCatching { LocalDate.parse(extractDatePart(isoDateTime)) }.getOrNull()
            ?: return isoDateTime
        val monthShort = spanishMonthsShort.getOrElse(date.monthValue - 1) { "mes" }
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return "$day $monthShort ${date.year}"
    }

    fun monthNameFull(month1To12: Int): String =
        spanishMonthsFull.getOrElse(month1To12 - 1) { "Mes $month1To12" }

    fun monthNameShort(month1To12: Int): String =
        spanishMonthsShort.getOrElse(month1To12 - 1) { "$month1To12" }.replaceFirstChar { it.uppercase() }

    fun parseLocalDate(isoDateTime: String): LocalDate? =
        runCatching { LocalDate.parse(extractDatePart(isoDateTime)) }.getOrNull()

    fun parseLocalTime(isoDateTime: String): LocalTime? =
        runCatching { LocalTime.parse(extractTimePart(isoDateTime)) }.getOrNull()

    fun hoursSinceIsoDateTime(isoDateTime: String): Double {
        val normalized = normalizeIsoDateTime(isoDateTime)
        val date = parseLocalDate(normalized) ?: return 0.0
        val time = parseLocalTime(normalized) ?: LocalTime.MIDNIGHT
        val ldt = LocalDateTime.of(date, time)
        val minutes = Duration.between(ldt, LocalDateTime.now()).toMinutes()
        return (minutes / 60.0).coerceAtLeast(0.0)
    }

    fun daysSince(isoTimestampOrDate: String?): Long {
        if (isoTimestampOrDate.isNullOrBlank()) return 0L
        val date = parseLocalDate(isoTimestampOrDate) ?: return 0L
        return ChronoUnit.DAYS.between(date, LocalDate.now()).coerceAtLeast(0L)
    }

    fun normalizeSearch(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    fun formatBillBreakdownCompact(breakdown: Map<Int, Int>?): String {
        if (breakdown.isNullOrEmpty()) return ""
        return breakdown.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.key }
            .joinToString(", ") { "${it.value}×${formatNumber(it.key.toDouble())}" }
    }
}
