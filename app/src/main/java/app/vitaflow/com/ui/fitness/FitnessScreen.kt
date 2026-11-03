package app.vitaflow.com.ui.fitness

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vitaflow.com.AppContainer
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.ui.components.Segmented
import app.vitaflow.com.ui.diet.DietScreen
import app.vitaflow.com.ui.workout.WorkoutScreen

/** Workout and diet under one tab. */
@Composable
fun FitnessScreen(account: AccountEntity, container: AppContainer, part: Int, onPart: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Segmented(listOf("ওয়ার্কআউট", "ডায়েট"), part, onPart, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
        Box(Modifier.weight(1f)) {
            if (part == 0) WorkoutScreen(account, container) else DietScreen(account, container)
        }
    }
}
