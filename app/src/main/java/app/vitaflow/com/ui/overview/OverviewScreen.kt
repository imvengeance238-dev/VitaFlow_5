package app.vitaflow.com.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vitaflow.com.AppContainer
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.data.db.ProfileEntity
import app.vitaflow.com.max.DietPlanner
import app.vitaflow.com.ui.components.ProgressBar
import app.vitaflow.com.ui.home.Dest
import app.vitaflow.com.ui.home.HomeTab
import app.vitaflow.com.ui.theme.GlassButton
import app.vitaflow.com.ui.theme.GlassChip
import app.vitaflow.com.ui.theme.GlassSurface
import app.vitaflow.com.ui.theme.LocalGlass
import app.vitaflow.com.workout.RunPhase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "শুভ সকাল"
    in 12..15 -> "শুভ দুপুর"
    in 16..18 -> "শুভ বিকাল"
    in 19..21 -> "শুভ সন্ধ্যা"
    else -> "শুভ রাত্রি"
}

@Composable
private fun Tile(title: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val g = LocalGlass.current
    GlassSurface(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = g.textSecondary, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

/** The first screen: a greeting and a small summary of workout, diet, water, tasks and progress. */
@Composable
fun OverviewScreen(account: AccountEntity, container: AppContainer, profile: ProfileEntity, onGo: (Dest) -> Unit) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val tasks by container.taskRepo.observe(account.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val meals by container.mealRepo.observeMeals(account.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val water by container.mealRepo.observeWater(account.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val plans by container.workoutRepo.observePlans(account.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val exercises by container.workoutRepo.observeExercises(account.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by container.progressRepo.observe(account.id).collectAsStateWithLifecycle(initialValue = null)
    val run by container.runner.state.collectAsStateWithLifecycle()

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val eaten = meals.filter { !it.planned && it.loggedAt >= startOfDay }
    val kcal = eaten.sumOf { it.kcal ?: 0 }
    val protein = eaten.sumOf { it.proteinG ?: 0.0 }
    val kcalTarget = DietPlanner.target(profile, null).kcal
    val waterToday = water.filter { it.loggedAt >= startOfDay }.sumOf { it.ml }
    val open = tasks.filter { !it.done }
    val first = account.name.trim().substringBefore(' ')

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${greeting()},", color = g.textSecondary, style = MaterialTheme.typography.bodyLarge)
            Text(first, color = g.textPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(
                if (open.isEmpty()) "আজ কোনো কাজ বাকি নেই" else "আজ ${open.size}টি কাজ বাকি",
                color = g.accent, style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("ওয়ার্কআউট", Modifier.weight(1f), { onGo(Dest(HomeTab.FITNESS, 0)) }) {
                val plan = plans.firstOrNull()
                val list = exercises.filter { it.planId == plan?.id }
                when {
                    run.phase == RunPhase.WORK || run.phase == RunPhase.REST -> {
                        Text("চলছে", color = g.accent, style = MaterialTheme.typography.titleMedium)
                        Text(run.planName, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    plan != null -> {
                        Text(plan.name, color = g.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text("${list.size}টি exercise", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        GlassButton("শুরু করুন", {
                            scope.launch { container.runner.start(account.id, plan, list) }
                            onGo(Dest(HomeTab.FITNESS, 0))
                        }, Modifier.fillMaxWidth(), enabled = list.isNotEmpty())
                    }
                    else -> Text("এখনো নেই", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Tile("ডায়েট", Modifier.weight(1f), { onGo(Dest(HomeTab.FITNESS, 1)) }) {
                Text("$kcal kcal", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
                if (kcalTarget > 0) {
                    ProgressBar(kcal.toFloat() / kcalTarget)
                    Text("লক্ষ্য $kcalTarget", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Text("প্রোটিন ${"%.0f".format(protein)} গ্রাম", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("পানি", Modifier.weight(1f), { onGo(Dest(HomeTab.FITNESS, 1)) }) {
                Text("$waterToday মিলি", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
                ProgressBar(waterToday.toFloat() / profile.waterTargetMl.coerceAtLeast(1))
                GlassChip("+২৫০", false, { scope.launch { container.mealRepo.addWater(account.id, 250) } })
            }
            Tile("টাস্ক", Modifier.weight(1f), { onGo(Dest(HomeTab.TASKS)) }) {
                Text("${open.size}টি বাকি", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
                open.take(2).forEach {
                    Text("• ${it.title}", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }

        Tile("প্রগতি", Modifier.fillMaxWidth(), { onGo(Dest(HomeTab.PROFILE)) }) {
            val w = progress?.weights?.lastOrNull()?.kg ?: profile.weightKg
            val t = profile.targetWeightKg
            if (w != null && t != null) {
                Text("${"%.1f".format(w)} → ${"%.1f".format(t)} কেজি", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
            }
            progress?.let {
                Text(
                    "এই সপ্তাহে ${it.sessions7}টি workout · টানা ${it.streak} দিন",
                    color = g.textSecondary, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Row(Modifier.height(4.dp)) {}
    }
}
