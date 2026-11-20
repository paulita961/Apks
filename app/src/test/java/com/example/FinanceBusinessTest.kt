package com.example

import com.example.data.repository.BackupSnapshot
import com.example.data.sync.RemoteWorkspaceData
import com.example.data.sync.SyncMergeEngine
import com.example.data.sync.SyncStateSnapshot
import com.example.domain.AppSettings
import com.example.domain.CommissionMode
import com.example.domain.CommissionMovement
import com.example.domain.CommissionMovementCategory
import com.example.domain.CupDenominations
import com.example.domain.FinanceCalculator
import com.example.domain.Formatters
import com.example.domain.NotificationPreferences
import com.example.domain.NoticeActionType
import com.example.domain.NoticePriority
import com.example.domain.NoticeSeverity
import com.example.domain.Operation
import com.example.domain.OperationType
import com.example.domain.PdfReceiptFormat
import com.example.domain.Person
import com.example.domain.SyncEntityType
import com.example.domain.SyncTombstone
import com.example.export.BackupManager
import com.example.pdf.ReceiptPdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinanceBusinessTest {

    @Test
    fun `exact business formulas for person and commission wallet including saldoNeto`() {
        val baseTs = 1_760_000_000_000L
        val ops = listOf(
            Operation(
                id = "1",
                personId = "p1",
                type = OperationType.RECIBIDO,
                date = "2026-11-10T09:00",
                amount = 20000.0,
                createdAt = baseTs,
                updatedAt = baseTs
            ),
            Operation(
                id = "2",
                personId = "p1",
                type = OperationType.EXTRACCION,
                date = "2026-11-10T10:00",
                amount = 10000.0,
                commissionRateSnapshot = 5.0,
                commissionMode = CommissionMode.DESCONTADA,
                commissionAmount = null,
                createdAt = baseTs + 1000L,
                updatedAt = baseTs + 1000L
            ),
            Operation(
                id = "3",
                personId = "p1",
                type = OperationType.EXTRACCION,
                date = "2026-11-10T11:00",
                amount = 5000.0,
                commissionRateSnapshot = 5.0,
                commissionMode = CommissionMode.APARTE,
                commissionAmount = 300.0,
                createdAt = baseTs + 2000L,
                updatedAt = baseTs + 2000L
            ),
            Operation(
                id = "4",
                personId = "p1",
                type = OperationType.ENTREGA,
                date = "2026-11-10T12:00",
                amount = 9500.0,
                createdAt = baseTs + 3000L,
                updatedAt = baseTs + 3000L
            ),
            Operation(
                id = "5",
                personId = "p1",
                type = OperationType.COBRO_COMISION,
                date = "2026-11-10T13:00",
                amount = 200.0,
                createdAt = baseTs + 4000L,
                updatedAt = baseTs + 4000L
            )
        )

        val summary = FinanceCalculator.computeSummary(ops)
        assertEquals(20000.0, summary.totalRecibido, 0.001)
        assertEquals(15000.0, summary.totalExtraido, 0.001)
        assertEquals(9500.0, summary.totalEntregado, 0.001)
        assertEquals(800.0, summary.comisionGenerada, 0.001)
        assertEquals(500.0, summary.comisionDescontada, 0.001)
        assertEquals(200.0, summary.cobrosComision, 0.001)
        assertEquals(700.0, summary.comisionCobrada, 0.001)
        assertEquals(100.0, summary.comisionPendienteCobro, 0.001)
        assertEquals(5000.0, summary.pendientePorExtraer, 0.001)
        assertEquals(5000.0, summary.porEntregar, 0.001)

        val movements = listOf(
            CommissionMovement("m1", "2026-11-10T14:00", 150.0, CommissionMovementCategory.GASTO, null, baseTs + 5000L),
            CommissionMovement("m2", "2026-11-10T15:00", 400.0, CommissionMovementCategory.AHORRO, null, baseTs + 6000L),
            CommissionMovement("m3", "2026-11-10T16:00", 100.0, CommissionMovementCategory.USO_AHORRO, null, baseTs + 7000L)
        )
        val wallet = FinanceCalculator.computeCommissionWallet(summary.comisionCobrada, movements)
        assertEquals(300.0, wallet.ahorroActual, 0.001)
        assertEquals(250.0, wallet.totalGastado, 0.001)
        assertEquals(150.0, wallet.comisionDisponible, 0.001)
        // saldoNeto = comisionCobrada (700) - totalGastado (250) = 450
        assertEquals(450.0, wallet.saldoNeto, 0.001)
    }

    @Test
    fun `formatCup uses space thousands separator and 2 decimals when needed`() {
        assertEquals("20 000 CUP", Formatters.formatCup(20000.0))
        assertEquals("1 250.50 CUP", Formatters.formatCup(1250.5))
        assertEquals("-500 CUP", Formatters.formatCup(-500.0))
    }

    @Test
    fun `sanitizeWorkspaceId normalizes accents spaces and symbols`() {
        assertEquals("mi-espacio-1", Formatters.sanitizeWorkspaceId("  Mi Espacio #1! "))
        assertEquals("principal", Formatters.sanitizeWorkspaceId("   ---   "))
    }

    @Test
    fun `backup json backward compatibility without commissionMovements and time`() {
        val legacyJson = """
            {
              "version": 1,
              "persons": [
                {
                  "id": "p1",
                  "name": "Alias Interno",
                  "receiptName": null,
                  "commissionRate": 5
                }
              ],
              "operations": [
                {
                  "id": "o1",
                  "personId": "p1",
                  "type": "recibido",
                  "date": "2026-10-01",
                  "amount": 15000
                }
              ]
            }
        """.trimIndent()

        val result = BackupManager.parseAndValidateBackupJson(legacyJson)
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(1, payload.persons.size)
        assertEquals("Alias Interno", payload.persons[0].name)
        assertEquals(null, payload.persons[0].receiptName)
        assertEquals(0, payload.commissionMovements.size)
        assertEquals("2026-10-01T00:00", payload.operations[0].date)
        assertEquals(CupDenominations.DEFAULT_VISIBLE, payload.settings.visibleDenominations)
    }

    @Test
    fun `csv export contains utf8 bom and expected columns including Mi comision`() {
        val ts = 1_760_000_000_000L
        val person = Person("p1", "Juan", null, 5.0, CommissionMode.DESCONTADA, false, ts, ts)
        val op = Operation("o1", "p1", OperationType.RECIBIDO, "2026-11-10T09:30", 5000.0, createdAt = ts, updatedAt = ts)
        val mov = CommissionMovement("m1", "2026-11-11T11:00", 200.0, CommissionMovementCategory.GASTO, "Recarga", ts, ts)
        val csv = BackupManager.exportCsv(
            BackupSnapshot(listOf(person), listOf(op), listOf(mov), AppSettings())
        )
        assertTrue(csv.startsWith("\uFEFFFecha,Hora,Titular,Tipo,Monto (CUP)"))
        assertTrue(csv.contains("Mi comisión"))
        assertTrue(csv.contains("Juan"))
    }

    @Test
    fun `pdf simple format excludes commission payment operations`() {
        val ts = 1_760_000_000_000L
        val ops = listOf(
            Operation("1", "p1", OperationType.RECIBIDO, "2026-11-01T10:00", 10000.0, createdAt = ts, updatedAt = ts),
            Operation("2", "p1", OperationType.EXTRACCION, "2026-11-01T11:00", 5000.0, 5.0, CommissionMode.APARTE, 250.0, createdAt = ts, updatedAt = ts),
            Operation("3", "p1", OperationType.COBRO_COMISION, "2026-11-01T12:00", 250.0, createdAt = ts, updatedAt = ts)
        )
        val simpleOps = ReceiptPdfGenerator.filterOperationsForFormat(ops, PdfReceiptFormat.SIMPLE)
        assertEquals(2, simpleOps.size)
        assertTrue(simpleOps.none { it.type == OperationType.COBRO_COMISION })

        val fullOps = ReceiptPdfGenerator.filterOperationsForFormat(ops, PdfReceiptFormat.WITH_COMMISSIONS)
        assertEquals(3, fullOps.size)
    }

    @Test
    fun `calculator expression evaluator handles precedence and percentages`() {
        assertEquals(14.0, com.example.ui.components.evaluateExpression("2+3×4")!!, 0.001)
        assertEquals(105.0, com.example.ui.components.evaluateExpression("100+5%")!!, 0.001)
        assertEquals(1000.0, com.example.ui.components.evaluateExpression("20000×5%")!!, 0.001)
    }

    @Test
    fun `sync merge engine applies LWW, tombstones 180d, offline upload and settings rules`() {
        val now = 2_000_000_000_000L
        val ws = "principal"

        val localPersonOffline = Person("p_local", "Offline Local", createdAt = now - 5000L, updatedAt = now - 5000L)
        val localPersonOlder = Person("p_shared", "Nombre Viejo", createdAt = 1000L, updatedAt = now - 10_000L)
        val remotePersonNewer = Person("p_shared", "Nombre Nuevo Web", createdAt = 1000L, updatedAt = now - 2_000L)
        val remoteDeletedPerson = Person("p_deleted", "Borrado", createdAt = 1000L, updatedAt = now - 20_000L)

        val localTombstone = SyncTombstone(ws, SyncEntityType.PERSON, "p_deleted", deletedAt = now - 1_000L)
        val expiredRemoteTombstone = SyncTombstone(
            ws,
            SyncEntityType.OPERATION,
            "o_old",
            deletedAt = now - SyncMergeEngine.TOMBSTONE_RETENTION_MS - 10_000L
        )

        val localSettings = AppSettings(
            dailyCardLimit = 40000.0,
            firstUsedAt = "2026-11-01T10:00:00",
            lastBackupAt = "2026-11-05T10:00:00",
            settingsUpdatedAt = now - 10_000L,
            syncWorkspaceId = ws
        )
        val remoteSettings = AppSettings(
            dailyCardLimit = 60000.0,
            firstUsedAt = "2026-10-15T08:00:00",
            lastBackupAt = "2026-11-12T10:00:00",
            settingsUpdatedAt = now - 1_000L,
            syncWorkspaceId = ws,
            notifications = NotificationPreferences(pendingDeliveryHours = 48)
        )

        val merged = SyncMergeEngine.merge(
            workspaceId = ws,
            local = SyncStateSnapshot(
                workspaceId = ws,
                persons = listOf(localPersonOffline, localPersonOlder),
                operations = emptyList(),
                movements = emptyList(),
                tombstones = listOf(localTombstone),
                settings = localSettings
            ),
            remote = RemoteWorkspaceData(
                persons = listOf(remotePersonNewer, remoteDeletedPerson),
                operations = emptyList(),
                movements = emptyList(),
                tombstones = listOf(expiredRemoteTombstone),
                settings = remoteSettings,
                settingsUpdatedAt = remoteSettings.settingsUpdatedAt
            ),
            nowMs = now
        )

        // p_local preserved and queued to push; p_shared takes newer remote; p_deleted removed by tombstone
        assertEquals(listOf("p_local", "p_shared"), merged.mergedPersons.map { it.id }.sorted())
        assertEquals("Nombre Nuevo Web", merged.mergedPersons.first { it.id == "p_shared" }.name)
        assertEquals(listOf("p_local"), merged.personsToPush.map { it.id })
        assertEquals(1, merged.mergedTombstones.size)
        assertEquals("p_deleted", merged.mergedTombstones[0].entityId)

        // Settings rules: min firstUsedAt, max lastBackupAt, newer remote limit & notifications
        assertEquals("2026-10-15T08:00:00", merged.mergedSettings.firstUsedAt)
        assertEquals("2026-11-12T10:00:00", merged.mergedSettings.lastBackupAt)
        assertEquals(60000.0, merged.mergedSettings.dailyCardLimit!!, 0.001)
        assertEquals(48, merged.mergedSettings.notifications.pendingDeliveryHours)
        assertFalse(merged.mergedSettings.hasPendingSync)
    }

    @Test
    fun `evaluateNotices orders by priority and evaluates all 6 notice rules`() {
        val now = Formatters.nowEpochMs()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val twoDaysAgo = LocalDateTime.now().minusHours(50).format(fmt)
        val today = LocalDateTime.now().format(fmt)
        val tenDaysAgo = LocalDate.now().minusDays(10).toString() + "T10:00:00"

        val pOver = Person("p1", "Exceso", createdAt = now, updatedAt = now)
        val pPending = Person("p2", "Pendiente", createdAt = now, updatedAt = now)

        val ops = listOf(
            // p1 over-delivered: extracted 1000, delivered 1500
            Operation("o1", "p1", OperationType.EXTRACCION, today, 1000.0, 0.0, CommissionMode.DESCONTADA, 0.0, createdAt = now),
            Operation("o2", "p1", OperationType.ENTREGA, today, 1500.0, createdAt = now),
            // p2 pending delivery > 24h AND pending aparte commission
            Operation("o3", "p2", OperationType.EXTRACCION, twoDaysAgo, 9000.0, 5.0, CommissionMode.APARTE, 450.0, createdAt = now)
        )

        val summaries = FinanceCalculator.computePersonSummaries(listOf(pOver, pPending), ops)
        val settings = AppSettings(
            dailyCardLimit = 1000.0, // today extracted 1000 -> 100% red
            firstUsedAt = tenDaysAgo,
            hasPendingSync = true,
            notifications = NotificationPreferences(
                enabled = true,
                pendingDeliveryHours = 24,
                backupReminderDays = 7
            )
        )

        val notices = FinanceCalculator.evaluateNotices(
            personSummaries = summaries,
            operations = ops,
            settings = settings,
            isSupabaseConfigured = true
        )

        assertTrue(notices.any { it.id == "over_delivery_p1" && it.severity == NoticeSeverity.RED && it.priority == NoticePriority.URGENT })
        assertTrue(notices.any { it.id == "daily_limit_reached" && it.severity == NoticeSeverity.RED })
        assertTrue(notices.any { it.id == "pending_delivery_p2" && it.severity == NoticeSeverity.ORANGE })
        assertTrue(notices.any { it.id == "pending_comm_p2" && it.severity == NoticeSeverity.AMBER })
        assertTrue(notices.any { it.id == "pending_sync" && it.actionType == NoticeActionType.SYNC_NOW })
        assertTrue(notices.any { it.id == "backup_reminder" && it.actionType == NoticeActionType.GO_TO_BACKUP })

        // Verify priority sorting: URGENT first, then WARNING, then INFO
        val ranks = notices.map { it.priority.rank }
        assertEquals(ranks.sorted(), ranks)

        // Verify grouped commission notice when > 3 persons have pending commissions
        val manyPersons = (1..4).map { idx -> Person("g$idx", "Titular $idx", createdAt = now, updatedAt = now) }
        val manyOps = manyPersons.mapIndexed { idx, person ->
            Operation("go$idx", person.id, OperationType.EXTRACCION, today, 2000.0, 5.0, CommissionMode.APARTE, 100.0, createdAt = now)
        }
        val groupedNotices = FinanceCalculator.evaluateNotices(
            personSummaries = FinanceCalculator.computePersonSummaries(manyPersons, manyOps),
            operations = manyOps,
            settings = AppSettings(notifications = NotificationPreferences(backupReminderDays = 0)),
            isSupabaseConfigured = false
        )
        assertTrue(groupedNotices.any { it.id == "pending_comm_grouped" && it.actionType == NoticeActionType.GO_TO_COMMISSIONS })
        assertTrue(groupedNotices.none { it.id == "backup_reminder" })
    }
}
