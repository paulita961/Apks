package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppSettings
import com.example.domain.BillCounterState
import com.example.domain.CalculatorHistoryItem
import com.example.domain.CupDenominations
import com.example.domain.Formatters
import com.example.domain.NotificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "control_extracciones_prefs"
)

class PreferencesDataStore(private val context: Context) {

    private object Keys {
        val DAILY_CARD_LIMIT = doublePreferencesKey("daily_card_limit")
        val VISIBLE_DENOMINATIONS = stringPreferencesKey("visible_denominations")
        val LAST_BACKUP_AT = stringPreferencesKey("last_backup_at")
        val FIRST_USED_AT = stringPreferencesKey("first_used_at")
        val SETTINGS_UPDATED_AT = longPreferencesKey("settings_updated_at")
        val SYNC_WORKSPACE_ID = stringPreferencesKey("sync_workspace_id")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val HAS_PENDING_SYNC = booleanPreferencesKey("has_pending_sync")
        val NOTIFICATIONS_JSON = stringPreferencesKey("notifications_json")
        val CALCULATOR_HISTORY = stringPreferencesKey("calculator_history")
        val BILL_COUNTER_COUNTS = stringPreferencesKey("bill_counter_counts")
        val BILL_COUNTER_EXPECTED = stringPreferencesKey("bill_counter_expected")
    }

    suspend fun ensureFirstUsedInitialized() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.FIRST_USED_AT].isNullOrBlank()) {
                prefs[Keys.FIRST_USED_AT] = Formatters.currentIsoTimestamp()
            }
            if (prefs[Keys.SYNC_WORKSPACE_ID].isNullOrBlank()) {
                prefs[Keys.SYNC_WORKSPACE_ID] = "principal"
            }
        }
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            dailyCardLimit = prefs[Keys.DAILY_CARD_LIMIT],
            visibleDenominations = parseDenominations(prefs[Keys.VISIBLE_DENOMINATIONS]),
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT],
            firstUsedAt = prefs[Keys.FIRST_USED_AT].orEmpty(),
            settingsUpdatedAt = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L,
            syncWorkspaceId = Formatters.sanitizeWorkspaceId(prefs[Keys.SYNC_WORKSPACE_ID] ?: "principal"),
            lastSyncedAt = prefs[Keys.LAST_SYNCED_AT],
            hasPendingSync = prefs[Keys.HAS_PENDING_SYNC] ?: false,
            notifications = parseNotificationPrefs(prefs[Keys.NOTIFICATIONS_JSON])
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setDailyCardLimit(limit: Double?) {
        val now = Formatters.nowEpochMs()
        context.dataStore.edit { prefs ->
            if (limit == null || limit <= 0.0) {
                prefs.remove(Keys.DAILY_CARD_LIMIT)
            } else {
                prefs[Keys.DAILY_CARD_LIMIT] = limit
            }
            prefs[Keys.SETTINGS_UPDATED_AT] = now
            prefs[Keys.HAS_PENDING_SYNC] = true
        }
    }

    suspend fun setVisibleDenominations(denominations: List<Int>) {
        val clean = CupDenominations.ALL.filter { it in denominations }
        val finalList = if (clean.isEmpty()) CupDenominations.DEFAULT_VISIBLE else clean
        val now = Formatters.nowEpochMs()
        context.dataStore.edit { prefs ->
            prefs[Keys.VISIBLE_DENOMINATIONS] = finalList.joinToString(",")
            prefs[Keys.SETTINGS_UPDATED_AT] = now
            prefs[Keys.HAS_PENDING_SYNC] = true
        }
    }

    suspend fun setNotificationPreferences(notifications: NotificationPreferences) {
        val now = Formatters.nowEpochMs()
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_JSON] = serializeNotificationPrefs(notifications)
            prefs[Keys.SETTINGS_UPDATED_AT] = now
            prefs[Keys.HAS_PENDING_SYNC] = true
        }
    }

    suspend fun setSyncWorkspaceId(workspaceId: String) {
        val sanitized = Formatters.sanitizeWorkspaceId(workspaceId)
        context.dataStore.edit { prefs ->
            prefs[Keys.SYNC_WORKSPACE_ID] = sanitized
        }
    }

    suspend fun setHasPendingSync(pending: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_PENDING_SYNC] = pending
        }
    }

    suspend fun setLastBackupAt(timestamp: String?) {
        val now = Formatters.nowEpochMs()
        context.dataStore.edit { prefs ->
            if (timestamp.isNullOrBlank()) {
                prefs.remove(Keys.LAST_BACKUP_AT)
            } else {
                prefs[Keys.LAST_BACKUP_AT] = timestamp
            }
            prefs[Keys.SETTINGS_UPDATED_AT] = now
            prefs[Keys.HAS_PENDING_SYNC] = true
        }
    }

    suspend fun applySettingsFromSyncOrBackup(
        settings: AppSettings,
        markPendingSync: Boolean,
        syncedAtEpoch: Long? = settings.lastSyncedAt
    ) {
        context.dataStore.edit { prefs ->
            if (settings.dailyCardLimit == null || settings.dailyCardLimit <= 0.0) {
                prefs.remove(Keys.DAILY_CARD_LIMIT)
            } else {
                prefs[Keys.DAILY_CARD_LIMIT] = settings.dailyCardLimit
            }
            val clean = CupDenominations.ALL.filter { it in settings.visibleDenominations }
            prefs[Keys.VISIBLE_DENOMINATIONS] =
                (if (clean.isEmpty()) CupDenominations.DEFAULT_VISIBLE else clean).joinToString(",")
            if (settings.lastBackupAt.isNullOrBlank()) {
                prefs.remove(Keys.LAST_BACKUP_AT)
            } else {
                prefs[Keys.LAST_BACKUP_AT] = settings.lastBackupAt
            }
            if (settings.firstUsedAt.isNotBlank()) {
                prefs[Keys.FIRST_USED_AT] = settings.firstUsedAt
            }
            prefs[Keys.SETTINGS_UPDATED_AT] = settings.settingsUpdatedAt
            prefs[Keys.SYNC_WORKSPACE_ID] = Formatters.sanitizeWorkspaceId(settings.syncWorkspaceId)
            if (syncedAtEpoch != null) {
                prefs[Keys.LAST_SYNCED_AT] = syncedAtEpoch
            }
            prefs[Keys.HAS_PENDING_SYNC] = markPendingSync
            prefs[Keys.NOTIFICATIONS_JSON] = serializeNotificationPrefs(settings.notifications)
        }
    }

    val calculatorHistoryFlow: Flow<List<CalculatorHistoryItem>> = context.dataStore.data.map { prefs ->
        parseCalculatorHistory(prefs[Keys.CALCULATOR_HISTORY])
    }

    suspend fun addCalculatorHistoryItem(item: CalculatorHistoryItem) {
        context.dataStore.edit { prefs ->
            val current = parseCalculatorHistory(prefs[Keys.CALCULATOR_HISTORY]).toMutableList()
            current.add(0, item)
            val trimmed = current.take(10)
            prefs[Keys.CALCULATOR_HISTORY] = serializeCalculatorHistory(trimmed)
        }
    }

    suspend fun clearCalculatorHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CALCULATOR_HISTORY)
        }
    }

    val billCounterStateFlow: Flow<BillCounterState> = context.dataStore.data.map { prefs ->
        BillCounterState(
            counts = parseCounts(prefs[Keys.BILL_COUNTER_COUNTS]),
            expectedAmount = prefs[Keys.BILL_COUNTER_EXPECTED].orEmpty()
        )
    }

    suspend fun updateBillCounterState(counts: Map<Int, Int>, expectedAmount: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BILL_COUNTER_COUNTS] = serializeCounts(counts)
            prefs[Keys.BILL_COUNTER_EXPECTED] = expectedAmount
        }
    }

    private fun parseDenominations(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return CupDenominations.DEFAULT_VISIBLE
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in CupDenominations.ALL }
        return if (parsed.isEmpty()) CupDenominations.DEFAULT_VISIBLE
        else CupDenominations.ALL.filter { it in parsed }
    }

    private fun parseCalculatorHistory(raw: String?): List<CalculatorHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CalculatorHistoryItem(
                            id = obj.optString("id"),
                            expression = obj.optString("expression"),
                            result = obj.optString("result"),
                            timestamp = obj.optString("timestamp")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeCalculatorHistory(items: List<CalculatorHistoryItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("expression", item.expression)
                    put("result", item.result)
                    put("timestamp", item.timestamp)
                }
            )
        }
        return arr.toString()
    }

    private fun parseCounts(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = linkedMapOf<Int, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val denom = k.toIntOrNull()
                val count = obj.optInt(k, 0)
                if (denom != null && count > 0) result[denom] = count
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun serializeCounts(counts: Map<Int, Int>): String {
        val obj = JSONObject()
        counts.forEach { (d, c) ->
            if (c > 0) obj.put(d.toString(), c)
        }
        return obj.toString()
    }

    companion object {
        fun parseNotificationPrefs(raw: String?): NotificationPreferences {
            if (raw.isNullOrBlank()) return NotificationPreferences()
            return runCatching {
                val obj = JSONObject(raw)
                parseNotificationPrefs(obj)
            }.getOrDefault(NotificationPreferences())
        }

        fun parseNotificationPrefs(obj: JSONObject?): NotificationPreferences {
            if (obj == null) return NotificationPreferences()
            val validHours = setOf(12, 24, 48, 72)
            val validBackupDays = setOf(0, 7, 15, 30)
            val hours = obj.optInt("pendingDeliveryHours", 24).let { if (it in validHours) it else 24 }
            val days = obj.optInt("backupReminderDays", 7).let { if (it in validBackupDays) it else 7 }
            return NotificationPreferences(
                enabled = obj.optBoolean("enabled", true),
                overDeliveryEnabled = obj.optBoolean("overDeliveryEnabled", true),
                pendingDeliveryEnabled = obj.optBoolean("pendingDeliveryEnabled", true),
                pendingDeliveryHours = hours,
                pendingCommissionEnabled = obj.optBoolean("pendingCommissionEnabled", true),
                dailyLimitEnabled = obj.optBoolean("dailyLimitEnabled", true),
                pendingSyncEnabled = obj.optBoolean("pendingSyncEnabled", true),
                backupReminderDays = days
            )
        }

        fun serializeNotificationPrefs(prefs: NotificationPreferences): String =
            notificationPrefsToJson(prefs).toString()

        fun notificationPrefsToJson(prefs: NotificationPreferences): JSONObject =
            JSONObject().apply {
                put("enabled", prefs.enabled)
                put("overDeliveryEnabled", prefs.overDeliveryEnabled)
                put("pendingDeliveryEnabled", prefs.pendingDeliveryEnabled)
                put("pendingDeliveryHours", prefs.pendingDeliveryHours)
                put("pendingCommissionEnabled", prefs.pendingCommissionEnabled)
                put("dailyLimitEnabled", prefs.dailyLimitEnabled)
                put("pendingSyncEnabled", prefs.pendingSyncEnabled)
                put("backupReminderDays", prefs.backupReminderDays)
            }
    }
}
