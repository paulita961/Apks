package com.example.export

import com.example.BuildConfig
import com.example.data.prefs.PreferencesDataStore
import com.example.data.repository.BackupSnapshot
import com.example.domain.AppSettings
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.CupDenominations
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AppVersionInfo {
    const val APP_VERSION = BuildConfig.VERSION_NAME
    const val VERSION_CODE = BuildConfig.VERSION_CODE
    const val SCHEMA_VERSION = 3
    const val BUILD_DATE = "20 nov 2026"
}

data class BackupPayload(
    val persons: List<Person>,
    val operations: List<Operation>,
    val commissionMovements: List<CommissionMovement>,
    val settings: AppSettings,
    val sourceAppVersion: String?,
    val sourceSchemaVersion: Int
)

object BackupManager {

    fun exportBackupJson(snapshot: BackupSnapshot, updatedLastBackupAt: String): String {
        val root = JSONObject()
        root.put("appVersion", AppVersionInfo.APP_VERSION)
        root.put("appVersionCode", AppVersionInfo.VERSION_CODE)
        root.put("version", AppVersionInfo.SCHEMA_VERSION)
        root.put("exportedAt", updatedLastBackupAt)

        val personsArr = JSONArray()
        snapshot.persons.forEach { p ->
            personsArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    if (!p.receiptName.isNullOrBlank()) put("receiptName", p.receiptName)
                    put("commissionRate", p.commissionRate)
                    put("defaultCommissionMode", p.defaultCommissionMode.wireValue)
                    put("archived", p.archived)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                }
            )
        }
        root.put("persons", personsArr)

        val opsArr = JSONArray()
        snapshot.operations.forEach { op ->
            opsArr.put(
                JSONObject().apply {
                    put("id", op.id)
                    put("personId", op.personId)
                    put("type", op.type.wireValue)
                    put("date", op.date)
                    put("amount", op.amount)
                    if (op.commissionRateSnapshot != null) put("commissionRateSnapshot", op.commissionRateSnapshot)
                    if (op.commissionMode != null) put("commissionMode", op.commissionMode.wireValue)
                    if (op.commissionAmount != null) put("commissionAmount", op.commissionAmount)
                    if (!op.note.isNullOrBlank()) put("note", op.note)
                    if (!op.billBreakdown.isNullOrEmpty()) {
                        val billsObj = JSONObject()
                        op.billBreakdown.forEach { (denom, count) ->
                            if (count > 0) billsObj.put(denom.toString(), count)
                        }
                        if (billsObj.length() > 0) put("billBreakdown", billsObj)
                    }
                    put("createdAt", op.createdAt)
                    put("updatedAt", op.updatedAt)
                }
            )
        }
        root.put("operations", opsArr)

        val movArr = JSONArray()
        snapshot.movements.forEach { m ->
            movArr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("date", m.date)
                    put("amount", m.amount)
                    put("category", m.category.wireValue)
                    if (!m.note.isNullOrBlank()) put("note", m.note)
                    put("createdAt", m.createdAt)
                    put("updatedAt", m.updatedAt)
                }
            )
        }
        root.put("commissionMovements", movArr)

        val settingsObj = JSONObject().apply {
            if (snapshot.settings.dailyCardLimit != null) {
                put("dailyCardLimit", snapshot.settings.dailyCardLimit)
            } else {
                put("dailyCardLimit", JSONObject.NULL)
            }
            put("visibleDenominations", JSONArray(snapshot.settings.visibleDenominations))
            put("lastBackupAt", updatedLastBackupAt)
            put("firstUsedAt", snapshot.settings.firstUsedAt)
            put("settingsUpdatedAt", snapshot.settings.settingsUpdatedAt)
            put("syncWorkspaceId", snapshot.settings.syncWorkspaceId)
            put("notifications", PreferencesDataStore.notificationPrefsToJson(snapshot.settings.notifications))
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    fun parseAndValidateBackupJson(rawJson: String): Result<BackupPayload> {
        return runCatching {
            val trimmed = rawJson.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty()) error("El archivo está vacío.")
            val root = JSONObject(trimmed)

            if (!root.has("persons") || !root.has("operations")) {
                error("Formato inválido: faltan las listas 'persons' u 'operations'.")
            }

            val now = Formatters.nowEpochMs()
            val personsJson = root.getJSONArray("persons")
            val operationsJson = root.getJSONArray("operations")
            val movementsJson = root.optJSONArray("commissionMovements") ?: JSONArray()

            val persons = mutableListOf<Person>()
            val personIds = mutableSetOf<String>()

            for (i in 0 until personsJson.length()) {
                val obj = personsJson.getJSONObject(i)
                val id = obj.optNullableString("id") ?: UUID.randomUUID().toString()
                val name = obj.optNullableString("name")
                    ?: error("El titular en la posición $i no tiene nombre.")
                if (!personIds.add(id)) error("ID de titular duplicado: $id")

                val receiptName = obj.optNullableString("receiptName")
                val rate = obj.optDouble("commissionRate", 5.0).let {
                    if (it.isNaN() || it < 0.0 || it > 100.0) 5.0 else it
                }
                val defaultMode = CommissionMode.fromWire(obj.optNullableString("defaultCommissionMode") ?: "descontada")
                val archived = obj.optBoolean("archived", false)
                val createdAt = Formatters.parseEpochMs(obj.opt("createdAt"), now)
                val updatedAt = Formatters.parseEpochMs(obj.opt("updatedAt"), createdAt)

                persons.add(
                    Person(
                        id = id,
                        name = name,
                        receiptName = receiptName,
                        commissionRate = rate,
                        defaultCommissionMode = defaultMode,
                        archived = archived,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }

            val operations = mutableListOf<Operation>()
            val opIds = mutableSetOf<String>()

            for (i in 0 until operationsJson.length()) {
                val obj = operationsJson.getJSONObject(i)
                val id = obj.optNullableString("id") ?: UUID.randomUUID().toString()
                if (!opIds.add(id)) error("ID de operación duplicado: $id")

                val personId = obj.optNullableString("personId") ?: ""
                if (personId !in personIds) {
                    error("La operación $id hace referencia a un titular inexistente ($personId).")
                }

                val typeRaw = obj.optNullableString("type") ?: ""
                val type = OperationType.entries.firstOrNull { it.wireValue == typeRaw }
                    ?: error("Tipo de operación inválido en $id: '$typeRaw'.")

                val amount = obj.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount < 0.0) {
                    error("Monto inválido en la operación $id.")
                }

                val date = Formatters.normalizeIsoDateTime(obj.optNullableString("date"))
                val rateSnap = if (obj.has("commissionRateSnapshot") && !obj.isNull("commissionRateSnapshot")) {
                    obj.optDouble("commissionRateSnapshot").takeIf { !it.isNaN() }
                } else null

                val commMode = if (obj.has("commissionMode") && !obj.isNull("commissionMode")) {
                    CommissionMode.fromWire(obj.optNullableString("commissionMode"))
                } else if (type == OperationType.EXTRACCION) {
                    CommissionMode.DESCONTADA
                } else null

                val commAmount = if (obj.has("commissionAmount") && !obj.isNull("commissionAmount")) {
                    obj.optDouble("commissionAmount").takeIf { !it.isNaN() && it >= 0.0 }
                } else null

                val note = obj.optNullableString("note")

                val billsObj = obj.optJSONObject("billBreakdown")
                val billMap = if (billsObj != null) {
                    val map = linkedMapOf<Int, Int>()
                    val keys = billsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val d = k.toIntOrNull()
                        val c = billsObj.optInt(k, 0)
                        if (d != null && c > 0) map[d] = c
                    }
                    map.takeIf { it.isNotEmpty() }
                } else null

                val createdAt = Formatters.parseEpochMs(obj.opt("createdAt"), now)
                val updatedAt = Formatters.parseEpochMs(obj.opt("updatedAt"), createdAt)

                operations.add(
                    Operation(
                        id = id,
                        personId = personId,
                        type = type,
                        date = date,
                        amount = Formatters.round2(amount),
                        commissionRateSnapshot = rateSnap,
                        commissionMode = commMode,
                        commissionAmount = commAmount?.let { Formatters.round2(it) },
                        note = note,
                        billBreakdown = billMap,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }

            val movements = mutableListOf<CommissionMovement>()
            val movIds = mutableSetOf<String>()

            for (i in 0 until movementsJson.length()) {
                val obj = movementsJson.getJSONObject(i)
                val id = obj.optNullableString("id") ?: UUID.randomUUID().toString()
                if (!movIds.add(id)) error("ID de movimiento de comisión duplicado: $id")

                val catRaw = obj.optNullableString("category") ?: ""
                val category = CommissionMovementCategory.entries.firstOrNull { it.wireValue == catRaw }
                    ?: error("Categoría de movimiento inválida en $id: '$catRaw'.")

                val amount = obj.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount < 0.0) {
                    error("Monto inválido en el movimiento de comisión $id.")
                }

                val date = Formatters.normalizeIsoDateTime(obj.optNullableString("date"))
                val note = obj.optNullableString("note")
                val createdAt = Formatters.parseEpochMs(obj.opt("createdAt"), now)
                val updatedAt = Formatters.parseEpochMs(obj.opt("updatedAt"), createdAt)

                movements.add(
                    CommissionMovement(
                        id = id,
                        date = date,
                        amount = Formatters.round2(amount),
                        category = category,
                        note = note,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }

            val settingsJson = root.optJSONObject("settings")
            val dailyLimit = if (settingsJson != null && settingsJson.has("dailyCardLimit") && !settingsJson.isNull("dailyCardLimit")) {
                settingsJson.optDouble("dailyCardLimit").takeIf { !it.isNaN() && it > 0.0 }
            } else null

            val visibleDenoms = if (settingsJson != null && settingsJson.has("visibleDenominations")) {
                val arr = settingsJson.optJSONArray("visibleDenominations")
                if (arr != null) {
                    val parsed = (0 until arr.length()).mapNotNull { idx ->
                        arr.optInt(idx, -1).takeIf { it in CupDenominations.ALL }
                    }
                    if (parsed.isEmpty()) CupDenominations.DEFAULT_VISIBLE
                    else CupDenominations.ALL.filter { it in parsed }
                } else CupDenominations.DEFAULT_VISIBLE
            } else CupDenominations.DEFAULT_VISIBLE

            val lastBackupAt = settingsJson?.optNullableString("lastBackupAt")
            val firstUsedAt = settingsJson?.optNullableString("firstUsedAt")
                ?: Formatters.currentIsoTimestamp()
            val settingsUpdatedAt = Formatters.parseEpochMs(settingsJson?.opt("settingsUpdatedAt"), now)
            val workspaceId = Formatters.sanitizeWorkspaceId(
                settingsJson?.optNullableString("syncWorkspaceId") ?: "principal"
            )
            val notifications = PreferencesDataStore.parseNotificationPrefs(
                settingsJson?.optJSONObject("notifications")
            )

            BackupPayload(
                persons = persons,
                operations = operations.sortedWith(FinanceCalculator.operationDescendingComparator),
                commissionMovements = movements.sortedWith(FinanceCalculator.movementDescendingComparator),
                settings = AppSettings(
                    dailyCardLimit = dailyLimit,
                    visibleDenominations = visibleDenoms,
                    lastBackupAt = lastBackupAt,
                    firstUsedAt = firstUsedAt,
                    settingsUpdatedAt = settingsUpdatedAt,
                    syncWorkspaceId = workspaceId,
                    notifications = notifications
                ),
                sourceAppVersion = root.optNullableString("appVersion"),
                sourceSchemaVersion = root.optInt("version", 1)
            )
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().takeIf { it.isNotEmpty() }
    }

    fun exportCsv(snapshot: BackupSnapshot): String {
        val personMap = snapshot.persons.associateBy { it.id }

        data class CsvRow(
            val datePart: String,
            val timePart: String,
            val createdAt: Long,
            val line: String
        )

        val rows = mutableListOf<CsvRow>()

        snapshot.operations.forEach { op ->
            val datePart = Formatters.extractDatePart(op.date)
            val timePart = Formatters.extractTimePart(op.date)
            val titular = personMap[op.personId]?.name ?: "Titular eliminado"
            val tipo = op.type.label
            val monto = formatCsvNumber(op.amount)
            val modalidad = if (op.type == OperationType.EXTRACCION) {
                op.commissionMode?.shortLabel ?: ""
            } else ""
            val porcentaje = if (op.type == OperationType.EXTRACCION && op.commissionRateSnapshot != null) {
                formatCsvNumber(op.commissionRateSnapshot)
            } else ""
            val comision = if (op.type == OperationType.EXTRACCION) {
                formatCsvNumber(FinanceCalculator.effectiveCommission(op))
            } else ""
            val nota = op.note.orEmpty()
            val billetes = Formatters.formatBillBreakdownCompact(op.billBreakdown)

            val cols = listOf(
                datePart, timePart, titular, tipo, monto,
                modalidad, porcentaje, comision, nota, billetes
            ).joinToString(",") { escapeCsv(it) }

            rows.add(CsvRow(datePart, timePart, op.createdAt, cols))
        }

        snapshot.movements.forEach { m ->
            val datePart = Formatters.extractDatePart(m.date)
            val timePart = Formatters.extractTimePart(m.date)
            val cols = listOf(
                datePart,
                timePart,
                "Mi comisión",
                m.category.label,
                formatCsvNumber(m.amount),
                "",
                "",
                "",
                m.note.orEmpty(),
                ""
            ).joinToString(",") { escapeCsv(it) }

            rows.add(CsvRow(datePart, timePart, m.createdAt, cols))
        }

        rows.sortWith { a, b ->
            val d = b.datePart.compareTo(a.datePart)
            if (d != 0) return@sortWith d
            val t = b.timePart.compareTo(a.timePart)
            if (t != 0) return@sortWith t
            b.createdAt.compareTo(a.createdAt)
        }

        return buildString {
            append('\uFEFF')
            appendLine("Fecha,Hora,Titular,Tipo,Monto (CUP),Modalidad comisión,Porcentaje (%),Comisión (CUP),Nota,Desglose de billetes")
            rows.forEach { appendLine(it.line) }
        }
    }

    private fun formatCsvNumber(value: Double): String {
        val rounded = Formatters.round2(value)
        return if (kotlin.math.abs(rounded - rounded.toLong()) < 0.005) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", rounded)
        }
    }

    private fun escapeCsv(field: String): String {
        val needsQuotes = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuotes) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
