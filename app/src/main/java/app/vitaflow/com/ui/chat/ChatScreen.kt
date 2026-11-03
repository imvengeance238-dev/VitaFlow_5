package app.vitaflow.com.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vitaflow.com.AppContainer
import app.vitaflow.com.R
import app.vitaflow.com.ai.Attachment
import app.vitaflow.com.ai.AttachmentJson
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.data.db.ChangeRecordEntity
import app.vitaflow.com.data.db.ChatMessageEntity
import app.vitaflow.com.data.diffJson
import app.vitaflow.com.ui.components.ConfirmDialog
import app.vitaflow.com.ui.components.GlassDialog
import app.vitaflow.com.ui.components.LocalImage
import app.vitaflow.com.ui.home.Dest
import app.vitaflow.com.ui.home.destFor
import app.vitaflow.com.ui.theme.GlassButton
import app.vitaflow.com.ui.theme.GlassSurface
import app.vitaflow.com.ui.theme.GlassTextField
import app.vitaflow.com.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/** A round button, green. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, danger: Boolean = false) {
    val g = LocalGlass.current
    val fill = if (danger) Brush.horizontalGradient(listOf(g.danger, g.danger)) else Brush.horizontalGradient(listOf(g.accent, g.accent2))
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = g.onAccent, modifier = Modifier.size(24.dp)) }
}

@Composable
fun ChatScreen(account: AccountEntity, container: AppContainer, vm: ChatViewModel, onGo: (Dest) -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val currentId by vm.conversationId.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    var openIds by remember { mutableStateOf<List<Long>?>(null) }
    var deleteChatId by remember { mutableStateOf<Long?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterGrant?.invoke()
        afterGrant = null
    }
    fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterGrant = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.attach(uri) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.attach(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.attach(it) }
    }

    LaunchedEffect(messages.size, vm.streaming) {
        val extra = if (vm.streaming != null) 1 else 0
        val last = messages.size + extra - 1
        if (last >= 0) listState.scrollToItem(last)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = g.bgTop, drawerContentColor = g.textPrimary) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("আপনার chat", color = g.textPrimary, style = MaterialTheme.typography.titleLarge)
                    GlassButton("＋ নতুন chat", {
                        vm.newChat()
                        scope.launch { drawerState.close() }
                    }, Modifier.fillMaxWidth())
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        conversations.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.title.ifBlank { "নতুন chat" },
                                    color = if (c.id == currentId) g.accent else g.textPrimary,
                                    style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                    modifier = Modifier.weight(1f).clickable {
                                        vm.openChat(c.id)
                                        scope.launch { drawerState.close() }
                                    }.padding(vertical = 12.dp)
                                )
                                IconButton(onClick = { deleteChatId = c.id }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "মুছুন", tint = g.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Rounded.Menu, contentDescription = "chat-এর তালিকা", tint = g.textPrimary)
                }
                Text("Max", style = MaterialTheme.typography.titleLarge, color = g.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.newChat() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "নতুন chat", tint = g.textPrimary)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (messages.isEmpty() && vm.streaming == null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Image(
                                painterResource(R.drawable.vita_logo), contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                            )
                            Text(
                                "কী করতে পারি, ${account.name.trim().substringBefore(' ')}?",
                                color = g.textPrimary, style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { m ->
                    Message(m, onOpen = { openIds = it }, onSpeak = { vm.speak(it) })
                }
                vm.streaming?.let { partial ->
                    item { Message(ChatMessageEntity(id = -1, accountId = account.id, role = "MAX", text = partial), {}, {}) }
                }
                if (vm.busy && vm.streaming == null) {
                    item { Text("ভাবছি...", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (vm.attachments.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.attachments.toList().forEach { a ->
                        Box {
                            if (a.kind == "IMAGE") {
                                LocalImage(a.path, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
                            } else {
                                GlassSurface(Modifier.size(width = 120.dp, height = 72.dp), shape = RoundedCornerShape(14.dp)) {
                                    Text(a.name, color = g.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, modifier = Modifier.padding(8.dp))
                                }
                            }
                            IconButton(onClick = { vm.removeAttachment(a) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "সরান", tint = g.danger)
                            }
                        }
                    }
                }
            }

            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    IconButton(onClick = { attachMenu = true }, enabled = !vm.importing) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = "ছবি বা ফাইল", tint = g.textSecondary)
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("গ্যালারি থেকে ছবি") }, onClick = {
                            attachMenu = false
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("ক্যামেরায় ছবি তুলুন") }, onClick = {
                            attachMenu = false
                            val f = File(context.cacheDir, "camera/IMG_${System.currentTimeMillis()}.jpg")
                            f.parentFile?.mkdirs()
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                            cameraUri = uri
                            camera.launch(uri)
                        })
                        DropdownMenuItem(text = { Text("ফাইল (PDF, লেখা)") }, onClick = {
                            attachMenu = false
                            filePicker.launch(arrayOf("application/pdf", "text/*", "application/json"))
                        })
                    }
                }
                GlassTextField(vm.input, { vm.input = it }, "Max-কে লিখুন", modifier = Modifier.weight(1f))
                val hasContent = vm.input.isNotBlank() || vm.attachments.isNotEmpty()
                if (hasContent) {
                    RoundButton(Icons.Rounded.ArrowUpward, "পাঠান", { vm.send(vm.input) })
                } else if (vm.speaking) {
                    RoundButton(Icons.Rounded.Stop, "থামান", { vm.stopSpeaking() })
                } else if (vm.recording) {
                    RoundButton(Icons.Rounded.Stop, "থামান", { vm.toggleMic() }, danger = true)
                } else {
                    RoundButton(Icons.Rounded.Mic, "বলুন", {
                        if (vm.liveState == LiveState.STANDBY) vm.activateNow()
                        else if (vm.liveState == LiveState.OFF) withMic { vm.toggleMic() }
                    })
                }
            }
        }
    }

    openIds?.let { ids ->
        ChangeDialog(ids, container, onDismiss = { openIds = null }, onGo = { openIds = null; onGo(it) })
    }
    deleteChatId?.let { id ->
        ConfirmDialog("এই chat আর এর ছবি-ফাইল মুছে ফেলবেন?", onConfirm = { vm.deleteChat(id) }, onDismiss = { deleteChatId = null })
    }
}

@Composable
private fun Message(m: ChatMessageEntity, onOpen: (List<Long>) -> Unit, onSpeak: (String) -> Unit) {
    val g = LocalGlass.current
    val mine = m.role == "USER"
    val ids = m.changeIds.split(',').mapNotNull { it.trim().toLongOrNull() }
    val files: List<Attachment> = remember(m.attachments) { AttachmentJson.decode(m.attachments) }
    val sources = remember(m.sourcesJson) {
        runCatching {
            val arr = JSONArray(m.sourcesJson ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it).optString("title") to arr.getJSONObject(it).optString("url") }
        }.getOrDefault(emptyList())
    }
    val images = @Composable {
        files.forEach { a ->
            if (a.kind == "IMAGE" && File(a.path).exists()) {
                LocalImage(a.path, Modifier.size(width = 200.dp, height = 160.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                Text("📎 ${a.name}", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (mine) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(18.dp)).background(g.accent.copy(alpha = 0.16f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images()
                if (m.text.isNotBlank()) Text(m.text, color = g.textPrimary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            images()
            Text(m.text, color = g.textPrimary, style = MaterialTheme.typography.bodyLarge)
            sources.forEachIndexed { i, (title, url) ->
                Text("[${i + 1}] $title\n$url", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (m.id > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ids.isNotEmpty()) {
                        Text("খুলুন", color = g.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onOpen(ids) }.padding(vertical = 6.dp))
                    }
                    Text("শুনুন", color = g.textSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { onSpeak(m.text) }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

/** Shows what Max changed: each change, the old and new values for edits, and an undo. */
@Composable
private fun ChangeDialog(ids: List<Long>, container: AppContainer, onDismiss: () -> Unit, onGo: (Dest) -> Unit) {
    val g = LocalGlass.current
    var records by remember { mutableStateOf<List<ChangeRecordEntity>>(emptyList()) }
    var undoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ids) { records = container.db.changeDao().getByIds(ids) }

    GlassDialog("যা যা বদলেছে", onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            records.forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.summary, color = g.textPrimary, style = MaterialTheme.typography.bodyLarge)
                    if (r.action == "UPDATE") {
                        diffJson(r.beforeJson, r.afterJson).forEach {
                            Text(it, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (r.undone) {
                        Text("ফিরিয়ে নেওয়া হয়েছে", color = g.success, style = MaterialTheme.typography.bodyMedium)
                    } else if (r.entity != "SESSION") {
                        Text(
                            "ফিরিয়ে নিন",
                            color = g.danger, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    undoMessage = container.undo.undo(r)
                                    records = container.db.changeDao().getByIds(ids)
                                }
                            }.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
        undoMessage?.let { Text(it, color = g.accent, style = MaterialTheme.typography.bodyMedium) }
        records.firstOrNull()?.let { first ->
            GlassButton("দেখুন", { onGo(destFor(first.entity)) }, Modifier.fillMaxWidth())
        }
    }
}
