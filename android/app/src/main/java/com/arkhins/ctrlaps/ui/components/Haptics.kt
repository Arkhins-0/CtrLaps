package com.arkhins.ctrlaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.arkhins.ctrlaps.ui.theme.InterfaceSetting

/**
 * The app's buzzes, each one light and short, and none at all with Settings → Interface → Haptics off (the phone's
 * own "Touch feedback" switch still applies on top). Phones without the newer kinds get the nearest one.
 */
class Haptics(private val feedback: HapticFeedback) {
    /** A switch, a star or a bell turned on or off. */
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** Something sent or saved. */
    fun confirm() = play(HapticFeedbackType.Confirm)

    /** A pick among several: a chip, a tab. */
    fun tick() = play(HapticFeedbackType.SegmentTick)

    /** A wheel passing a row, or a list being pulled far enough to refresh. */
    fun step() = play(HapticFeedbackType.SegmentFrequentTick)

    /** A pull or drag reaching the point where letting go does something. */
    fun threshold() = play(HapticFeedbackType.GestureThresholdActivate)

    /** A long press that picks something up (selecting a message, copying). */
    fun longPress() = play(HapticFeedbackType.LongPress)

    private fun play(type: HapticFeedbackType) {
        if (InterfaceSetting.haptics.value) feedback.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
