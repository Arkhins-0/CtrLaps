package com.arkhins.ctrlaps

import com.arkhins.ctrlaps.ui.theme.ThemeSetting
import com.arkhins.ctrlaps.ui.theme.InterfaceSetting
import com.arkhins.ctrlaps.ui.theme.AppMotion
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arkhins.ctrlaps.push.Notifications
import com.arkhins.ctrlaps.ui.Links
import com.arkhins.ctrlaps.ui.CtrlapsApp
import com.arkhins.ctrlaps.ui.theme.CtrlapsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ThemeSetting.init(this)
        InterfaceSetting.init(this)
        AppMotion.readSystem(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handle(intent)
        // Dialogs read their window theme from the activity's when they open, so blur on or off reaches the next one.
        lifecycleScope.launch {
            InterfaceSetting.blur.collect { on ->
                theme.applyStyle(if (on) R.style.ThemeOverlay_Ctrlaps_DialogBlur else R.style.ThemeOverlay_Ctrlaps_DialogPlain, true)
            }
        }
        // The window's usual recomposer, but with the app's animation scale in it (Settings → Interface): every
        // animation inside, dialogs and sheets included, takes its length from AppMotion.
        val recomposer = window.decorView.createLifecycleAwareWindowRecomposer(AppMotion, lifecycle)
        setContent(parent = recomposer) {
            CompositionLocalProvider(LocalApp provides (application as CtrlapsApplication)) {
                CtrlapsTheme {
                    CtrlapsApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppMotion.readSystem(this)
        Notifications.foreground = true
    }

    override fun onPause() {
        Notifications.foreground = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tapped notification carries an in-app link; an App Link carries a URL. Both become a pending route. */
    private fun handle(intent: Intent?) {
        val fromNotification = intent?.getStringExtra(Notifications.EXTRA_LINK)
        val fromUrl = intent?.data?.let { uri -> uri.path?.let { p -> p + (uri.query?.let { "?$it" } ?: "") } }
        // Debug builds only: `adb shell am start -n com.arkhins.ctrlaps/.MainActivity --es route settings` opens any screen
        // by its navigation route (Settings, Storage, a group's info…), for testing on a phone that refuses adb taps.
        if (BuildConfig.DEBUG) intent?.getStringExtra("sheet")?.let { com.arkhins.ctrlaps.ui.DebugHooks.sheet.value = it; intent.removeExtra("sheet") }
        val debugRoute = if (BuildConfig.DEBUG) intent?.getStringExtra("route")?.let { "route:$it" } else null
        val link = debugRoute ?: fromNotification ?: fromUrl ?: return
        Links.pending.value = link
        intent?.removeExtra(Notifications.EXTRA_LINK)
        intent?.removeExtra("route")
    }
}
