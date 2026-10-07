package com.arkhins.ctrlaps.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.screens.AboutSheet
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * Questions someone chose not to be asked again ("Don't ask me again" on a [QuestionSheet]), kept on the phone by key.
 * Settings can bring them all back.
 */
object DontAsk {
    private const val PREFS = "dont_ask"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun skipped(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)

    fun remember(context: Context, key: String) = prefs(context).edit().putBoolean(key, true).apply()

    fun any(context: Context): Boolean = prefs(context).all.values.any { it == true }

    fun askAgain(context: Context) = prefs(context).edit().clear().apply()
}

/**
 * A question as a sheet, Arkhime's reusable bottom sheet: a title, what it means, then Cancel and the answer side by
 * side ([danger] makes the answer red). With [dontAskKey], a "Don't ask me again" box; it is kept only when the
 * answer is given, so ticking it and then cancelling changes nothing. Callers skip the sheet with [DontAsk.skipped].
 */
@Composable
fun QuestionSheet(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dontAskKey: String? = null,
) {
    val context = LocalContext.current
    var dontAsk by remember { mutableStateOf(false) }
    AboutSheet(
        title,
        onClose = onDismiss,
        expanded = true,
        footer = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, NightLine)) {
                    Text("Cancel", color = Snow, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (dontAsk && dontAskKey != null) DontAsk.remember(context, dontAskKey)
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (danger) Danger else Gold, contentColor = if (danger) Snow else OnGold),
                ) { Text(confirm, fontWeight = FontWeight.Bold) }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
            if (dontAskKey != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clickable { dontAsk = !dontAsk }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it }, colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = OnGold, uncheckedColor = SnowFaint))
                    Spacer(Modifier.width(4.dp))
                    Text("Don't ask me again", style = MaterialTheme.typography.bodyMedium, color = Snow)
                }
            }
        }
    }
}
