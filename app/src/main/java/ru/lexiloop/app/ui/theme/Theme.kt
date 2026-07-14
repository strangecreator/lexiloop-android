package ru.lexiloop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.lexiloop.app.R

/**
 * The LexiLoop web design system, translated 1:1 from `styles.css` custom
 * properties. `primary*` values depend on the user's accent-color setting.
 */
data class LexiPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val border2: Color,
    val text: Color,
    val muted: Color,
    val muted2: Color,
    val primary: Color,
    val primary2: Color,
    val primaryDeep: Color,
    val primaryBg: Color,
    val green: Color,
    val greenBg: Color,
    val red: Color,
    val redBg: Color,
    val orange: Color,
    val blue: Color,
)

private data class Accent(
    val primary: Color,
    val primary2: Color,
    val deep: Color,
    val bg: Color,
)

// :root[data-accent=…] values (identical in dark and light themes).
private val ACCENTS: Map<String, Accent> = mapOf(
    "violet" to Accent(Color(0xFF8B7CFF), Color(0xFFB19EFF), Color(0xFF6555DA), Color(0x218B7CFF)),
    "indigo" to Accent(Color(0xFF6366F1), Color(0xFF9FA2FF), Color(0xFF4447C8), Color(0x216366F1)),
    "blue" to Accent(Color(0xFF3185F5), Color(0xFF76B4FF), Color(0xFF1763C7), Color(0x213185F5)),
    "teal" to Accent(Color(0xFF12A9A0), Color(0xFF56D7CF), Color(0xFF087C77), Color(0x2112A9A0)),
    "emerald" to Accent(Color(0xFF18A66B), Color(0xFF58D99C), Color(0xFF08794A), Color(0x2118A66B)),
    "rose" to Accent(Color(0xFFDF5B85), Color(0xFFF197B3), Color(0xFFB33B63), Color(0x21DF5B85)),
    "orange" to Accent(Color(0xFFDE7A28), Color(0xFFFFAD65), Color(0xFFAE5514), Color(0x21DE7A28)),
)

fun lexiPalette(dark: Boolean, accentName: String): LexiPalette {
    val accent = ACCENTS[accentName] ?: ACCENTS.getValue("emerald")
    return if (dark) {
        LexiPalette(
            isDark = true,
            bg = Color(0xFF0A0B0F),
            surface = Color(0xFF11131A),
            surface2 = Color(0xFF171A23),
            surface3 = Color(0xFF1D202B),
            border = Color(0xFF272B37),
            border2 = Color(0xFF343947),
            text = Color(0xFFF5F6F8),
            muted = Color(0xFF969CA9),
            muted2 = Color(0xFF6F7582),
            primary = accent.primary,
            primary2 = accent.primary2,
            primaryDeep = accent.deep,
            primaryBg = accent.bg,
            green = Color(0xFF59D99B),
            greenBg = Color(0x4A36D38F),
            red = Color(0xFFFF6F82),
            redBg = Color(0x45FF5B74),
            orange = Color(0xFFFFB45E),
            blue = Color(0xFF5EB9FF),
        )
    } else {
        LexiPalette(
            isDark = false,
            bg = Color(0xFFF5F5F8),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF8F8FB),
            surface3 = Color(0xFFF0F1F5),
            border = Color(0xFFE3E4E9),
            border2 = Color(0xFFD3D5DD),
            text = Color(0xFF16171C),
            muted = Color(0xFF686D78),
            muted2 = Color(0xFF9398A3),
            primary = accent.primary,
            primary2 = accent.deep, // light theme uses deeper tones for contrast
            primaryDeep = accent.deep,
            primaryBg = accent.bg,
            green = Color(0xFF148B5C),
            greenBg = Color(0x38148B5C),
            red = Color(0xFFD84B60),
            redBg = Color(0x36D84B60),
            orange = Color(0xFFBB6B12),
            blue = Color(0xFF287DC0),
        )
    }
}

// Pool accent chips (.pool-accent-*): fixed hues independent of the UI accent.
val POOL_ACCENTS: Map<String, Color> = mapOf(
    "emerald" to Color(0xFF22B573),
    "blue" to Color(0xFF529EE4),
    "violet" to Color(0xFF8B7CFF),
    "rose" to Color(0xFFE8789E),
    "orange" to Color(0xFFE49C52),
    "teal" to Color(0xFF49BFA4),
    "indigo" to Color(0xFF6366F1),
)

fun poolAccentColor(accent: String, palette: LexiPalette): Color =
    POOL_ACCENTS[accent] ?: if (accent == "muted") palette.muted2 else POOL_ACCENTS.getValue("emerald")

val LocalPalette = staticCompositionLocalOf { lexiPalette(dark = true, accentName = "emerald") }

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>): FontFamily =
    FontFamily(
        weights.map { weight ->
            Font(
                resId = resId,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

// 'DM Sans' body font and 'Manrope' display font, same as the web app.
val DmSans = variableFamily(R.font.dm_sans, listOf(400, 500, 600, 700))
val Manrope = variableFamily(R.font.manrope, listOf(500, 600, 700, 800))

private fun typography(text: Color): Typography {
    val base = TextStyle(fontFamily = DmSans, color = text)
    return Typography(
        displaySmall = base.copy(fontFamily = Manrope, fontSize = 30.sp, fontWeight = FontWeight.W700, lineHeight = 34.sp),
        headlineMedium = base.copy(fontFamily = Manrope, fontSize = 27.sp, fontWeight = FontWeight.W700, lineHeight = 33.sp),
        headlineSmall = base.copy(fontFamily = Manrope, fontSize = 22.sp, fontWeight = FontWeight.W700, lineHeight = 27.sp),
        titleLarge = base.copy(fontFamily = Manrope, fontSize = 20.sp, fontWeight = FontWeight.W700),
        titleMedium = base.copy(fontFamily = Manrope, fontSize = 17.sp, fontWeight = FontWeight.W700),
        titleSmall = base.copy(fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.W700),
        bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.W700),
        labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
        labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
    )
}

@Composable
fun LexiTheme(
    theme: String, // dark | light | system
    accent: String,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val palette = lexiPalette(dark, accent)
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.red,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface2,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = palette.red,
        )
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography(palette.text),
            content = content,
        )
    }
}
