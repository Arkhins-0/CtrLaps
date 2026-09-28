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
)

val DarkPalette = Palette(
    night = Color(0xFF0B0B0C), panel = Color(0xFF161618), line = Color(0xFF26262A),
    snow = Color(0xFFF4F4F5), snowSoft = Color(0xFFB4B4BA), snowFaint = Color(0xFF7A7A82),
    gold = Color(0xFFFFD100), goldDeep = Color(0xFFE0A800), danger = Color(0xFFFF5A5F), dark = true,
)

val LightPalette = Palette(
    night = Color(0xFFF3F3F5), panel = Color(0xFFFFFFFF), line = Color(0xFFE2E2E7),
    snow = Color(0xFF111114), snowSoft = Color(0xFF45454D), snowFaint = Color(0xFF787880),
    gold = Color(0xFFC89B00), goldDeep = Color(0xFFA67F00), danger = Color(0xFFE5484D), dark = false,
)

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
) else lightColorScheme(
    primary = p.gold, onPrimary = OnGold, primaryContainer = p.goldDeep, onPrimaryContainer = OnGold,
    secondary = p.snow, onSecondary = p.night, tertiary = p.goldDeep, onTertiary = OnGold,
    background = p.night, onBackground = p.snow, surface = p.panel, onSurface = p.snow,
    surfaceVariant = p.panel, onSurfaceVariant = p.snowSoft, outline = p.line, outlineVariant = p.line,
    error = p.danger, onError = Color.White,
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

/** The chosen theme, kept on the phone. */
object ThemeSetting {
    private var prefs: SharedPreferences? = null
    // Dark until someone picks otherwise: the look the app was made in.
    private val _mode = MutableStateFlow(ThemeMode.Dark)
    val mode: StateFlow<ThemeMode> = _mode

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ctrlaps_theme", Context.MODE_PRIVATE).also { p ->
            _mode.value = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "Dark") }.getOrDefault(ThemeMode.Dark)
        }
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString("mode", mode.name)?.apply()
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
    val p = if (dark) DarkPalette else LightPalette
    if (palette !== p) palette = p
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
    MaterialTheme(colorScheme = scheme(p), typography = CtrlapsTypography, content = content)
}
