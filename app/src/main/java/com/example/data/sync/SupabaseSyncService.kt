package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import com.example.data.prefs.PreferencesDataStore
import com.example.domain.AppSettings
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.CupDenominations
import com.example.domain.DbConnectionStatus
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.Person
import com.example.domain.SyncEntityType
import com.example.domain.SyncTombstone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SyncStateSnapshot(
    val workspaceId: String,
    val persons: List<Person>,
    val operations: List<Operation>,
    val movements: List<CommissionMovement>,
    val tombstones: List<SyncTombstone>,
    val settings: AppSettings
)

data class RemoteWorkspaceData(
    val persons: List<Person>,
    val operations: List<Operation>,
    val movements: List<CommissionMovement>,
    val tombstones: List<SyncTombstone>,
    val settings: AppSettings?,
    val settingsUpdatedAt: Long
)

data class SyncMergeResult(
    val mergedPersons: List<Person>,
    val mergedOperations: List<Operation>,
    val mergedMovements: List<CommissionMovement>,
    val mergedTombstones: List<SyncTombstone>,
    val mergedSettings: AppSettings,
    val personsToPush: List<Person>,
    val operationsToPush: List<Operation>,
    val movementsToPush: List<CommissionMovement>,
    val tombstonesToPush: List<SyncTombstone>
)

object SyncMergeEngine {
    const val TOMBSTONE_RETENTION_MS = 180L * 24L * 60L * 60L * 1000L

    fun merge(
        workspaceId: String,
        local: SyncStateSnapshot,
        remote: RemoteWorkspaceData,
        nowMs: Long = Formatters.nowEpochMs()
    ): SyncMergeResult {
        val cutoff = nowMs - TOMBSTONE_RETENTION_MS

        // 1. Merge tombstones within 180-day retention window
        val tombstoneMap = linkedMapOf<Pair<SyncEntityType, String>, SyncTombstone>()
        (local.tombstones + remote.tombstones)
            .filter { it.deletedAt >= cutoff }
            .forEach { t ->
                val key = t.entityType to t.entityId
                val existing = tombstoneMap[key]
                if (existing == null || t.deletedAt > existing.deletedAt) {
                    tombstoneMap[key] = t.copy(workspaceId = workspaceId)
                }
            }

        // 2. Merge Persons (Last-Write-Wins + tombstone check)
        val localPersons = local.persons.associateBy { it.id }
        val remotePersons = remote.persons.associateBy { it.id }
        val allPersonIds = (localPersons.keys + remotePersons.keys).toSet()
        val mergedPersons = mutableListOf<Person>()
        val personsToPush = mutableListOf<Person>()

        for (id in allPersonIds) {
            val l = localPersons[id]
            val r = remotePersons[id]
            val tomb = tombstoneMap[SyncEntityType.PERSON to id]
            val winner = pickWinner(l, r, { it.updatedAt })
            if (winner != null && (tomb == null || winner.updatedAt > tomb.deletedAt)) {
                mergedPersons.add(winner)
                if (r == null || winner.updatedAt > r.updatedAt) {
                    personsToPush.add(winner)
                }
            }
        }
        val activePersonIds = mergedPersons.map { it.id }.toSet()

        // 3. Merge Operations (Last-Write-Wins + tombstone check + active parent person check)
        val localOps = local.operations.associateBy { it.id }
        val remoteOps = remote.operations.associateBy { it.id }
        val allOpIds = (localOps.keys + remoteOps.keys).toSet()
        val mergedOperations = mutableListOf<Operation>()
        val operationsToPush = mutableListOf<Operation>()

        for (id in allOpIds) {
            val l = localOps[id]
            val r = remoteOps[id]
            val tomb = tombstoneMap[SyncEntityType.OPERATION to id]
            val winner = pickWinner(l, r, { it.updatedAt })
            if (winner != null &&
                winner.personId in activePersonIds &&
                (tomb == null || winner.updatedAt > tomb.deletedAt)
            ) {
                mergedOperations.add(winner)
                if (r == null || winner.updatedAt > r.updatedAt) {
                    operationsToPush.add(winner)
                }
            }
        }

        // 4. Merge CommissionMovements (Last-Write-Wins + tombstone check)
        val localMovs = local.movements.associateBy { it.id }
        val remoteMovs = remote.movements.associateBy { it.id }
        val allMovIds = (localMovs.keys + remoteMovs.keys).toSet()
        val mergedMovements = mutableListOf<CommissionMovement>()
        val movementsToPush = mutableListOf<CommissionMovement>()

        for (id in allMovIds) {
            val l = localMovs[id]
            val r = remoteMovs[id]
            val tomb = tombstoneMap[SyncEntityType.COMMISSION_MOVEMENT to id]
            val winner = pickWinner(l, r, { it.updatedAt })
            if (winner != null && (tomb == null || winner.updatedAt > tomb.deletedAt)) {
                mergedMovements.add(winner)
                if (r == null || winner.updatedAt > r.updatedAt) {
                    movementsToPush.add(winner)
                }
            }
        }

        // 5. Merge Settings
        val lSet = local.settings
        val rSet = remote.settings
        val rUpdated = remote.settingsUpdatedAt
        val lUpdated = lSet.settingsUpdatedAt

        val mergedFirstUsed = listOfNotNull(
            lSet.firstUsedAt.takeIf { it.isNotBlank() },
            rSet?.firstUsedAt?.takeIf { it.isNotBlank() }
        ).minOrNull().orEmpty().ifBlank { Formatters.currentIsoTimestamp() }

        val mergedLastBackup = listOfNotNull(
            lSet.lastBackupAt?.takeIf { it.isNotBlank() },
            rSet?.lastBackupAt?.takeIf { it.isNotBlank() }
        ).maxOrNull()

        val useRemote = rSet != null && rUpdated > lUpdated
        val mergedSettings = AppSettings(
            dailyCardLimit = if (useRemote) rSet!!.dailyCardLimit else lSet.dailyCardLimit,
            visibleDenominations = if (useRemote) rSet!!.visibleDenominations else lSet.visibleDenominations,
            lastBackupAt = mergedLastBackup,
            firstUsedAt = mergedFirstUsed,
            settingsUpdatedAt = maxOf(lUpdated, rUpdated, nowMs.takeIf { lUpdated == 0L && rUpdated == 0L } ?: 0L),
            syncWorkspaceId = workspaceId,
            lastSyncedAt = nowMs,
            hasPendingSync = false,
            notifications = if (useRemote) rSet!!.notifications else lSet.notifications
        )

        val remoteTombMap = remote.tombstones.associateBy { it.entityType to it.entityId }
        val tombstonesToPush = tombstoneMap.values.filter { t ->
            val rt = remoteTombMap[t.entityType to t.entityId]
            rt == null || t.deletedAt > rt.deletedAt
        }

        return SyncMergeResult(
            mergedPersons = mergedPersons.sortedBy { it.createdAt },
            mergedOperations = mergedOperations.sortedWith(FinanceCalculator.operationDescendingComparator),
            mergedMovements = mergedMovements.sortedWith(FinanceCalculator.movementDescendingComparator),
            mergedTombstones = tombstoneMap.values.toList(),
            mergedSettings = mergedSettings,
            personsToPush = personsToPush,
            operationsToPush = operationsToPush,
            movementsToPush = movementsToPush,
            tombstonesToPush = tombstonesToPush
        )
    }

    private inline fun <T> pickWinner(local: T?, remote: T?, updatedAt: (T) -> Long): T? {
        if (local == null) return remote
        if (remote == null) return local
        return if (updatedAt(local) >= updatedAt(remote)) local else remote
    }
}

class SupabaseSyncService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun resolvedBaseUrl(): String {
        val raw = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (raw.isEmpty() || raw.contains("YOUR_SUPABASE_URL")) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else ""
    }

    private fun resolvedApiKey(): String {
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anon.isNotEmpty() && !anon.contains("YOUR_SUPABASE_ANON_KEY")) return anon
        val service = BuildConfig.SUPABASE_SERVICE_KEY.trim()
        if (service.isNotEmpty() && !service.contains("YOUR_SUPABASE")) return service
        return ""
    }

    fun isConfigured(): Boolean = resolvedBaseUrl().isNotEmpty() && resolvedApiKey().isNotEmpty()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun checkConnection(): Pair<DbConnectionStatus, String?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext DbConnectionStatus.NOT_CONFIGURED to "Credenciales de Supabase no configuradas."
        }
        if (!isNetworkAvailable()) {
            return@withContext DbConnectionStatus.OFFLINE to "Sin conexión a internet."
        }
        runCatching {
            val req = baseRequest("${resolvedBaseUrl()}/rest/v1/sync_workspaces?select=id&limit=1")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    DbConnectionStatus.CONNECTED to null
                } else {
                    DbConnectionStatus.ERROR to "HTTP ${resp.code}: ${resp.message}"
                }
            }
        }.getOrElse { err ->
            if (!isNetworkAvailable()) {
                DbConnectionStatus.OFFLINE to "Sin conexión a internet."
            } else {
                DbConnectionStatus.ERROR to (err.localizedMessage ?: "Error al conectar con Supabase")
            }
        }
    }

    suspend fun syncBidirectional(local: SyncStateSnapshot): Result<SyncMergeResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured()) error("Supabase no configurado.")
            if (!isNetworkAvailable()) error("Sin conexión a internet.")

            val baseUrl = resolvedBaseUrl()
            val wsId = Formatters.sanitizeWorkspaceId(local.workspaceId)
            val encodedWs = URLEncoder.encode(wsId, "UTF-8")

            // 1. Fetch remote tables for this workspace
            val wsArray = getJsonArray("$baseUrl/rest/v1/sync_workspaces?id=eq.$encodedWs&select=*")
            val personsArray = getJsonArray("$baseUrl/rest/v1/persons?workspace_id=eq.$encodedWs&select=*")
            val opsArray = getJsonArray("$baseUrl/rest/v1/operations?workspace_id=eq.$encodedWs&select=*")
            val movsArray = getJsonArray("$baseUrl/rest/v1/commission_movements?workspace_id=eq.$encodedWs&select=*")
            val tombsArray = getJsonArray("$baseUrl/rest/v1/sync_tombstones?workspace_id=eq.$encodedWs&select=*")

            val (remoteSettings, remoteSettingsUpdatedAt) = parseRemoteWorkspace(wsArray, wsId)
            val remoteData = RemoteWorkspaceData(
                persons = parsePersons(personsArray),
                operations = parseOperations(opsArray),
                movements = parseMovements(movsArray),
                tombstones = parseTombstones(tombsArray, wsId),
                settings = remoteSettings,
                settingsUpdatedAt = remoteSettingsUpdatedAt
            )

            // 2. Merge locally with Last-Write-Wins + 180d tombstones
            val merged = SyncMergeEngine.merge(
                workspaceId = wsId,
                local = local,
                remote = remoteData
            )

            // 3. Push delta updates to Supabase
            if (merged.personsToPush.isNotEmpty()) {
                upsertJson(
                    url = "$baseUrl/rest/v1/persons?on_conflict=workspace_id,id",
                    bodyArray = personsToJsonArray(wsId, merged.personsToPush)
                )
            }
            if (merged.operationsToPush.isNotEmpty()) {
                upsertJson(
                    url = "$baseUrl/rest/v1/operations?on_conflict=workspace_id,id",
                    bodyArray = operationsToJsonArray(wsId, merged.operationsToPush)
                )
            }
            if (merged.movementsToPush.isNotEmpty()) {
                upsertJson(
                    url = "$baseUrl/rest/v1/commission_movements?on_conflict=workspace_id,id",
                    bodyArray = movementsToJsonArray(wsId, merged.movementsToPush)
                )
            }
            if (merged.tombstonesToPush.isNotEmpty()) {
                upsertJson(
                    url = "$baseUrl/rest/v1/sync_tombstones?on_conflict=workspace_id,entity_type,entity_id",
                    bodyArray = tombstonesToJsonArray(wsId, merged.tombstonesToPush)
                )
            }

            // Always upsert workspace settings row so workspace exists and settings stay synced
            val wsPayload = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("id", wsId)
                        put("settings", settingsToJson(merged.mergedSettings))
                        put("settings_updated_at", merged.mergedSettings.settingsUpdatedAt)
                        put("updated_at", Formatters.currentIsoUtcTimestamp())
                    }
                )
            }
            upsertJson(
                url = "$baseUrl/rest/v1/sync_workspaces?on_conflict=id",
                bodyArray = wsPayload
            )

            merged
        }
    }

    private fun baseRequest(url: String): Request.Builder {
        val key = resolvedApiKey()
        return Request.Builder()
            .url(url)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
    }

    private fun getJsonArray(url: String): JSONArray {
        val req = baseRequest(url).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("Error leyendo Supabase (${resp.code}): ${raw.take(180)}")
            }
            return if (raw.isBlank()) JSONArray() else JSONArray(raw)
        }
    }

    private fun upsertJson(url: String, bodyArray: JSONArray) {
        val body = bodyArray.toString().toRequestBody(jsonMediaType)
        val req = baseRequest(url)
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                error("Error subiendo a Supabase (${resp.code}): ${raw.take(180)}")
            }
        }
    }

    private fun parseRemoteWorkspace(arr: JSONArray, wsId: String): Pair<AppSettings?, Long> {
        if (arr.length() == 0) return null to 0L
        val row = arr.getJSONObject(0)
        val settingsObj = row.optJSONObject("settings") ?: return null to 0L
        val rowUpdated = Formatters.parseEpochMs(row.opt("settings_updated_at"), 0L)
        val jsonUpdated = Formatters.parseEpochMs(settingsObj.opt("settingsUpdatedAt"), rowUpdated)
        val updatedAt = maxOf(rowUpdated, jsonUpdated)

        val dailyLimit = if (settingsObj.has("dailyCardLimit") && !settingsObj.isNull("dailyCardLimit")) {
            settingsObj.optDouble("dailyCardLimit").takeIf { !it.isNaN() && it > 0.0 }
        } else null

        val denomsArr = settingsObj.optJSONArray("visibleDenominations")
        val visibleDenoms = if (denomsArr != null) {
            val parsed = (0 until denomsArr.length()).mapNotNull { i ->
                denomsArr.optInt(i, -1).takeIf { it in CupDenominations.ALL }
            }
            if (parsed.isEmpty()) CupDenominations.DEFAULT_VISIBLE else CupDenominations.ALL.filter { it in parsed }
        } else CupDenominations.DEFAULT_VISIBLE

        val lastBackupAt = settingsObj.optNullableStr("lastBackupAt")
        val firstUsedAt = settingsObj.optNullableStr("firstUsedAt").orEmpty()
        val lastSyncedAt = if (settingsObj.has("lastSyncedAt") && !settingsObj.isNull("lastSyncedAt")) {
            Formatters.parseEpochMs(settingsObj.opt("lastSyncedAt"), 0L).takeIf { it > 0L }
        } else null
        val notifications = PreferencesDataStore.parseNotificationPrefs(settingsObj.optJSONObject("notifications"))

        return AppSettings(
            dailyCardLimit = dailyLimit,
            visibleDenominations = visibleDenoms,
            lastBackupAt = lastBackupAt,
            firstUsedAt = firstUsedAt,
            settingsUpdatedAt = updatedAt,
            syncWorkspaceId = wsId,
            lastSyncedAt = lastSyncedAt,
            hasPendingSync = false,
            notifications = notifications
        ) to updatedAt
    }

    private fun parsePersons(arr: JSONArray): List<Person> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.optNullableStr("id") ?: continue
            val name = obj.optNullableStr("name") ?: continue
            val created = Formatters.parseEpochMs(obj.opt("created_at"), 0L)
            val updated = Formatters.parseEpochMs(obj.opt("updated_at"), created)
            add(
                Person(
                    id = id,
                    name = name,
                    receiptName = obj.optNullableStr("receipt_name"),
                    commissionRate = obj.optDouble("commission_rate", 5.0).let { if (it.isNaN()) 5.0 else it },
                    defaultCommissionMode = CommissionMode.fromWire(obj.optNullableStr("default_commission_mode")),
                    archived = obj.optBoolean("archived", false),
                    createdAt = created,
                    updatedAt = updated
                )
            )
        }
    }

    private fun parseOperations(arr: JSONArray): List<Operation> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.optNullableStr("id") ?: continue
            val personId = obj.optNullableStr("person_id") ?: continue
            val type = OperationType.fromWire(obj.optNullableStr("type"))
            val date = Formatters.normalizeIsoDateTime(obj.optNullableStr("date"))
            val amount = obj.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else Formatters.round2(it) }
            val rateSnap = if (obj.has("commission_rate_snapshot") && !obj.isNull("commission_rate_snapshot")) {
                obj.optDouble("commission_rate_snapshot").takeIf { !it.isNaN() }
            } else null
            val mode = obj.optNullableStr("commission_mode")?.let { CommissionMode.fromWire(it) }
            val commAmt = if (obj.has("commission_amount") && !obj.isNull("commission_amount")) {
                obj.optDouble("commission_amount").takeIf { !it.isNaN() }?.let { Formatters.round2(it) }
            } else null
            val note = obj.optNullableStr("note")
            val billObj = obj.optJSONObject("bill_breakdown")
            val bills = if (billObj != null) {
                val map = linkedMapOf<Int, Int>()
                val keys = billObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val d = k.toIntOrNull()
                    val c = billObj.optInt(k, 0)
                    if (d != null && c > 0) map[d] = c
                }
                map.takeIf { it.isNotEmpty() }
            } else null
            val created = Formatters.parseEpochMs(obj.opt("created_at"), 0L)
            val updated = Formatters.parseEpochMs(obj.opt("updated_at"), created)

            add(
                Operation(
                    id = id,
                    personId = personId,
                    type = type,
                    date = date,
                    amount = amount,
                    commissionRateSnapshot = rateSnap,
                    commissionMode = mode,
                    commissionAmount = commAmt,
                    note = note,
                    billBreakdown = bills,
                    createdAt = created,
                    updatedAt = updated
                )
            )
        }
    }

    private fun parseMovements(arr: JSONArray): List<CommissionMovement> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.optNullableStr("id") ?: continue
            val date = Formatters.normalizeIsoDateTime(obj.optNullableStr("date"))
            val amount = obj.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else Formatters.round2(it) }
            val category = CommissionMovementCategory.fromWire(obj.optNullableStr("category"))
            val note = obj.optNullableStr("note")
            val created = Formatters.parseEpochMs(obj.opt("created_at"), 0L)
            val updated = Formatters.parseEpochMs(obj.opt("updated_at"), created)
            add(
                CommissionMovement(
                    id = id,
                    date = date,
                    amount = amount,
                    category = category,
                    note = note,
                    createdAt = created,
                    updatedAt = updated
                )
            )
        }
    }

    private fun parseTombstones(arr: JSONArray, wsId: String): List<SyncTombstone> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = SyncEntityType.fromWire(obj.optNullableStr("entity_type")) ?: continue
            val entityId = obj.optNullableStr("entity_id") ?: continue
            val deletedAt = Formatters.parseEpochMs(obj.opt("deleted_at"), 0L)
            if (deletedAt > 0L) {
                add(
                    SyncTombstone(
                        workspaceId = wsId,
                        entityType = type,
                        entityId = entityId,
                        deletedAt = deletedAt
                    )
                )
            }
        }
    }

    private fun personsToJsonArray(wsId: String, items: List<Person>): JSONArray {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("workspace_id", wsId)
                    put("id", p.id)
                    put("name", p.name)
                    put("receipt_name", p.receiptName ?: JSONObject.NULL)
                    put("commission_rate", p.commissionRate)
                    put("default_commission_mode", p.defaultCommissionMode.wireValue)
                    put("archived", p.archived)
                    put("created_at", p.createdAt)
                    put("updated_at", p.updatedAt)
                }
            )
        }
        return arr
    }

    private fun operationsToJsonArray(wsId: String, items: List<Operation>): JSONArray {
        val arr = JSONArray()
        items.forEach { op ->
            arr.put(
                JSONObject().apply {
                    put("workspace_id", wsId)
                    put("id", op.id)
                    put("person_id", op.personId)
                    put("type", op.type.wireValue)
                    put("date", op.date)
                    put("amount", op.amount)
                    put("commission_rate_snapshot", op.commissionRateSnapshot ?: JSONObject.NULL)
                    put("commission_mode", op.commissionMode?.wireValue ?: JSONObject.NULL)
                    put("commission_amount", op.commissionAmount ?: JSONObject.NULL)
                    put("note", op.note ?: JSONObject.NULL)
                    if (!op.billBreakdown.isNullOrEmpty()) {
                        val bObj = JSONObject()
                        op.billBreakdown.forEach { (d, c) -> if (c > 0) bObj.put(d.toString(), c) }
                        put("bill_breakdown", bObj)
                    } else {
                        put("bill_breakdown", JSONObject.NULL)
                    }
                    put("created_at", op.createdAt)
                    put("updated_at", op.updatedAt)
                }
            )
        }
        return arr
    }

    private fun movementsToJsonArray(wsId: String, items: List<CommissionMovement>): JSONArray {
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("workspace_id", wsId)
                    put("id", m.id)
                    put("date", m.date)
                    put("amount", m.amount)
                    put("category", m.category.wireValue)
                    put("note", m.note ?: JSONObject.NULL)
                    put("created_at", m.createdAt)
                    put("updated_at", m.updatedAt)
                }
            )
        }
        return arr
    }

    private fun tombstonesToJsonArray(wsId: String, items: List<SyncTombstone>): JSONArray {
        val arr = JSONArray()
        items.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("workspace_id", wsId)
                    put("entity_type", t.entityType.wireValue)
                    put("entity_id", t.entityId)
                    put("deleted_at", t.deletedAt)
                }
            )
        }
        return arr
    }

    private fun settingsToJson(s: AppSettings): JSONObject =
        JSONObject().apply {
            if (s.dailyCardLimit != null) put("dailyCardLimit", s.dailyCardLimit) else put("dailyCardLimit", JSONObject.NULL)
            put("visibleDenominations", JSONArray(s.visibleDenominations))
            if (!s.lastBackupAt.isNullOrBlank()) put("lastBackupAt", s.lastBackupAt) else put("lastBackupAt", JSONObject.NULL)
            put("firstUsedAt", s.firstUsedAt)
            put("settingsUpdatedAt", s.settingsUpdatedAt)
            put("syncWorkspaceId", s.syncWorkspaceId)
            if (s.lastSyncedAt != null) put("lastSyncedAt", s.lastSyncedAt) else put("lastSyncedAt", JSONObject.NULL)
            put("notifications", PreferencesDataStore.notificationPrefsToJson(s.notifications))
        }

    private fun JSONObject.optNullableStr(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().takeIf { it.isNotEmpty() }
    }
}
