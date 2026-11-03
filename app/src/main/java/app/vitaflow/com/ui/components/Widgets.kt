package app.vitaflow.com.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.vitaflow.com.ui.theme.GlassChip
import app.vitaflow.com.ui.theme.LocalGlass

/** A thin progress track, green. [progress] is 0..1. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(8.dp).clip(shape).background(g.borderBottom)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).clip(shape)
                .background(Brush.horizontalGradient(listOf(g.accent, g.accent2)))
        )
    }
}

/** Chips that wrap onto the next line. Pick one ([selected] has one entry) or many. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) -> GlassChip(label, key in selected, { onToggle(key) }) }
    }
}

/** Tabs like "ওয়ার্কআউট | ডায়েট". */
@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(g.glassFill).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) g.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) g.accent else g.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
