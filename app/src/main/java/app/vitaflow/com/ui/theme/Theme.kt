package app.vitaflow.com.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class GlassColors(
    val bgTop: Color,
    val bgBottom: Color,
    val blobA: Color,
    val blobB: Color,
    /** card background */
    val glassFill: Color,
    val glassHighlight: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    /** the second colour of the green gradient on buttons */
    val accent2: Color,
    /** text on top of the accent colour */
    val onAccent: Color,
    val danger: Color,
    val success: Color,
    val buttonAlphaTop: Float,
    val buttonAlphaBottom: Float
)

/** Plain near-black, no colour tint. Green is the only accent, like the logo. */
val DarkGlass = GlassColors(
    bgTop = Color(0xFF0A0B0D),
    bgBottom = Color(0xFF0A0B0D),
    blobA = Color.Transparent,
    blobB = Color.Transparent,
    glassFill = Color(0xFF14161A),
    glassHighlight = Color.Transparent,
    borderTop = Color(0x26FFFFFF),
    borderBottom = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF2F4F5),
    textSecondary = Color(0xFF8E959C),
    accent = Color(0xFF4ADE80),
    accent2 = Color(0xFF16B58C),
    onAccent = Color(0xFF04130B),
    danger = Color(0xFFFF6B6B),
    success = Color(0xFF4ADE80),
    buttonAlphaTop = 1f,
    buttonAlphaBottom = 1f
)

val LightGlass = GlassColors(
    bgTop = Color(0xFFF4F6F5),
    bgBottom = Color(0xFFF4F6F5),
    blobA = Color.Transparent,
    blobB = Color.Transparent,
    glassFill = Color(0xFFFFFFFF),
    glassHighlight = Color.Transparent,
    borderTop = Color(0x1F000000),
    borderBottom = Color(0x14000000),
    textPrimary = Color(0xFF111418),
    textSecondary = Color(0xFF626A72),
    accent = Color(0xFF14A85E),
    accent2 = Color(0xFF0E8F73),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFD7263D),
    success = Color(0xFF14A85E),
    buttonAlphaTop = 1f,
    buttonAlphaBottom = 1f
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** Kept for older code; the new design has no blur, so it has no effect. */
val LocalReduceEffects = staticCompositionLocalOf { false }

private val VitaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun VitaFlowTheme(dark: Boolean = isSystemInDarkTheme(), reduceEffects: Boolean = false, content: @Composable () -> Unit) {
    val g = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) {
        darkColorScheme(
            primary = g.accent, onPrimary = g.onAccent, background = g.bgTop, onBackground = g.textPrimary,
            surface = g.glassFill, onSurface = g.textPrimary, onSurfaceVariant = g.textSecondary, error = g.danger
        )
    } else {
        lightColorScheme(
            primary = g.accent, onPrimary = g.onAccent, background = g.bgTop, onBackground = g.textPrimary,
            surface = g.glassFill, onSurface = g.textPrimary, onSurfaceVariant = g.textSecondary, error = g.danger
        )
    }
    CompositionLocalProvider(LocalGlass provides g, LocalReduceEffects provides reduceEffects) {
        MaterialTheme(colorScheme = scheme, typography = VitaTypography, content = content)
    }
}
