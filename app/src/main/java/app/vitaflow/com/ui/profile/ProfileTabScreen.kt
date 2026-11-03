package app.vitaflow.com.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vitaflow.com.AppContainer
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.data.db.ProfileEntity
import app.vitaflow.com.max.RoadmapBuilder
import app.vitaflow.com.ui.me.ProgressScreen
import app.vitaflow.com.ui.theme.GlassButton
import app.vitaflow.com.ui.theme.GlassSurface
import app.vitaflow.com.ui.theme.LocalGlass

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val g = LocalGlass.current
    GlassSurface(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
            Text(label, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ProfileTabScreen(account: AccountEntity, container: AppContainer, profile: ProfileEntity, onEdit: () -> Unit, onSettings: () -> Unit) {
    val g = LocalGlass.current
    val bmi = RoadmapBuilder.bmi(profile)
    val road = RoadmapBuilder.build(profile, profile.ageYears)
    val goal = when (profile.goal) {
        "LOSE_FAT" -> "ওজন কমানো"
        "GAIN_WEIGHT" -> "ওজন বাড়ানো"
        "BUILD_MUSCLE" -> "পেশি বানানো"
        else -> if (profile.goalNote.isNotBlank()) profile.goalNote else "ফিট থাকা"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("প্রোফাইল", style = MaterialTheme.typography.headlineMedium, color = g.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "সেটিংস", tint = g.textPrimary, modifier = Modifier.size(26.dp))
            }
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).background(g.accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(account.name.trim().take(1), color = g.accent, style = MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text(account.name, color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
                    Text(goal, color = g.accent, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(profile.weightKg?.let { "%.1f".format(it) } ?: "—", "ওজন (কেজি)", Modifier.weight(1f))
            Stat(profile.targetWeightKg?.let { "%.1f".format(it) } ?: "—", "লক্ষ্য (কেজি)", Modifier.weight(1f))
            Stat(bmi?.let { "%.1f".format(it) } ?: "—", "BMI", Modifier.weight(1f))
        }
        GlassButton("তথ্য বদলান", onEdit, Modifier.fillMaxWidth())

        Text("রোডম্যাপ", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
        road.phases.forEach { ph ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ph.title, color = g.accent, style = MaterialTheme.typography.titleMedium)
                    Text(ph.text, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("প্রগতি", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
        ProgressScreen(account, container)
    }
}
