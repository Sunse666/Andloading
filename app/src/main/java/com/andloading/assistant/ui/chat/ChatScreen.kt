package com.andloading.assistant.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andloading.assistant.agent.ScreenshotApprovalScope
import com.andloading.assistant.data.model.MessageEntity
import com.andloading.assistant.data.model.Role
import com.andloading.assistant.data.model.ToolCall
import io.noties.markwon.Markwon
import android.text.method.LinkMovementMethod
import android.widget.TextView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val activeTool by vm.activeTool.collectAsStateWithLifecycle()
    val pendingApproval by vm.pendingApproval.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val currentTaskId by vm.currentTaskId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showThreads by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, streaming?.length, activeTool) {
        val count = messages.size + if (streaming != null) 1 else 0
        if (count > 0) listState.scrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Andloading") },
                actions = {
                    IconButton(onClick = { showThreads = true }) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "对话列表")
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Filled.Shield, contentDescription = "权限引导")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
                streaming?.let { partial ->
                    item(key = "streaming") { StreamingBubble(partial) }
                }
            }
            pendingApproval?.let { call ->
                ApprovalCard(
                    call = call,
                    onApprove = { vm.approve(always = false) },
                    onAlwaysAllow = { vm.approve(always = true) },
                    onApproveThisRun = { vm.approveScreenshot(ScreenshotApprovalScope.THIS_RUN) },
                    onApproveThisConversation = {
                        vm.approveScreenshot(ScreenshotApprovalScope.THIS_CONVERSATION)
                    },
                    onDeny = vm::deny,
                )
            }
            activeTool?.let { name ->
                Text(
                    "⚙ 正在执行 $name…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            InputRow(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    vm.send(input)
                    input = ""
                },
                onStop = vm::stop,
                busy = streaming != null,
            )
        }
    }

    if (showThreads) {
        ThreadListScreen(
            tasks = tasks,
            currentTaskId = currentTaskId,
            onSelect = vm::switchThread,
            onNewConversation = vm::newConversation,
            onDelete = vm::deleteThread,
            onDismiss = { showThreads = false },
        )
    }
}

@Composable
private fun ApprovalCard(
    call: ToolCall,
    onApprove: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onApproveThisRun: () -> Unit,
    onApproveThisConversation: () -> Unit,
    onDeny: () -> Unit,
) {
    val isScreenshot = call.name == "take_screenshot"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "请求执行工具：${call.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                call.argumentsJson.take(200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (isScreenshot) {
                Text(
                    "将截取当前屏幕并发送给 AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (isScreenshot) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = onApprove, modifier = Modifier.weight(1f)) {
                        Text("允许一次")
                    }
                    FilledTonalButton(onClick = onApproveThisRun, modifier = Modifier.weight(1f)) {
                        Text("本轮始终")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onApproveThisConversation,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("本对话始终")
                    }
                    OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                        Text("拒绝")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = onApprove) { Text("允许一次") }
                    FilledTonalButton(onClick = onAlwaysAllow) { Text("始终允许") }
                    OutlinedButton(onClick = onDeny) { Text("拒绝") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageEntity) {    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (isUser) {
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                MarkdownText(
                    markdown = msg.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(partial: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = partial,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor.toArgb())
            markwon.setMarkdown(tv, markdown)
        },
    )
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("给助手发消息") },
            maxLines = 4,
        )
        Spacer(Modifier.padding(start = 8.dp))
        FilledIconButton(
            onClick = if (busy) onStop else onSend,
            enabled = busy || value.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = if (busy) "停止" else "发送",
            )
        }
    }
}
