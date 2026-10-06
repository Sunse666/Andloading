package com.andloading.assistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.agent.ChatMessage
import com.andloading.assistant.agent.LLMClient
import com.andloading.assistant.agent.PromptBuilder
import com.andloading.assistant.data.model.MemoryEntity
import com.andloading.assistant.data.model.MemorySource
import com.andloading.assistant.data.model.Role
import com.andloading.assistant.data.model.TaskEntity
import com.andloading.assistant.ui.theme.AssistantTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MemoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MemoryManageScreen(onClose = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManageScreen(onClose: () -> Unit) {
    val app = AssistantApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val memories by app.database.memoryDao().observeAll().collectAsState(initial = emptyList())
    val enabledCount = memories.count { it.enabled }
    var persona by remember { mutableStateOf(app.settings.personaPrompt) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSummarizeDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆与人设") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("人设", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "描述回复风格、称呼、语气等，每轮对话都会生效；留空则不启用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = persona,
                        onValueChange = { persona = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("例：回复简短口语化，直接叫我名字，不用敬语。") },
                    )
                    Button(
                        onClick = {
                            app.settings.personaPrompt = persona
                            Toast.makeText(context, "人设已保存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存人设") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("操作记忆", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabledCount > PromptBuilder.MEMORY_INDEX_LIMIT) {
                            "启用 ${enabledCount} 条，超出索引上限，仅注入最近 ${PromptBuilder.MEMORY_INDEX_LIMIT} 条"
                        } else {
                            "已启用 $enabledCount 条（索引注入上限 ${PromptBuilder.MEMORY_INDEX_LIMIT} 条，正文按需读取）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showSummarizeDialog = true }) {
                            Text("从对话总结")
                        }
                        OutlinedButton(onClick = { showAddDialog = true }) {
                            Text("手工添加")
                        }
                    }
                }
            }
            if (memories.isEmpty()) {
                item {
                    Text(
                        "还没有记忆。执行过任务后，用「从对话总结」把可复用的操作路径沉淀下来。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(memories, key = { it.id }) { memory ->
                MemoryRow(
                    memory = memory,
                    onToggle = { enabled ->
                        scope.launch {
                            app.database.memoryDao().update(memory.copy(enabled = enabled))
                        }
                    },
                    onDelete = {
                        scope.launch { app.database.memoryDao().delete(memory) }
                    },
                )
            }
            if (memories.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清空全部记忆") }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content ->
                scope.launch {
                    app.database.memoryDao().insert(
                        MemoryEntity(title = title, content = content, source = MemorySource.MANUAL),
                    )
                }
                showAddDialog = false
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部记忆") },
            text = { Text("将删除全部 ${memories.size} 条记忆（含禁用的），不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { app.database.memoryDao().deleteAll() }
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }

    if (showSummarizeDialog) {
        SummarizeConversationDialog(
            onDismiss = { showSummarizeDialog = false },
        )
    }
}

@Composable
private fun MemoryRow(
    memory: MemoryEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    memory.title.ifBlank { memory.content.take(20) },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    memory.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (memory.source == MemorySource.MANUAL) "手工添加" else "总结自：${memory.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = memory.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手工添加记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text("标题：一句话概括，如「B站搜索路径」") },
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    minLines = 3,
                    placeholder = {
                        Text("完整操作细节，可包含包名、Activity、深链 scheme、具体点击步骤等")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), content.trim()) },
                enabled = content.trim().isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SummarizeConversationDialog(
    onDismiss: () -> Unit,
) {
    val app = AssistantApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tasks by app.database.taskDao().observeTasks().collectAsState(initial = emptyList())
    var candidates by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var checked by remember { mutableStateOf(setOf<Int>()) }
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Text(
                when {
                    loading -> "正在总结…"
                    candidates == null -> "选择要总结的对话"
                    else -> "勾选要保存的记忆"
                },
            )
        },
        text = {
            when {
                loading -> Text("AI 正在提炼该对话中的可复用操作路径…")
                candidates == null -> {
                    if (tasks.isEmpty()) {
                        Text("还没有历史对话。")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(tasks, key = { it.id }) { task ->
                                TextButton(
                                    onClick = {
                                        selectedTask = task
                                        loading = true
                                        scope.launch {
                                            candidates = summarize(task)
                                            checked = (0 until (candidates?.size ?: 0)).toSet()
                                            loading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "${task.title}（${formatDate(task.createdAt)}）",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (candidates!!.isEmpty()) {
                        Text("本次总结没有提炼出可复用的记忆条目。")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(candidates!!) { index, item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = index in checked,
                                        onCheckedChange = { on ->
                                            checked = if (on) checked + index else checked - index
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.first, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            item.second,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                loading -> Unit
                candidates == null -> Unit
                else -> TextButton(
                    onClick = {
                        val picked = checked.map { candidates!![it] }
                        if (picked.isNotEmpty()) {
                            scope.launch {
                                app.database.memoryDao().insertAll(
                                    picked.map { (title, content) ->
                                        MemoryEntity(
                                            title = title,
                                            content = content,
                                            source = selectedTask?.title ?: "对话总结",
                                        )
                                    },
                                )
                            }
                            Toast.makeText(context, "已保存 ${picked.size} 条记忆", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    enabled = candidates?.isNotEmpty() == true && checked.isNotEmpty(),
                ) { Text("保存勾选项") }
            }
        },
        dismissButton = {
            if (!loading) {
                TextButton(onClick = onDismiss) {
                    Text(if (candidates == null) "取消" else "返回")
                }
            }
        },
    )
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    itemContent: @Composable (Int, T) -> Unit,
) {
    items(items.size) { index -> itemContent(index, items[index]) }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

private suspend fun summarize(task: TaskEntity): List<Pair<String, String>> {
    val app = AssistantApp.instance
    val recent = app.database.chatDao().getRecent(task.id, 60)
    if (recent.isEmpty()) return emptyList()
    val transcript = recent.reversed().joinToString("\n") { m ->
        val who = when (m.role) {
            Role.USER -> "用户"
            Role.ASSISTANT -> "助手"
            else -> "工具(${m.toolName ?: "unknown"})"
        }
        "$who: ${m.content.take(500)}"
    }
    val prompt =
        "以下是一段人机协作完成手机任务的对话记录。请提炼其中【可复用的操作经验】，" +
            "例如某应用的有效操作路径（包名、Activity、深链 scheme）、按钮位置技巧、" +
            "点击顺序、失败后的替代方案等。\n" +
            "要求：\n" +
            "1. 每条一行，格式固定为：【标题】正文。标题不超过 16 字、概括这条经验的适用场景；" +
            "正文写完整细节（包名、Activity、深链、点击步骤等），不限一句话，一般不超过 300 字；\n" +
            "2. 只提炼下次执行类似任务时用得上的内容，不要复述任务结果；\n" +
            "3. 没有值得沉淀的内容时只回复：无\n\n" +
            "对话记录（会话：${task.title}）：\n$transcript"
    return try {
        val answer = LLMClient(app.settings)
            .chat(listOf(ChatMessage.text(Role.USER, prompt)))
            .message
            .contentText()
        answer.lines()
            .map { it.trim().removePrefix("- ").removePrefix("· ") }
            .map { it.replace(Regex("^\\d+[.、)]\\s*"), "") }
            .filter { it.isNotBlank() && it != "无" }
            .distinct()
            .take(10)
            .map { line ->
                val match = TITLE_CONTENT_REGEX.find(line)
                if (match != null) {
                    match.groupValues[1].trim() to match.groupValues[2].trim()
                } else {
                    line.take(12) to line
                }
            }
            .filter { (_, content) -> content.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}

private val TITLE_CONTENT_REGEX = Regex("^【(.+?)】\\s*(.+)$")
