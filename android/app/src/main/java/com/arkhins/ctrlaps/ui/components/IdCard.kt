package com.arkhins.ctrlaps.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.theme.Danger
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Verified
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * What a scan shows, as an event pass: the photo in a ring of the status colour, the name, the role as a chip
 * (red "User" for someone who registered and has no role yet), the team, the account code, and the status across
 * the bottom so it reads at arm's length. [live] adds a pulsing dot and a ticking clock under the photo, so a
 * screenshot of an old check cannot pass for a fresh one at the gate.
 */
@Composable
fun IdCard(v: Verified, live: Boolean = false) {
    val app = LocalApp.current
    val active = v.status == "active"
    val unassigned = v.role == "user"
    val tone = statusTone(v.status)
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NightPanel, Night)))
            .border(1.5.dp, tone.copy(alpha = 0.7f), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The pass's head: the mark, the name of the app, and what this is.
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ctr_logo), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(Config.APP_NAME, style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("EVENT PASS", style = MaterialTheme.typography.labelSmall, color = SnowFaint, letterSpacing = 2.sp)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Gold, Gold.copy(alpha = 0.35f), Color.Transparent))))

        Spacer(Modifier.height(24.dp))
        val photo = app.api.absolute(v.photoUrl)
        Box(
            Modifier
                .size(196.dp)
                .border(3.dp, tone, RoundedCornerShape(32.dp))
                .padding(6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Night),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("No photo", style = MaterialTheme.typography.labelMedium, color = SnowFaint)
        }
        if (live) {
            Spacer(Modifier.height(12.dp))
            LiveCheck(tone)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            v.name ?: "Profile not completed",
            style = MaterialTheme.typography.headlineSmall,
            color = Snow,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .background(if (unassigned) Danger else Gold, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(v.roleLabel.uppercase(), style = MaterialTheme.typography.labelLarge, color = Night, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        if (unassigned) {
            Spacer(Modifier.height(6.dp))
            Text("Registered · no role assigned", style = MaterialTheme.typography.labelMedium, color = Danger)
        }
        if (!v.teamName.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(v.teamName, style = MaterialTheme.typography.bodyLarge, color = SnowSoft)
        }

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .background(Night, RoundedCornerShape(16.dp))
                .border(1.dp, NightLine, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ACCOUNT CODE", style = MaterialTheme.typography.labelSmall, color = SnowFaint, letterSpacing = 2.sp)
            Spacer(Modifier.height(2.dp))
            Text(v.verifyCode, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp), color = Snow)
        }

        Spacer(Modifier.height(20.dp))
        // The status across the bottom, in its colour, to be read from a step away.
        Row(
            Modifier.fillMaxWidth().background(tone).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (active) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = Night, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(v.statusLabel.uppercase(), style = MaterialTheme.typography.titleLarge, color = Night, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            when {
                !active -> "Do not admit — account is ${v.statusLabel.lowercase()}"
                unassigned -> "Signed up only — not given a role at this event"
                else -> "Account in good standing"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!active || unassigned) Danger else SnowFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/** A dot that pulses and the time to the second: this check is happening now. */
@Composable
private fun LiveCheck(tone: Color) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier.background(Night, RoundedCornerShape(999.dp)).border(1.dp, NightLine, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(pulse).background(tone, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            "Checked " + now.format(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)).lowercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = SnowSoft,
        )
    }
}
