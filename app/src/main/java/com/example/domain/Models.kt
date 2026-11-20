package com.example.domain

enum class CommissionMode(val wireValue: String, val label: String, val shortLabel: String) {
    DESCONTADA("descontada", "Descontada del efectivo", "Descontada"),
    APARTE("aparte", "Pagada aparte por el titular", "Aparte");

    companion object {
        fun fromWire(value: String?): CommissionMode =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: DESCONTADA
    }
}

enum class OperationType(val wireValue: String, val label: String) {
    RECIBIDO("recibido", "Recibido"),
    EXTRACCION("extraccion", "Extracción"),
    ENTREGA("entrega", "Entrega"),
    COBRO_COMISION("cobro_comision", "Cobro comisión");

    companion object {
        fun fromWire(value: String?): OperationType =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: RECIBIDO
    }
}

enum class CommissionMovementCategory(val wireValue: String, val label: String) {
    GASTO("gasto", "Gasto o uso personal"),
    AHORRO("ahorro", "Pasar a ahorro"),
    USO_AHORRO("uso_ahorro", "Usar desde ahorro");

    companion object {
        fun fromWire(value: String?): CommissionMovementCategory =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: GASTO
    }
}

enum class SyncEntityType(val wireValue: String) {
    PERSON("person"),
    OPERATION("operation"),
    COMMISSION_MOVEMENT("commission_movement");

    companion object {
        fun fromWire(value: String?): SyncEntityType? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}

enum class DbConnectionStatus(val label: String) {
    CHECKING("Comprobando"),
    CONNECTED("BD activa"),
    SYNCING("Sincronizando"),
    OFFLINE("Offline"),
    NOT_CONFIGURED("Sin config"),
    ERROR("Error BD")
}

object CupDenominations {
    val ALL: List<Int> = listOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 3, 1)
    val DEFAULT_VISIBLE: List<Int> = listOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100)
}

data class Person(
    val id: String,
    val name: String,
    val receiptName: String? = null,
    val commissionRate: Double = 5.0,
    val defaultCommissionMode: CommissionMode = CommissionMode.DESCONTADA,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class Operation(
    val id: String,
    val personId: String,
    val type: OperationType,
    val date: String, // YYYY-MM-DDTHH:mm
    val amount: Double,
    val commissionRateSnapshot: Double? = null,
    val commissionMode: CommissionMode? = null,
    val commissionAmount: Double? = null,
    val note: String? = null,
    val billBreakdown: Map<Int, Int>? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class CommissionMovement(
    val id: String,
    val date: String, // YYYY-MM-DDTHH:mm
    val amount: Double,
    val category: CommissionMovementCategory,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class SyncTombstone(
    val workspaceId: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val deletedAt: Long
)

data class NotificationPreferences(
    val enabled: Boolean = true,
    val overDeliveryEnabled: Boolean = true,
    val pendingDeliveryEnabled: Boolean = true,
    val pendingDeliveryHours: Int = 24, // 12, 24, 48, 72
    val pendingCommissionEnabled: Boolean = true,
    val dailyLimitEnabled: Boolean = true,
    val pendingSyncEnabled: Boolean = true,
    val backupReminderDays: Int = 7 // 7, 15, 30, 0 = disabled
)

data class AppSettings(
    val dailyCardLimit: Double? = null,
    val visibleDenominations: List<Int> = CupDenominations.DEFAULT_VISIBLE,
    val lastBackupAt: String? = null,
    val firstUsedAt: String = "",
    val settingsUpdatedAt: Long = 0L,
    val syncWorkspaceId: String = "principal",
    val lastSyncedAt: Long? = null,
    val hasPendingSync: Boolean = false,
    val notifications: NotificationPreferences = NotificationPreferences()
)

data class CalculatorHistoryItem(
    val id: String,
    val expression: String,
    val result: String,
    val timestamp: String
)

data class BillCounterState(
    val counts: Map<Int, Int> = emptyMap(),
    val expectedAmount: String = ""
)

enum class PdfReceiptFormat {
    SIMPLE,
    WITH_COMMISSIONS
}

enum class CommissionFilterMode(val label: String) {
    ALL("Todas"),
    DESCONTADA("Descontada"),
    APARTE("Aparte")
}

enum class ChartMetricMode(val label: String) {
    MODE_SPLIT("Descontada vs Aparte"),
    STATUS_SPLIT("Generada vs Cobrada")
}

enum class NoticePriority(val rank: Int) {
    URGENT(0),
    WARNING(1),
    INFO(2)
}

enum class NoticeSeverity {
    RED,
    ORANGE,
    AMBER,
    BLUE,
    NEUTRAL
}

enum class NoticeActionType {
    VIEW_PERSON,
    GO_TO_COMMISSIONS,
    GO_TO_SETTINGS,
    SYNC_NOW,
    GO_TO_BACKUP
}

data class AppNotice(
    val id: String,
    val title: String,
    val message: String,
    val priority: NoticePriority,
    val severity: NoticeSeverity,
    val actionLabel: String,
    val actionType: NoticeActionType,
    val targetPersonId: String? = null
)
