package com.edgetts.mp3.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF07080D)
    val Surface = Color(0xFF0F1118)
    val SurfaceHigh = Color(0xFF171A26)
    val SurfaceHigher = Color(0xFF1F2333)
    val Stroke = Color(0x1AFFFFFF)
    val Violet = Color(0xFF8B5CF6)
    val Indigo = Color(0xFF6366F1)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFF472B6)
    val Text = Color(0xFFE9EBF3)
    val Muted = Color(0xFF8A90A6)
    val Danger = Color(0xFFF87171)
    val Success = Color(0xFF34D399)

    val accent = Brush.linearGradient(listOf(Violet, Indigo, Cyan))
    val accentHorizontal = Brush.horizontalGradient(listOf(Violet, Cyan))
    val glow = Brush.radialGradient(listOf(Violet.copy(alpha = 0.28f), Color.Transparent))
}

private val scheme = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    secondary = Palette.Cyan,
    onSecondary = Color.Black,
    tertiary = Palette.Pink,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.SurfaceHigh,
    surfaceContainerHigh = Palette.SurfaceHigher,
    surfaceContainerHighest = Palette.SurfaceHigher,
    outline = Palette.Stroke,
    outlineVariant = Palette.Stroke,
    error = Palette.Danger,
)

private val type = Typography().let { t ->
    val f = FontFamily.SansSerif
    t.copy(
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.2.sp),
        bodyLarge = TextStyle(fontFamily = f, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = f, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = f, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

@Composable
fun EdgeTtsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
