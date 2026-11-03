package app.vitaflow.com.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.vitaflow.com.AppContainer
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.data.db.ProfileEntity
import app.vitaflow.com.ui.auth.AuthViewModel
import app.vitaflow.com.ui.chat.ChatScreen
import app.vitaflow.com.ui.chat.ChatViewModel
import app.vitaflow.com.ui.chat.LiveState
import app.vitaflow.com.ui.fitness.FitnessScreen
import app.vitaflow.com.ui.onboarding.OnboardingScreen
import app.vitaflow.com.ui.overview.OverviewScreen
import app.vitaflow.com.ui.profile.ProfileTabScreen
import app.vitaflow.com.ui.settings.SettingsScreen
import app.vitaflow.com.ui.tasks.TasksScreen
import app.vitaflow.com.ui.theme.LocalGlass
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

enum class HomeTab(val label: String, val icon: ImageVector) {
    HOME("হোম", Icons.Rounded.Home),
    FITNESS("ফিটনেস", Icons.Rounded.FitnessCenter),
    TASKS("টাস্ক", Icons.Rounded.Checklist),
    CHAT("Max", Icons.Rounded.AutoAwesome),
    PROFILE("প্রোফাইল", Icons.Rounded.Person)
}

/** A place in the app. [part] picks the sub-tab inside Fitness (0 workout, 1 diet). */
data class Dest(val tab: HomeTab, val part: Int = 0)

/** Where to go to see the thing a change record is about. */
fun destFor(entity: String): Dest = when (entity) {
    "TASK" -> Dest(HomeTab.TASKS)
    "WORKOUT", "EXERCISE", "SESSION" -> Dest(HomeTab.FITNESS, 0)
    "MEAL", "WATER" -> Dest(HomeTab.FITNESS, 1)
    "WEIGHT" -> Dest(HomeTab.PROFILE)
    else -> Dest(HomeTab.CHAT)
}

@Composable
fun HomeScreen(account: AccountEntity, container: AppContainer, authVm: AuthViewModel) {
    val state by container.profileRepo.observe(account.id).map { it to true }
        .collectAsStateWithLifecycle(initialValue = null to false)
    val profile = state.first
    var editing by rememberSaveable { mutableStateOf(false) }

    when {
        !state.second -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalGlass.current.accent)
        }
        profile?.onboardingDone != true || editing -> OnboardingScreen(
            account = account, container = container,
            edit = profile?.onboardingDone == true,
            onDone = { editing = false },
            onCancel = if (profile?.onboardingDone == true) ({ editing = false }) else null
        )
        else -> MainShell(account, container, authVm, profile, onEditProfile = { editing = true })
    }
}

@Composable
private fun MainShell(account: AccountEntity, container: AppContainer, authVm: AuthViewModel, profile: ProfileEntity, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatVm: ChatViewModel = viewModel(
        key = "chat-${account.id}",
        factory = viewModelFactory { initializer { ChatViewModel(container, account) } }
    )
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var fitnessPart by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val autoLive by container.settingsStore.autoLive.collectAsStateWithLifecycle(initialValue = true)

    fun go(d: Dest) {
        tab = d.tab
        fitnessPart = d.part
        showSettings = false
    }

    // The microphone is asked for once; after that Max listens whenever the app is open.
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    LaunchedEffect(Unit) { if (!micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(chatVm) {
        chatVm.onWake = { go(Dest(HomeTab.CHAT)) }
        onDispose { chatVm.onWake = null }
    }
    LaunchedEffect(autoLive, micGranted) {
        if (autoLive && micGranted) chatVm.startLive() else if (!autoLive) chatVm.stopLive()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { if (autoLive && micGranted) chatVm.startLive() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        chatVm.stopLive()
        chatVm.onLeaveChat()
    }

    Column(Modifier.fillMaxSize()) {
        LivePill(chatVm)
        Box(Modifier.weight(1f)) {
            if (showSettings) {
                SettingsScreen(
                    account, container,
                    onBack = { showSettings = false },
                    onLogout = authVm::logout,
                    onClearChat = { scope.launch { container.chatRepo.clearAll(account.id) } }
                )
            } else {
                when (tab) {
                    HomeTab.HOME -> OverviewScreen(account, container, profile, onGo = ::go)
                    HomeTab.FITNESS -> FitnessScreen(account, container, fitnessPart, onPart = { fitnessPart = it })
                    HomeTab.TASKS -> TasksScreen(account, container.taskRepo)
                    HomeTab.CHAT -> ChatScreen(account, container, chatVm, onGo = ::go)
                    HomeTab.PROFILE -> ProfileTabScreen(account, container, profile, onEdit = onEditProfile, onSettings = { showSettings = true })
                }
            }
        }
        if (!showSettings) NavBar(tab, onSelect = { go(Dest(it)) })
    }
}

/** A thin strip at the top: shows that Max is listening, and any voice problem. Tap while waiting to start talking at once. */
@Composable
private fun LivePill(vm: ChatViewModel) {
    val g = LocalGlass.current
    val live = vm.liveState
    val notice = vm.notice
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (live != LiveState.OFF) {
            val (text, color) = when (live) {
                LiveState.STANDBY -> "\"Hey Max\" বলুন" to g.textSecondary
                LiveState.LISTENING -> "শুনছি" to g.accent
                LiveState.THINKING -> "ভাবছি" to g.accent
                LiveState.SPEAKING -> "বলছি" to g.accent
                LiveState.OFF -> "" to g.textSecondary
            }
            Row(
                Modifier.padding(top = 6.dp)
                    .background(g.glassFill, CircleShape)
                    .border(1.dp, g.borderBottom, CircleShape)
                    .clickable(enabled = live == LiveState.STANDBY) { vm.activateNow() }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Text(text, color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (notice != null) {
            Text(
                notice, color = g.danger, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { vm.clearNotice() }
            )
        }
    }
}

@Composable
private fun NavBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().background(g.glassFill).border(1.dp, g.borderBottom).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HomeTab.entries.forEach { t ->
            val on = t == selected
            val color = if (on) g.accent else g.textSecondary
            Column(
                Modifier.weight(1f).clickable { onSelect(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.background(if (on) g.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = color, modifier = Modifier.size(24.dp))
                }
                Text(t.label, color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
