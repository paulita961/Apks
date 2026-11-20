package com.example.domain

import java.time.LocalDate
import kotlin.math.abs

data class LedgerSummary(
    val totalRecibido: Double = 0.0,
    val totalExtraido: Double = 0.0,
    val totalEntregado: Double = 0.0,
    val comisionGenerada: Double = 0.0,
    val comisionDescontada: Double = 0.0,
    val cobrosComision: Double = 0.0,
    val comisionCobrada: Double = 0.0,
    val comisionPendienteCobro: Double = 0.0,
    val pendientePorExtraer: Double = 0.0,
    val porEntregar: Double = 0.0
)

data class PersonSummary(
    val person: Person,
    val summary: LedgerSummary,
    val operationCount: Int
)

data class CommissionWallet(
    val comisionCobrada: Double = 0.0,
    val ahorroActual: Double = 0.0,
    val totalGastado: Double = 0.0,
    val comisionDisponible: Double = 0.0,
    val saldoNeto: Double = 0.0
)

data class MonthlyCommissionStats(
    val yearMonth: String, // YYYY-MM
    val year: Int,
    val month: Int,
    val extractionCount: Int,
    val extractedAmount: Double,
    val generada: Double,
    val descontada: Double,
    val aparteGenerada: Double,
    val cobrosAparte: Double,
    val cobradaTotal: Double
)

object FinanceCalculator {

    val operationDescendingComparator: Comparator<Operation> = Comparator { a, b ->
        val dateA = Formatters.extractDatePart(a.date)
        val dateB = Formatters.extractDatePart(b.date)
        val dateCmp = dateB.compareTo(dateA)
        if (dateCmp != 0) return@Comparator dateCmp

        val timeA = Formatters.extractTimePart(a.date)
        val timeB = Formatters.extractTimePart(b.date)
        val timeCmp = timeB.compareTo(timeA)
        if (timeCmp != 0) return@Comparator timeCmp

        b.createdAt.compareTo(a.createdAt)
    }

    val movementDescendingComparator: Comparator<CommissionMovement> = Comparator { a, b ->
        val dateA = Formatters.extractDatePart(a.date)
        val dateB = Formatters.extractDatePart(b.date)
        val dateCmp = dateB.compareTo(dateA)
        if (dateCmp != 0) return@Comparator dateCmp

        val timeA = Formatters.extractTimePart(a.date)
        val timeB = Formatters.extractTimePart(b.date)
        val timeCmp = timeB.compareTo(timeA)
        if (timeCmp != 0) return@Comparator timeCmp

        b.createdAt.compareTo(a.createdAt)
    }

    fun effectiveCommission(operation: Operation): Double {
        if (operation.type != OperationType.EXTRACCION) return 0.0
        val explicit = operation.commissionAmount
        if (explicit != null) return Formatters.round2(explicit)
        val rate = operation.commissionRateSnapshot ?: 0.0
        return Formatters.round2(operation.amount * rate / 100.0)
    }

    fun computeSummary(operations: List<Operation>): LedgerSummary {
        var totalRecibido = 0.0
        var totalExtraido = 0.0
        var totalEntregado = 0.0
        var comisionGenerada = 0.0
        var comisionDescontada = 0.0
        var cobrosComision = 0.0

        for (op in operations) {
            when (op.type) {
                OperationType.RECIBIDO -> totalRecibido += op.amount
                OperationType.EXTRACCION -> {
                    totalExtraido += op.amount
                    val comm = effectiveCommission(op)
                    comisionGenerada += comm
                    val mode = op.commissionMode ?: CommissionMode.DESCONTADA
                    if (mode == CommissionMode.DESCONTADA) {
                        comisionDescontada += comm
                    }
                }
                OperationType.ENTREGA -> totalEntregado += op.amount
                OperationType.COBRO_COMISION -> cobrosComision += op.amount
            }
        }

        totalRecibido = Formatters.round2(totalRecibido)
        totalExtraido = Formatters.round2(totalExtraido)
        totalEntregado = Formatters.round2(totalEntregado)
        comisionGenerada = Formatters.round2(comisionGenerada)
        comisionDescontada = Formatters.round2(comisionDescontada)
        cobrosComision = Formatters.round2(cobrosComision)

        val comisionCobrada = Formatters.round2(comisionDescontada + cobrosComision)
        val comisionPendienteCobro = Formatters.round2(comisionGenerada - comisionCobrada)
        val pendientePorExtraer = Formatters.round2(totalRecibido - totalExtraido)
        val porEntregar = Formatters.round2(totalExtraido - comisionDescontada - totalEntregado)

        return LedgerSummary(
            totalRecibido = totalRecibido,
            totalExtraido = totalExtraido,
            totalEntregado = totalEntregado,
            comisionGenerada = comisionGenerada,
            comisionDescontada = comisionDescontada,
            cobrosComision = cobrosComision,
            comisionCobrada = comisionCobrada,
            comisionPendienteCobro = comisionPendienteCobro,
            pendientePorExtraer = pendientePorExtraer,
            porEntregar = porEntregar
        )
    }

    fun computePersonSummaries(
        persons: List<Person>,
        operations: List<Operation>
    ): List<PersonSummary> {
        val opsByPerson = operations.groupBy { it.personId }
        return persons.map { person ->
            val personOps = opsByPerson[person.id].orEmpty()
            PersonSummary(
                person = person,
                summary = computeSummary(personOps),
                operationCount = personOps.size
            )
        }
    }

    fun computeCommissionWallet(
        comisionCobrada: Double,
        movements: List<CommissionMovement>
    ): CommissionWallet {
        var gasto = 0.0
        var ahorro = 0.0
        var usoAhorro = 0.0

        for (m in movements) {
            when (m.category) {
                CommissionMovementCategory.GASTO -> gasto += m.amount
                CommissionMovementCategory.AHORRO -> ahorro += m.amount
                CommissionMovementCategory.USO_AHORRO -> usoAhorro += m.amount
            }
        }

        val ahorroActual = Formatters.round2(ahorro - usoAhorro)
        val totalGastado = Formatters.round2(gasto + usoAhorro)
        val comisionDisponible = Formatters.round2(comisionCobrada - gasto - ahorro)
        val saldoNeto = Formatters.round2(comisionCobrada - totalGastado)

        return CommissionWallet(
            comisionCobrada = Formatters.round2(comisionCobrada),
            ahorroActual = ahorroActual,
            totalGastado = totalGastado,
            comisionDisponible = comisionDisponible,
            saldoNeto = saldoNeto
        )
    }

    fun extractedOnDate(
        operations: List<Operation>,
        isoDate: String,
        excludeOperationId: String? = null
    ): Double {
        val targetDate = Formatters.extractDatePart(isoDate)
        val total = operations
            .filter {
                it.type == OperationType.EXTRACCION &&
                    it.id != excludeOperationId &&
                    Formatters.extractDatePart(it.date) == targetDate
            }
            .sumOf { it.amount }
        return Formatters.round2(total)
    }

    fun computeMonthlyStatsForYear(
        operations: List<Operation>,
        year: Int = LocalDate.now().year
    ): List<MonthlyCommissionStats> {
        val grouped = operations
            .filter { Formatters.extractDatePart(it.date).startsWith("$year-") }
            .groupBy { Formatters.extractDatePart(it.date).substring(5, 7).toIntOrNull() ?: 1 }

        return (1..12).map { month ->
            val monthOps = grouped[month].orEmpty()
            val extractions = monthOps.filter { it.type == OperationType.EXTRACCION }
            val cobros = monthOps.filter { it.type == OperationType.COBRO_COMISION }

            var descontada = 0.0
            var aparteGen = 0.0
            var extractedAmount = 0.0
            for (op in extractions) {
                extractedAmount += op.amount
                val comm = effectiveCommission(op)
                if ((op.commissionMode ?: CommissionMode.DESCONTADA) == CommissionMode.DESCONTADA) {
                    descontada += comm
                } else {
                    aparteGen += comm
                }
            }
            val cobrosAparte = cobros.sumOf { it.amount }
            val generada = Formatters.round2(descontada + aparteGen)
            val cobradaTotal = Formatters.round2(descontada + cobrosAparte)

            MonthlyCommissionStats(
                yearMonth = "$year-${month.toString().padStart(2, '0')}",
                year = year,
                month = month,
                extractionCount = extractions.size,
                extractedAmount = Formatters.round2(extractedAmount),
                generada = generada,
                descontada = Formatters.round2(descontada),
                aparteGenerada = Formatters.round2(aparteGen),
                cobrosAparte = Formatters.round2(cobrosAparte),
                cobradaTotal = cobradaTotal
            )
        }
    }

    fun computeAllMonthlyBreakdownDesc(operations: List<Operation>): List<MonthlyCommissionStats> {
        val byYearMonth = operations
            .filter { it.type == OperationType.EXTRACCION || it.type == OperationType.COBRO_COMISION }
            .groupBy { Formatters.extractDatePart(it.date).take(7) }

        return byYearMonth.entries
            .sortedByDescending { it.key }
            .mapNotNull { (ym, ops) ->
                val parts = ym.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val month = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val extractions = ops.filter { it.type == OperationType.EXTRACCION }
                val cobros = ops.filter { it.type == OperationType.COBRO_COMISION }

                var descontada = 0.0
                var aparteGen = 0.0
                var extractedAmount = 0.0
                for (op in extractions) {
                    extractedAmount += op.amount
                    val comm = effectiveCommission(op)
                    if ((op.commissionMode ?: CommissionMode.DESCONTADA) == CommissionMode.DESCONTADA) {
                        descontada += comm
                    } else {
                        aparteGen += comm
                    }
                }
                val cobrosAparte = cobros.sumOf { it.amount }
                MonthlyCommissionStats(
                    yearMonth = ym,
                    year = year,
                    month = month,
                    extractionCount = extractions.size,
                    extractedAmount = Formatters.round2(extractedAmount),
                    generada = Formatters.round2(descontada + aparteGen),
                    descontada = Formatters.round2(descontada),
                    aparteGenerada = Formatters.round2(aparteGen),
                    cobrosAparte = Formatters.round2(cobrosAparte),
                    cobradaTotal = Formatters.round2(descontada + cobrosAparte)
                )
            }
    }

    fun evaluateNotices(
        personSummaries: List<PersonSummary>,
        operations: List<Operation>,
        settings: AppSettings,
        isSupabaseConfigured: Boolean
    ): List<AppNotice> {
        val prefs = settings.notifications
        if (!prefs.enabled) return emptyList()

        val notices = mutableListOf<AppNotice>()
        val opsByPerson = operations.groupBy { it.personId }

        // 1. Entregas en exceso (rojo, urgente)
        if (prefs.overDeliveryEnabled) {
            personSummaries
                .filter { it.summary.porEntregar < -0.005 }
                .forEach { ps ->
                    notices.add(
                        AppNotice(
                            id = "over_delivery_${ps.person.id}",
                            title = "Entrega en exceso: ${ps.person.name}",
                            message = "Se entregó ${Formatters.formatCup(abs(ps.summary.porEntregar))} de más respecto al saldo disponible.",
                            priority = NoticePriority.URGENT,
                            severity = NoticeSeverity.RED,
                            actionLabel = "Ver titular",
                            actionType = NoticeActionType.VIEW_PERSON,
                            targetPersonId = ps.person.id
                        )
                    )
                }
        }

        // 2. Entregas pendientes (naranja, advertencia)
        if (prefs.pendingDeliveryEnabled) {
            personSummaries
                .filter { it.summary.porEntregar > 0.005 }
                .forEach { ps ->
                    val latestExtraction = opsByPerson[ps.person.id]
                        .orEmpty()
                        .filter { it.type == OperationType.EXTRACCION }
                        .maxByOrNull { it.date }
                    if (latestExtraction != null) {
                        val elapsedHours = Formatters.hoursSinceIsoDateTime(latestExtraction.date)
                        if (elapsedHours >= prefs.pendingDeliveryHours) {
                            val wholeHours = elapsedHours.toInt()
                            notices.add(
                                AppNotice(
                                    id = "pending_delivery_${ps.person.id}",
                                    title = "Entrega pendiente: ${ps.person.name}",
                                    message = "Faltan ${Formatters.formatCup(ps.summary.porEntregar)} por entregar (hace ${wholeHours} h desde la última extracción).",
                                    priority = NoticePriority.WARNING,
                                    severity = NoticeSeverity.ORANGE,
                                    actionLabel = "Ver titular",
                                    actionType = NoticeActionType.VIEW_PERSON,
                                    targetPersonId = ps.person.id
                                )
                            )
                        }
                    }
                }
        }

        // 3. Comisiones aparte por cobrar (ámbar, advertencia)
        if (prefs.pendingCommissionEnabled) {
            val withPendingComm = personSummaries
                .filter { it.summary.comisionPendienteCobro > 0.005 }
                .sortedByDescending { it.summary.comisionPendienteCobro }
            if (withPendingComm.size in 1..3) {
                withPendingComm.forEach { ps ->
                    notices.add(
                        AppNotice(
                            id = "pending_comm_${ps.person.id}",
                            title = "Comisión por cobrar: ${ps.person.name}",
                            message = "Tiene ${Formatters.formatCup(ps.summary.comisionPendienteCobro)} de comisión pendiente de cobro.",
                            priority = NoticePriority.WARNING,
                            severity = NoticeSeverity.AMBER,
                            actionLabel = "Ver titular",
                            actionType = NoticeActionType.VIEW_PERSON,
                            targetPersonId = ps.person.id
                        )
                    )
                }
            } else if (withPendingComm.size > 3) {
                val totalPending = Formatters.round2(withPendingComm.sumOf { it.summary.comisionPendienteCobro })
                notices.add(
                    AppNotice(
                        id = "pending_comm_grouped",
                        title = "${withPendingComm.size} titulares con comisión pendiente",
                        message = "Total pendiente por cobrar: ${Formatters.formatCup(totalPending)}.",
                        priority = NoticePriority.WARNING,
                        severity = NoticeSeverity.AMBER,
                        actionLabel = "Ir a Comisiones",
                        actionType = NoticeActionType.GO_TO_COMMISSIONS
                    )
                )
            }
        }

        // 4. Límite diario de tarjeta (>=80% ámbar, >=100% rojo)
        val dailyLimit = settings.dailyCardLimit
        if (prefs.dailyLimitEnabled && dailyLimit != null && dailyLimit > 0.0) {
            val extractedToday = extractedOnDate(operations, Formatters.currentIsoDate())
            val ratio = extractedToday / dailyLimit
            if (ratio >= 1.0) {
                notices.add(
                    AppNotice(
                        id = "daily_limit_reached",
                        title = "Límite diario de tarjeta alcanzado",
                        message = "Hoy se ha extraído ${Formatters.formatCup(extractedToday)} de un límite de ${Formatters.formatCup(dailyLimit)}.",
                        priority = NoticePriority.URGENT,
                        severity = NoticeSeverity.RED,
                        actionLabel = "Ajustes",
                        actionType = NoticeActionType.GO_TO_SETTINGS
                    )
                )
            } else if (ratio >= 0.8) {
                val pct = (ratio * 100).toInt()
                notices.add(
                    AppNotice(
                        id = "daily_limit_near",
                        title = "Cerca del límite diario ($pct%)",
                        message = "Extraído hoy: ${Formatters.formatCup(extractedToday)} de ${Formatters.formatCup(dailyLimit)}.",
                        priority = NoticePriority.WARNING,
                        severity = NoticeSeverity.AMBER,
                        actionLabel = "Ajustes",
                        actionType = NoticeActionType.GO_TO_SETTINGS
                    )
                )
            }
        }

        // 5. Cambios pendientes por sincronizar (azul, informativo)
        if (prefs.pendingSyncEnabled && settings.hasPendingSync && isSupabaseConfigured) {
            notices.add(
                AppNotice(
                    id = "pending_sync",
                    title = "Cambios pendientes por sincronizar",
                    message = "Tienes cambios locales guardados en el dispositivo pendientes de subir a la nube.",
                    priority = NoticePriority.INFO,
                    severity = NoticeSeverity.BLUE,
                    actionLabel = "Sincronizar",
                    actionType = NoticeActionType.SYNC_NOW
                )
            )
        }

        // 6. Recordatorio de respaldo JSON (neutro, informativo)
        val reminderDays = prefs.backupReminderDays
        val refTimestamp = settings.lastBackupAt ?: settings.firstUsedAt
        if (reminderDays > 0 && operations.isNotEmpty() && refTimestamp.isNotBlank()) {
            val daysElapsed = Formatters.daysSince(refTimestamp)
            if (daysElapsed > reminderDays) {
                notices.add(
                    AppNotice(
                        id = "backup_reminder",
                        title = "Recordatorio de respaldo JSON",
                        message = "Han pasado $daysElapsed días sin realizar una copia de seguridad local en JSON.",
                        priority = NoticePriority.INFO,
                        severity = NoticeSeverity.NEUTRAL,
                        actionLabel = "Ir a respaldo",
                        actionType = NoticeActionType.GO_TO_BACKUP
                    )
                )
            }
        }

        return notices.sortedBy { it.priority.rank }
    }
}
