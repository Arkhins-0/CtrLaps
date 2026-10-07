package com.arkhins.ctrlaps.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Night with one gold accent — the CTR yellow on near-black — or its light twin, paper white with a deeper gold
 * that still reads on white. Every colour below is the chosen palette's, so the whole app follows the theme
 * (Settings → Theme: the phone's, light or dark) without each screen knowing about it.
 */
data class Palette(
    val night: Color,
    val panel: Color,
    val line: Color,
    val snow: Color,
    val snowSoft: Color,
    val snowFaint: Color,
    val gold: Color,
    val goldDeep: Color,
    val danger: Color,
    val dark: Boolean,
    /**
     * Surfaces from furthest back to nearest (Material's container levels): the page sits on [night], cards on
     * [panel] (= container), menus and sheets on [low]/[high], dialogs and docked bars on [high], pressed or picked
     * things on [highest].
     */
    val lowest: Color,
    val low: Color,
    val high: Color,
    val highest: Color,
    val dim: Color,
    val bright: Color,
)

val DarkPalette = Palette(
    night = Color(0xFF0B0B0C), panel = Color(0xFF161618), line = Color(0xFF26262A),
    snow = Color(0xFFF4F4F5), snowSoft = Color(0xFFB4B4BA), snowFaint = Color(0xFF7A7A82),
    gold = Color(0xFFFFD100), goldDeep = Color(0xFFE0A800), danger = Color(0xFFFF5A5F), dark = true,
    lowest = Color(0xFF070708), low = Color(0xFF111113), high = Color(0xFF1E1E21), highest = Color(0xFF28282C),
    dim = Color(0xFF0B0B0C), bright = Color(0xFF333338),
)

/** The dark theme on a pure black page, for OLED screens: cards stay a shade above it so they still read. */
val BlackPalette = DarkPalette.copy(
    night = Color(0xFF000000), panel = Color(0xFF0E0E0F), line = Color(0xFF1F1F22),
    lowest = Color(0xFF000000), low = Color(0xFF080809), high = Color(0xFF161618), highest = Color(0xFF202023),
    dim = Color(0xFF000000), bright = Color(0xFF2A2A2E),
)

val LightPalette = Palette(
    night = Color(0xFFF3F3F5), panel = Color(0xFFFFFFFF), line = Color(0xFFE2E2E7),
    snow = Color(0xFF111114), snowSoft = Color(0xFF45454D), snowFaint = Color(0xFF787880),
    gold = Color(0xFFC89B00), goldDeep = Color(0xFFA67F00), danger = Color(0xFFE5484D), dark = false,
    lowest = Color(0xFFFFFFFF), low = Color(0xFFF8F8FA), high = Color(0xFFFFFFFF), highest = Color(0xFFE9E9EE),
    dim = Color(0xFFDCDCE1), bright = Color(0xFFFFFFFF),
)

/**
 * The accent: CTR gold unless the person picks another. Each has a bright shade for the dark theme and a deeper one
 * for the light theme, all light enough for the near-black [OnGold] text on them. No red: red means danger here.
 */
enum class Accent(val label: String, val dark: Color, val darkDeep: Color, val light: Color, val lightDeep: Color) {
    Gold("Gold", Color(0xFFFFD100), Color(0xFFE0A800), Color(0xFFC89B00), Color(0xFFA67F00)),
    Orange("Orange", Color(0xFFFF8A3D), Color(0xFFE8701A), Color(0xFFE8701A), Color(0xFFC25A10)),
    Green("Green", Color(0xFF4ADE80), Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF15803D)),
    Blue("Blue", Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF3B82F6), Color(0xFF2563EB)),
    Violet("Violet", Color(0xFFB7A4FF), Color(0xFF9F86FF), Color(0xFF8B5CF6), Color(0xFF7C3AED)),
}

/** The palette for the chosen look: light or dark, pure black or not, and the accent. */
fun paletteFor(dark: Boolean, black: Boolean, accent: Accent): Palette {
    val base = if (!dark) LightPalette else if (black) BlackPalette else DarkPalette
    return if (dark) base.copy(gold = accent.dark, goldDeep = accent.darkDeep) else base.copy(gold = accent.light, goldDeep = accent.lightDeep)
}

/** The palette in use; set by [CtrlapsTheme] from the setting and the phone's own theme. */
internal var palette by mutableStateOf(DarkPalette)

val Night: Color get() = palette.night
val NightPanel: Color get() = palette.panel
val NightLine: Color get() = palette.line
val Snow: Color get() = palette.snow
val SnowSoft: Color get() = palette.snowSoft
val SnowFaint: Color get() = palette.snowFaint
val Gold: Color get() = palette.gold
val GoldDeep: Color get() = palette.goldDeep
val Danger: Color get() = palette.danger
/** Docked bars and raised surfaces (the save bar, dialogs): one step above a card. */
val NightHigh: Color get() = palette.high
val NightHighest: Color get() = palette.highest

/** Words and icons on gold (and dark shades over photos): near-black in either theme, so they always read. */
val OnGold = Color(0xFF0B0B0C)

val Display: FontFamily = FontFamily.SansSerif
val Body: FontFamily = FontFamily.SansSerif

private fun scheme(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.gold, onPrimary = OnGold, primaryContainer = p.goldDeep, onPrimaryContainer = OnGold,
    secondary = p.snow, onSecondary = p.night, tertiary = p.goldDeep, onTertiary = OnGold,
    background = p.night, onBackground = p.snow, surface = p.panel, onSurface = p.snow,
    surfaceVariant = p.panel, onSurfaceVariant = p.snowSoft, outline = p.line, outlineVariant = p.line,
    error = p.danger, onError = OnGold,
    surfaceContainerLowest = p.lowest, surfaceContainerLow = p.low, surfaceContainer = p.panel,
    surfaceContainerHigh = p.high, surfaceContainerHighest = p.highest, surfaceDim = p.dim, surfaceBright = p.bright,
    inverseSurface = p.snow, inverseOnSurface = p.night, inversePrimary = p.goldDeep,
) else lightColorScheme(
    primary = p.gold, onPrimary = OnGold, primaryContainer = p.goldDeep, onPrimaryContainer = OnGold,
    secondary = p.snow, onSecondary = p.night, tertiary = p.goldDeep, onTertiary = OnGold,
    background = p.night, onBackground = p.snow, surface = p.panel, onSurface = p.snow,
    surfaceVariant = p.panel, onSurfaceVariant = p.snowSoft, outline = p.line, outlineVariant = p.line,
    error = p.danger, onError = Color.White,
    surfaceContainerLowest = p.lowest, surfaceContainerLow = p.low, surfaceContainer = p.panel,
    surfaceContainerHigh = p.high, surfaceContainerHighest = p.highest, surfaceDim = p.dim, surfaceBright = p.bright,
    inverseSurface = p.snow, inverseOnSurface = p.night, inversePrimary = p.goldDeep,
)

/**
 * Four corner sizes for the whole app: 8dp small bits, 12dp cards and fields, 18dp big buttons and docked bars,
 * 32dp dialogs and sheets. Chips and the main buttons are pills.
 */
val CtrlapsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val CtrlapsTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
)

/** How the app looks: follow the phone, or always light, or always dark. */
enum class ThemeMode(val label: String) { System("System default"), Light("Light"), Dark("Dark") }

/** The chosen theme, kept on the phone: light or dark, pure black for the dark one, and the accent. */
object ThemeSetting {
    private var prefs: SharedPreferences? = null
    // Dark until someone picks otherwise: the look the app was made in.
    private val _mode = MutableStateFlow(ThemeMode.Dark)
    val mode: StateFlow<ThemeMode> = _mode
    private val _black = MutableStateFlow(false)
    val black: StateFlow<Boolean> = _black
    private val _accent = MutableStateFlow(Accent.Gold)
    val accent: StateFlow<Accent> = _accent

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ctrlaps_theme", Context.MODE_PRIVATE).also { p ->
            _mode.value = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "Dark") }.getOrDefault(ThemeMode.Dark)
            _black.value = p.getBoolean("black", false)
            _accent.value = runCatching { Accent.valueOf(p.getString("accent", null) ?: "Gold") }.getOrDefault(Accent.Gold)
        }
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString("mode", mode.name)?.apply()
    }

    fun setBlack(on: Boolean) {
        _black.value = on
        prefs?.edit()?.putBoolean("black", on)?.apply()
    }

    fun setAccent(accent: Accent) {
        _accent.value = accent
        prefs?.edit()?.putString("accent", accent.name)?.apply()
    }
}

@Composable
fun CtrlapsTheme(content: @Composable () -> Unit) {
    val mode by ThemeSetting.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val black by ThemeSetting.black.collectAsState()
    val accent by ThemeSetting.accent.collectAsState()
    val p = paletteFor(dark, black, accent)
    if (palette != p) palette = p
    // The status and navigation bar icons: dark on the light theme, light on the dark one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = scheme(p), typography = CtrlapsTypography, shapes = CtrlapsShapes, content = content)
}
