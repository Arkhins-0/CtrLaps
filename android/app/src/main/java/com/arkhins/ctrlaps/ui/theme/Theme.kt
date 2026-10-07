package com.arkhins.ctrlaps.ui.theme

import com.arkhins.ctrlaps.ui.contrastText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.remember
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.arkhins.ctrlaps.R
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
    /** Words and icons on the accent: near-black, or white on a dark custom colour. */
    val onAccent: Color = Color(0xFF0B0B0C),
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

/** The palette for the chosen look: light or dark, pure black or not, and the accent (with its deeper shade). */
fun paletteFor(dark: Boolean, black: Boolean, accent: Color, deep: Color): Palette {
    val base = if (!dark) LightPalette else if (black) BlackPalette else DarkPalette
    return base.copy(gold = accent, goldDeep = deep, onAccent = accent.contrastText())
}

/** WCAG contrast between two colours, 1 to 21. */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Any colour (a custom pick, the wallpaper's) made fit to be the accent on this page: lighter until it stands out on
 * the dark theme's page, darker on the light one's, so gold-coloured words stay readable. Its deep shade is 15% darker.
 */
fun fitAccent(color: Color, page: Color, dark: Boolean): Pair<Color, Color> {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    var c = color
    var steps = 0
    while (contrast(c, page) < 3f && steps < 40) {
        if (dark) { hsv[2] = (hsv[2] + 0.03f).coerceAtMost(1f); hsv[1] = (hsv[1] - if (hsv[2] >= 1f) 0.03f else 0f).coerceAtLeast(0f) }
        else hsv[2] = (hsv[2] - 0.03f).coerceAtLeast(0f)
        c = Color(android.graphics.Color.HSVToColor(hsv))
        steps++
    }
    val deep = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }.also { it[2] *= 0.85f }
    return c to Color(android.graphics.Color.HSVToColor(deep))
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

/** Words and icons on the accent: near-black on gold and the presets, white on a dark custom colour. */
val OnGold: Color get() = palette.onAccent

/** Plus Jakarta Sans (SIL Open Font License, licenses/PlusJakartaSans-OFL.txt), the website's font too. */
private val Jakarta = FontFamily(
    Font(R.font.jakarta, FontWeight.Normal),
    Font(R.font.jakarta_semi_bold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
)
/** The app's font, or the phone's own when "Use device font" is on. */
private fun family(device: Boolean): FontFamily = if (device) FontFamily.Default else Jakarta
val Display: FontFamily get() = family(ThemeSetting.deviceFont.value)
val Body: FontFamily get() = family(ThemeSetting.deviceFont.value)

private fun scheme(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.gold, onPrimary = p.onAccent, primaryContainer = p.goldDeep, onPrimaryContainer = p.onAccent,
    secondary = p.snow, onSecondary = p.night, tertiary = p.goldDeep, onTertiary = p.onAccent,
    background = p.night, onBackground = p.snow, surface = p.panel, onSurface = p.snow,
    surfaceVariant = p.panel, onSurfaceVariant = p.snowSoft, outline = p.line, outlineVariant = p.line,
    error = p.danger, onError = Color(0xFF0B0B0C),
    surfaceContainerLowest = p.lowest, surfaceContainerLow = p.low, surfaceContainer = p.panel,
    surfaceContainerHigh = p.high, surfaceContainerHighest = p.highest, surfaceDim = p.dim, surfaceBright = p.bright,
    inverseSurface = p.snow, inverseOnSurface = p.night, inversePrimary = p.goldDeep,
) else lightColorScheme(
    primary = p.gold, onPrimary = p.onAccent, primaryContainer = p.goldDeep, onPrimaryContainer = p.onAccent,
    secondary = p.snow, onSecondary = p.night, tertiary = p.goldDeep, onTertiary = p.onAccent,
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

/** The type scale in [Display] and [Body]: rebuilt when "Use device font" changes. */
fun ctrlapsTypography() = Typography(
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

/**
 * The chosen theme, kept on the phone: light or dark, pure black for the dark one, the accent (a preset, a custom
 * colour, or the wallpaper's with Material You), and the app's font or the phone's.
 */
object ThemeSetting {
    private var prefs: SharedPreferences? = null
    // Dark until someone picks otherwise: the look the app was made in.
    private val _mode = MutableStateFlow(ThemeMode.Dark)
    val mode: StateFlow<ThemeMode> = _mode
    private val _black = MutableStateFlow(false)
    val black: StateFlow<Boolean> = _black
    private val _accent = MutableStateFlow(Accent.Gold)
    val accent: StateFlow<Accent> = _accent
    private val _deviceFont = MutableStateFlow(false)
    val deviceFont: StateFlow<Boolean> = _deviceFont
    private val _materialYou = MutableStateFlow(false)
    val materialYou: StateFlow<Boolean> = _materialYou
    private val _custom = MutableStateFlow(false)
    val custom: StateFlow<Boolean> = _custom
    private val _customColor = MutableStateFlow(0xFFFFD100.toInt())
    val customColor: StateFlow<Int> = _customColor

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ctrlaps_theme", Context.MODE_PRIVATE).also { p ->
            _mode.value = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "Dark") }.getOrDefault(ThemeMode.Dark)
            _black.value = p.getBoolean("black", false)
            _accent.value = runCatching { Accent.valueOf(p.getString("accent", null) ?: "Gold") }.getOrDefault(Accent.Gold)
            _deviceFont.value = p.getBoolean("deviceFont", false)
            _materialYou.value = p.getBoolean("materialYou", false)
            _custom.value = p.getBoolean("custom", false)
            _customColor.value = p.getInt("customColor", 0xFFFFD100.toInt())
        }
    }

    fun setDeviceFont(on: Boolean) {
        _deviceFont.value = on
        prefs?.edit()?.putBoolean("deviceFont", on)?.apply()
    }

    /** Material You and a custom colour exclude each other: turning one on turns the other off. */
    fun setMaterialYou(on: Boolean) {
        _materialYou.value = on
        if (on) _custom.value = false
        prefs?.edit()?.putBoolean("materialYou", on)?.putBoolean("custom", _custom.value)?.apply()
    }

    fun setCustom(on: Boolean) {
        _custom.value = on
        if (on) _materialYou.value = false
        prefs?.edit()?.putBoolean("custom", on)?.putBoolean("materialYou", _materialYou.value)?.apply()
    }

    /** A colour from the picker: it becomes the accent at once. */
    fun setCustomColor(argb: Int) {
        _customColor.value = argb
        setCustom(true)
        prefs?.edit()?.putInt("customColor", argb)?.apply()
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString("mode", mode.name)?.apply()
    }

    fun setBlack(on: Boolean) {
        _black.value = on
        prefs?.edit()?.putBoolean("black", on)?.apply()
    }

    /** A preset: it is the accent again, so a custom colour and Material You go off. */
    fun setAccent(accent: Accent) {
        _accent.value = accent
        _custom.value = false
        _materialYou.value = false
        prefs?.edit()?.putString("accent", accent.name)?.putBoolean("custom", false)?.putBoolean("materialYou", false)?.apply()
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
    val materialYou by ThemeSetting.materialYou.collectAsState()
    val custom by ThemeSetting.custom.collectAsState()
    val customColor by ThemeSetting.customColor.collectAsState()
    val deviceFont by ThemeSetting.deviceFont.collectAsState()
    val context = LocalContext.current
    val page = if (!dark) LightPalette.night else if (black) BlackPalette.night else DarkPalette.night
    // Material You (the wallpaper's colour, Android 12+) over a custom colour over the preset.
    val (accentColor, deep) = when {
        materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            fitAccent((if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary, page, dark)
        custom -> fitAccent(Color(customColor), page, dark)
        dark -> accent.dark to accent.darkDeep
        else -> accent.light to accent.lightDeep
    }
    val p = paletteFor(dark, black, accentColor, deep)
    if (palette != p) palette = p
    val typography = remember(deviceFont) { ctrlapsTypography() }
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
    MaterialTheme(colorScheme = scheme(p), typography = typography, shapes = CtrlapsShapes, content = content)
}
