package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppNotice
import com.example.domain.DbConnectionStatus
import com.example.domain.NoticeSeverity
import com.example.ui.theme.LocalSemanticColors

@Composable
fun DbStatusDot(
    status: DbConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isPulsing = status == DbConnectionStatus.CHECKING || status == DbConnectionStatus.SYNCING
    val infiniteTransition = rememberInfiniteTransition(label = "db_status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val dotColor = when (status) {
        DbConnectionStatus.CHECKING -> Color(0xFF94A3B8)
        DbConnectionStatus.CONNECTED -> Color(0xFF16A34A)
        DbConnectionStatus.SYNCING -> Color(0xFF2563EB)
        DbConnectionStatus.OFFLINE -> Color(0xFF64748B)
        DbConnectionStatus.NOT_CONFIGURED -> Color(0xFFD97706)
        DbConnectionStatus.ERROR -> Color(0xFFDC2626)
    }

    Box(
        modifier = modifier
            .size(9.dp)
            .alpha(if (isPulsing) pulseAlpha else 1f)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
fun DbStatusBadge(
    status: DbConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val (bgColor, textColor) = when (status) {
        DbConnectionStatus.CONNECTED -> semantic.positiveBg to semantic.positiveText
        DbConnectionStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DbConnectionStatus.NOT_CONFIGURED -> semantic.amberWarningBg to semantic.amberWarningText
        DbConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        DbConnectionStatus.CHECKING,
        DbConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        modifier = modifier
            .heightIn(min = 34.dp)
            .testTag("topbar_db_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DbStatusDot(status = status)
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeCenterBottomSheet(
    notices: List<AppNotice>,
    hiddenCount: Int,
    onDismissNotice: (String) -> Unit,
    onRestoreHidden: () -> Unit,
    onAction: (AppNotice) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSheet: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Centro de avisos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        onDismissSheet()
                        onOpenSettings()
                    },
                    modifier = Modifier.testTag("notice_center_configure_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Configurar")
                }
            }

            if (hiddenCount > 0) {
                TextButton(
                    onClick = onRestoreHidden,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("notice_center_restore_hidden_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Mostrar avisos ocultados ($hiddenCount)")
                }
            }

            if (notices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No tienes avisos activos",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Todo está al día según tus preferencias de alertas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notices, key = { it.id }) { notice ->
                        NoticeItemCard(
                            notice = notice,
                            onDismiss = { onDismissNotice(notice.id) },
                            onAction = { onAction(notice) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NoticeItemCard(
    notice: AppNotice,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val (bgColor, accentColor) = when (notice.severity) {
        NoticeSeverity.RED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        NoticeSeverity.ORANGE -> semantic.orangePendingBg to semantic.orangePendingText
        NoticeSeverity.AMBER -> semantic.amberWarningBg to semantic.amberWarningText
        NoticeSeverity.BLUE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.primary
        NoticeSeverity.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notice_card_${notice.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = notice.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_notice_${notice.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Ocultar aviso",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("action_notice_${notice.id}")
                ) {
                    Text(
                        text = notice.actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
