// Theme.kt

package com.rambox.plus.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb as colorToArgb
import com.rambox.plus.constants.AppThemeKey
import com.rambox.plus.constants.CustomThemeColorKey
import com.rambox.plus.utils.rememberPreference
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFF8E8E8E)

/** the colour themes: RamBox + is the gradient one and the first pick */
enum class AppThemeMode(val label: String) {
    RAMBOX("RamBox +"),
    BLACK("Black"),
    DEFAULT("Default"),
    CUSTOM("Custom"),
}

/** the RamBox + gradient's three colours: indigo, purple, pink */
val RamBoxGradientColors = listOf(Color(0xFF6A5CFF), Color(0xFFC04DFF), Color(0xFFFF5FA2))
val RamBoxThemeSeed = Color(0xFF8B5CF6)

/** a handful of colours to pick from for the Custom theme */
val CustomThemePresets = listOf(
    Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981),
    Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFEC4899), Color(0xFF9CA3AF),
)

private fun hueOf(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.colorToArgb(), hsv)
    return hsv[0]
}

private fun satOf(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.colorToArgb(), hsv)
    return hsv[1]
}

/** a dark scheme tinted with [seed]: coloured accents, cards and a deep tinted background */
private fun tintedDarkScheme(seed: Color): ColorScheme {
    val h = hueOf(seed)
    val s = satOf(seed).coerceIn(0.35f, 0.9f)
    val h2 = (h + 30f) % 360f
    val bg = Color.hsv(h, 0.75f, 0.17f)
    return darkColorScheme(
        primary = Color.hsv(h, 0.35f, 1f),
        onPrimary = Color.hsv(h, 0.8f, 0.2f),
        primaryContainer = Color.hsv(h, s, 0.38f),
        onPrimaryContainer = Color.White,
        secondary = Color.hsv(h2, 0.35f, 0.95f),
        onSecondary = Color.hsv(h2, 0.8f, 0.2f),
        secondaryContainer = Color.hsv(h2, s * 0.8f, 0.30f),
        onSecondaryContainer = Color.White,
        background = bg,
        onBackground = Color.White,
        surface = bg,
        onSurface = Color.White,
        surfaceVariant = Color.hsv(h, 0.6f, 0.26f),
        onSurfaceVariant = Color.White,
        surfaceTint = bg,
    )
}

/** the page behind everything: a gradient for RamBox + and Custom, flat black for the others */
@Composable
fun appBackdropBrush(fallback: Color): Brush {
    val mode = AppThemeMode.entries.firstOrNull { it.name == rememberPreference(AppThemeKey, "DEFAULT").value } ?: AppThemeMode.DEFAULT
    val custom = Color(rememberPreference(CustomThemeColorKey, RamBoxThemeSeed.colorToArgb()).value)
    return when (mode) {
        AppThemeMode.RAMBOX -> Brush.verticalGradient(listOf(Color(0xFF301A6B), Color(0xFF120726), Color(0xFF07030F)))
        AppThemeMode.CUSTOM -> {
            val h = hueOf(custom); val sat = satOf(custom).coerceIn(0.35f, 0.9f)
            Brush.verticalGradient(listOf(Color.hsv(h, sat, 0.30f), Color.hsv(h, sat, 0.10f), Color.hsv(h, sat, 0.04f)))
        }
        else -> Brush.verticalGradient(listOf(fallback, fallback))
    }
}

/**
 * rambox renders dark-only.
 *
 * The light palette was never finished - large parts of the UI hardcode white text and dark
 * glass - so on a light-mode phone the app looked broken rather than light. Pinned here as one
 * switch instead of being decided independently at every call site.
 */
const val ForceDarkTheme = true

@Composable
fun RamBoxTheme(
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Dynamic (Material You) colour is deliberately not used - the app ships its own palette.
    // The branch is gone rather than dead-but-present: dynamic*ColorScheme is API 31.
    val themeMode = AppThemeMode.entries.firstOrNull { it.name == rememberPreference(AppThemeKey, "DEFAULT").value } ?: AppThemeMode.DEFAULT
    val customThemeColor = Color(rememberPreference(CustomThemeColorKey, RamBoxThemeSeed.colorToArgb()).value)
    androidx.compose.runtime.SideEffect {
        val on = darkTheme && (themeMode == AppThemeMode.RAMBOX || themeMode == AppThemeMode.CUSTOM)
        ThemeBg.set(on, if (themeMode == AppThemeMode.CUSTOM) hueOf(customThemeColor) else hueOf(RamBoxThemeSeed))
    }
    val baseColorScheme = run {

        if (darkTheme && themeMode == AppThemeMode.RAMBOX) {
            tintedDarkScheme(RamBoxThemeSeed)
        } else if (darkTheme && themeMode == AppThemeMode.CUSTOM) {
            tintedDarkScheme(customThemeColor)
        } else if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFE0E0E0),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF333333),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFFB0B0B0),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF2C2C2C),
                onSecondaryContainer = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surface = Color.Black,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF1A1A1A),
                onSurfaceVariant = Color.White,
                surfaceTint = Color.Black
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color.Black,
                secondary = Color(0xFF333333),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCCCCC),
                onSecondaryContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF0F0F0),
                onSurfaceVariant = Color.Black,
                surfaceTint = Color.White
            )
        }
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, themeMode) {
        if (darkTheme && (pureBlack || themeMode == AppThemeMode.BLACK)) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    val font = InterFontFamily
    val typography = remember(font) { appTypography(font) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        ProvideTextStyle(
            value = typography.bodyLarge,
            content = content
        )
    }
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}

/**
 * the theme's background colours. The app's pages, cards and bars are painted from a few dozen dark greys; each
 * goes through [bg], which swaps a dark grey for the same shade in the theme's colour while the theme is RamBox + or
 * Custom. Only dark, colourless greys change: text, album art, photos, icons and anything colourful stay as they are.
 */
object ThemeBg {
    private var enabled by androidx.compose.runtime.mutableStateOf(false)
    private var hue by androidx.compose.runtime.mutableFloatStateOf(262f)

    fun set(on: Boolean, themeHue: Float) {
        if (enabled != on) enabled = on
        if (hue != themeHue) hue = themeHue
    }

    fun bg(argb: Long): Color {
        val c = Color(argb)
        if (!enabled) return c
        val top = maxOf(c.red, c.green, c.blue)
        val low = minOf(c.red, c.green, c.blue)
        // light or colourful: not a background
        if (top > 0.30f || top - low > 0.06f) return c
        val v = (0.17f + top * 0.9f).coerceAtMost(0.46f)
        val s = (0.75f - top * 1.5f).coerceIn(0.30f, 0.75f)
        return Color.hsv(hue, s, v, c.alpha)
    }
}
