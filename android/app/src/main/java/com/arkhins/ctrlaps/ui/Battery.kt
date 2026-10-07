package com.arkhins.ctrlaps.ui

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * What decides whether popups reach a sleeping phone. Android's battery
 * optimisation puts the app on hold; on top of that many phone makers have
 * their own switches: an autostart list (Xiaomi, Oppo, Vivo, Huawei and
 * more) and, on Xiaomi, a battery saver of its own whose "No restrictions"
 * is separate from Android's. Each has a page to open; only some can be read.
 */
object Battery {
    fun isExempt(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** The system's "allow to run in background?" dialog for this app. */
    fun requestExemption(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    val isXiaomi: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Whether MIUI lets CTR[L]APS start by itself (its hidden app-op 10008), which is what
     * brings popups back after recent apps are cleared. Null when the phone cannot say.
     */
    fun autostartAllowed(context: Context): Boolean? {
        if (!isXiaomi) return null
        return runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            check.invoke(ops, 10008, Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** The phone maker's autostart page, if it has one; the first known one on this phone wins. Null otherwise. */
    fun autostartIntent(context: Context): Intent? = AUTOSTART_SCREENS.firstNotNullOfOrNull { (pkg, cls) ->
        Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Xiaomi's own battery saver page for this app ("No restrictions"); null on other phones. */
    fun xiaomiBatteryIntent(context: Context): Intent? {
        if (!isXiaomi) return null
        return Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** The notification channels switched off one by one (the app's notifications as a whole may still be on). */
    fun channelsOff(context: Context): List<String> {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return emptyList()
        return context.getSystemService(NotificationManager::class.java).notificationChannels
            .filter { it.importance == NotificationManager.IMPORTANCE_NONE }
            .map { it.name.toString() }
    }

    // Known autostart managers, most common first.
    private val AUTOSTART_SCREENS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
    )
}
