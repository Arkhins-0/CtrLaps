package com.arkhins.ctrlaps.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How long animations take: each one's length is multiplied by [scale]. */
enum class AnimationSpeed(val label: String, val scale: Float) {
    Slow("Slow", 1.5f),
    Normal("Normal", 1f),
    Fast("Fast", 0.7f),
    Fastest("Fastest", 0.5f),
}

/**
 * How the app moves and feels, kept on the phone (Settings → Interface), after Arkhime's interface settings:
 * animations on or off and their speed, haptics, and blur behind dialogs and on the profile banner.
 */
object InterfaceSetting {
    private var prefs: SharedPreferences? = null
    private val _animations = MutableStateFlow(true)
    val animations: StateFlow<Boolean> = _animations
    private val _speed = MutableStateFlow(AnimationSpeed.Normal)
    val speed: StateFlow<AnimationSpeed> = _speed
    private val _blur = MutableStateFlow(true)
    val blur: StateFlow<Boolean> = _blur
    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ctrlaps_interface", Context.MODE_PRIVATE).also { p ->
            _animations.value = p.getBoolean("animations", true)
            _speed.value = runCatching { AnimationSpeed.valueOf(p.getString("speed", null) ?: "Normal") }.getOrDefault(AnimationSpeed.Normal)
            _blur.value = p.getBoolean("blur", true)
            _haptics.value = p.getBoolean("haptics", true)
        }
    }

    fun setAnimations(on: Boolean) {
        _animations.value = on
        prefs?.edit()?.putBoolean("animations", on)?.apply()
    }

    fun setSpeed(speed: AnimationSpeed) {
        _speed.value = speed
        prefs?.edit()?.putString("speed", speed.name)?.apply()
    }

    fun setHaptics(on: Boolean) {
        _haptics.value = on
        prefs?.edit()?.putBoolean("haptics", on)?.apply()
    }

    fun setBlur(on: Boolean) {
        _blur.value = on
        prefs?.edit()?.putBoolean("blur", on)?.apply()
    }
}

/**
 * The length of every Compose animation in the app is multiplied by this: it rides in the window's recomposer (see
 * MainActivity), so screen slides, sheets, the photo viewer and the rest all follow it without knowing. 0 with
 * animations off, which makes each one jump to its end; otherwise the chosen speed times the phone's own animation
 * scale (Developer options, or Accessibility's "Remove animations"), which still applies. Flings keep their own speed.
 */
object AppMotion : MotionDurationScale {
    /** The phone's animator duration scale, read when the app comes to the front. */
    var system by mutableFloatStateOf(1f)
        private set

    override val scaleFactor: Float
        get() = if (!InterfaceSetting.animations.value) 0f else InterfaceSetting.speed.value.scale * system

    /** True when animations play at all: the app's switch and the phone's both on. */
    val on: Boolean get() = scaleFactor > 0f

    fun readSystem(context: Context) {
        system = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    }
}
