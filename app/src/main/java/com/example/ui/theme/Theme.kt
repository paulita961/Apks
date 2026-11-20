package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SemanticColors(
    val positiveText: Color,
    val positiveBg: Color,
    val orangePendingText: Color,
    val orangePendingBg: Color,
    val amberWarningText: Color,
    val amberWarningBg: Color,
    val actionReceivedBg: Color,
    val actionReceivedFg: Color,
    val actionExtractionBg: Color,
    val actionExtractionFg: Color,
    val actionDeliveryBg: Color,
    val actionDeliveryFg: Color,
    val actionCommissionBg: Color,
    val actionCommissionFg: Color
)

private val LightSemanticColors = SemanticColors(
    positiveText = Color(0xFF047857),
    positiveBg = Color(0xFFDCFCE7),
    orangePendingText = Color(0xFFC2410C),
    orangePendingBg = Color(0xFFFFEDD5),
    amberWarningText = Color(0xFFB45309),
    amberWarningBg = Color(0xFFFEF3C7),
    actionReceivedBg = Color(0xFFE6F7EE),
    actionReceivedFg = Color(0xFF065F46),
    actionExtractionBg = Color(0xFFE5F2FF),
    actionExtractionFg = Color(0xFF1E40AF),
    actionDeliveryBg = Color(0xFFFFEFE0),
    actionDeliveryFg = Color(0xFF9A3412),
    actionCommissionBg = Color(0xFFE2F7F5),
    actionCommissionFg = Color(0xFF115E59)
)

private val DarkSemanticColors = SemanticColors(
    positiveText = Color(0xFF34D399),
    positiveBg = Color(0xFF064E3B),
    orangePendingText = Color(0xFFFB923C),
    orangePendingBg = Color(0xFF431407),
    amberWarningText = Color(0xFFFBBF24),
    amberWarningBg = Color(0xFF451A03),
    actionReceivedBg = Color(0xFF0B3B2B),
    actionReceivedFg = Color(0xFF6EE7B7),
    actionExtractionBg = Color(0xFF0F2B4B),
    actionExtractionFg = Color(0xFF93C5FD),
    actionDeliveryBg = Color(0xFF43200B),
    actionDeliveryFg = Color(0xFFFDBA74),
    actionCommissionBg = Color(0xFF0B3836),
    actionCommissionFg = Color(0xFF5EEAD4)
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = EmeraldOnContainerLight,
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = SlateBackgroundLight,
    onBackground = SlateTextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = SlateTextPrimaryLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateTextSecondaryLight,
    outline = SlateOutlineLight,
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = EmeraldOnContainerDark,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    background = SlateBackgroundDark,
    onBackground = SlateTextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = SlateTextPrimaryDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateTextSecondaryDark,
    outline = SlateOutlineDark,
    error = ErrorRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ControlExtraccionesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
