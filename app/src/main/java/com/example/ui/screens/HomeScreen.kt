package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Formatters
import com.example.domain.PersonSummary
import com.example.ui.MainUiState
import com.example.ui.components.CupAmountText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.tabular
import kotlin.math.abs

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onGoToBackup: () -> Unit,
    onOpenCommissionMovement: () -> Unit,
    onAddPerson: () -> Unit,
    onSelectPerson: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    val normalizedQuery = remember(searchQuery) { Formatters.normalizeSearch(searchQuery) }

    val filteredActive = remember(uiState.activePersonSummaries, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            uiState.activePersonSummaries
        } else {
            uiState.activePersonSummaries.filter { ps ->
                Formatters.normalizeSearch(ps.person.name).contains(normalizedQuery) ||
                    (ps.person.receiptName != null && Formatters.normalizeSearch(ps.person.receiptName).contains(normalizedQuery))
            }
        }
    }

    val filteredArchived = remember(uiState.archivedPersonSummaries, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            uiState.archivedPersonSummaries
        } else {
            uiState.archivedPersonSummaries.filter { ps ->
                Formatters.normalizeSearch(ps.person.name).contains(normalizedQuery) ||
                    (ps.person.receiptName != null && Formatters.normalizeSearch(ps.person.receiptName).contains(normalizedQuery))
            }
        }
    }

    val global = uiState.globalSummary
    val wallet = uiState.commissionWallet

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding + 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.showBackupBanner) {
            item {
                val daysCfg = uiState.settings.notifications.backupReminderDays
                Card(
                    colors = CardDefaults.cardColors(containerColor = semantic.amberWarningBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Backup,
                                contentDescription = "Aviso de respaldo",
                                tint = semantic.amberWarningText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Han pasado más de $daysCfg días sin respaldar tus datos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.amberWarningText,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = onGoToBackup,
                            modifier = Modifier.testTag("banner_go_to_backup")
                        ) {
                            Text(
                                text = "Ir a respaldo",
                                color = semantic.amberWarningText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Tarjeta Principal Hero: Saldo neto disponible (v1.7.2)
        item {
            NetBalanceHeroCard(
                saldoNeto = wallet.saldoNeto,
                recibidas = wallet.comisionCobrada,
                usadas = wallet.totalGastado
            )
        }

        // Cuadrícula 2x2 alineada de 4 tarjetas secundarias (v1.7.3)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 560.dp
                val gridGap = if (isWide) 14.dp else 10.dp
                val overDelivered = global.porEntregar < -0.005

                Column(verticalArrangement = Arrangement.spacedBy(gridGap)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(gridGap)
                    ) {
                        // 1. Pendiente por extraer
                        SummaryGridCard(
                            title = "Pendiente por extraer",
                            amount = global.pendientePorExtraer,
                            subtitle = "Recibido: ${Formatters.formatCup(global.totalRecibido)}\nExtraído: ${Formatters.formatCup(global.totalExtraido)}",
                            containerColor = MaterialTheme.colorScheme.surface,
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        // 2. Por entregar
                        SummaryGridCard(
                            title = if (overDelivered) "Entregaste de más" else "Por entregar",
                            amount = if (overDelivered) abs(global.porEntregar) else global.porEntregar,
                            subtitle = "Entregado: ${Formatters.formatCup(global.totalEntregado)}",
                            containerColor = if (overDelivered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                            valueColor = if (overDelivered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            borderColor = if (overDelivered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(gridGap)
                    ) {
                        // 3. Comisión disponible en mano (con botón Usar / ahorrar alineado)
                        SummaryGridCard(
                            title = "Comisión disponible en mano",
                            amount = wallet.comisionDisponible,
                            subtitle = "Cobrada hist.: ${Formatters.formatCup(global.comisionCobrada)}",
                            containerColor = MaterialTheme.colorScheme.surface,
                            valueColor = if (wallet.comisionDisponible < 0) MaterialTheme.colorScheme.error else semantic.positiveText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Spacer(Modifier.height(6.dp))
                            FilledTonalButton(
                                onClick = onOpenCommissionMovement,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .testTag("home_use_save_commission_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Usar / ahorrar",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 4. En ahorro
                        SummaryGridCard(
                            title = "En ahorro",
                            amount = wallet.ahorroActual,
                            subtitle = "Gastado hist.: ${Formatters.formatCup(wallet.totalGastado)}",
                            containerColor = MaterialTheme.colorScheme.surface,
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    // Franja de Comisión pendiente de cobro (cuando hay saldo pendiente aparte o como resumen)
                    val hasPendingComm = global.comisionPendienteCobro > 0.005
                    if (hasPendingComm) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = semantic.orangePendingBg,
                            border = BorderStroke(1.dp, semantic.orangePendingText.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_pending_commission_strip")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = semantic.orangePendingText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Comisión pendiente de cobro",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = semantic.orangePendingText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Generada hist.: ${Formatters.formatCup(global.comisionGenerada)}",
                                            style = MaterialTheme.typography.bodySmall.tabular(),
                                            color = semantic.orangePendingText.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                CupAmountText(
                                    amount = global.comisionPendienteCobro,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = semantic.orangePendingText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Titulares Header + Search
        item {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Titulares (${uiState.activePersonSummaries.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedButton(
                        onClick = onAddPerson,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_new_person_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo titular")
                    }
                }

                if (uiState.persons.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por alias o nombre para PDF...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_person_input")
                    )
                }
            }
        }

        // Empty state when no persons exist yet
        if (uiState.persons.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PeopleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Aún no tienes titulares registrados",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Crea un titular para comenzar a controlar dinero recibido, extracciones, entregas y comisiones en CUP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onAddPerson,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("empty_add_person_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agregar primer titular")
                        }
                    }
                }
            }
        } else if (filteredActive.isEmpty() && filteredArchived.isEmpty()) {
            item {
                Text(
                    text = "No se encontraron titulares con \"$searchQuery\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(filteredActive, key = { it.person.id }) { ps ->
                PersonSummaryCard(
                    personSummary = ps,
                    onClick = { onSelectPerson(ps.person.id) }
                )
            }

            if (filteredArchived.isNotEmpty()) {
                item {
                    Surface(
                        onClick = { showArchived = !showArchived },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_archived_persons")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Ver titulares archivados (${filteredArchived.size})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Icon(
                                imageVector = if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                }

                if (showArchived) {
                    items(filteredArchived, key = { "arch_${it.person.id}" }) { ps ->
                        AnimatedVisibility(visible = true) {
                            PersonSummaryCard(
                                personSummary = ps,
                                onClick = { onSelectPerson(ps.person.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetBalanceHeroCard(
    saldoNeto: Double,
    recibidas: Double,
    usadas: Double
) {
    val isNegative = saldoNeto < -0.005
    val containerColor = if (isNegative) Color(0xFF7F1D1D) else Color(0xFF0B222A)
    val accentBorder = if (isNegative) Color(0xFFFCA5A5).copy(alpha = 0.45f) else Color(0xFF2DD4BF).copy(alpha = 0.25f)
    val labelColor = if (isNegative) Color(0xFFFECACA) else Color(0xFF94A3B8)
    val pillColor = if (isNegative) Color(0xFF991B1B) else Color(0xFF13333E)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_net_balance_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNegative) "Saldo neto disponible (en negativo)" else "Saldo neto disponible",
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = pillColor
                ) {
                    Text(
                        text = "Comisiones",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNegative) Color(0xFFFECACA) else Color(0xFF5EEAD4),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = Formatters.formatCup(saldoNeto),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp
                ).tabular(),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_net_balance_amount")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Recibidas",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor
                        )
                        Text(
                            text = Formatters.formatCup(recibidas),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ).tabular(),
                            color = if (isNegative) Color.White else Color(0xFF5EEAD4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Usadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor
                        )
                        Text(
                            text = Formatters.formatCup(usadas),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ).tabular(),
                            color = Color(0xFFF8FAFC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryGridCard(
    title: String,
    amount: Double,
    subtitle: String,
    containerColor: Color,
    valueColor: Color,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CupAmountText(
                    amount = amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = valueColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (footer != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    footer()
                }
            }
        }
    }
}

@Composable
private fun PersonSummaryCard(
    personSummary: PersonSummary,
    onClick: () -> Unit
) {
    val person = personSummary.person
    val s = personSummary.summary
    val semantic = LocalSemanticColors.current
    val overDelivered = s.porEntregar < -0.005
    val hasPendingComm = s.comisionPendienteCobro > 0.005

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (person.archived) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (overDelivered) MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("person_card_${person.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (person.archived) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Archivado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${Formatters.formatRate(person.commissionRate)} · ${person.defaultCommissionMode.shortLabel}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasPendingComm) semantic.orangePendingBg else semantic.positiveBg
                ) {
                    Text(
                        text = if (hasPendingComm) {
                            "Por cobrar: ${Formatters.formatCup(s.comisionPendienteCobro)}"
                        } else {
                            "Cobrada: ${Formatters.formatCup(s.comisionCobrada)}"
                        },
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = if (hasPendingComm) semantic.orangePendingText else semantic.positiveText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pendiente por extraer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CupAmountText(
                        amount = s.pendientePorExtraer,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (overDelivered) "Entregado de más" else "Por entregar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overDelivered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CupAmountText(
                        amount = if (overDelivered) abs(s.porEntregar) else s.porEntregar,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (overDelivered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
