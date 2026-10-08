package com.arkhins.ctrlaps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkhins.ctrlaps.data.CrashReporter
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.theme.CtrlapsTheme
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.theme.ThemeSetting

/**
 * After the app stopped on screen (see CrashReporter): what happened, the report to copy or share, a ticket with it
 * filled in, and the app opened again. Runs in its own process (":crash"), so none of the app's machinery is here.
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSetting.init(this)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val report = CrashReporter.read(this) ?: "No report was kept."
        setContent {
            CtrlapsTheme {
                Column(
                    Modifier.fillMaxSize().background(Night).safeDrawingPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Danger, modifier = Modifier.padding(top = 16.dp).size(40.dp))
                    Text("${Config.APP_NAME} stopped", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Snow)
                    Text(
                        "Something went wrong and the app had to close. The report below tells the support team what happened: raise a ticket with it, or copy or share it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SnowSoft,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().background(NightPanel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        SelectionContainer {
                            Text(
                                report,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                                color = SnowSoft,
                                softWrap = false,
                                modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                    GoldButton("Raise a ticket", Modifier.fillMaxWidth()) { open(CrashReporter.ticketText(report)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Copy", Modifier.weight(1f)) { copy(report) }
                        GhostButton("Share", Modifier.weight(1f)) { share(report) }
                        GhostButton("Open again", Modifier.weight(1f)) { open(null) }
                    }
                }
            }
        }
    }

    /** The app again, from the start; with [ticket], on the support form with it filled in. */
    private fun open(ticket: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { if (ticket != null) putExtra(CrashReporter.EXTRA_TICKET, ticket) },
        )
        finish()
    }

    private fun copy(report: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("${Config.APP_NAME} crash report", report))
        // Android 13 and later show their own "Copied".
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "${Config.APP_NAME} crash report")
            .putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, "Share the report"))
    }
}
