package com.andloading.assistant.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andloading.assistant.data.model.TaskEntity
import com.andloading.assistant.data.model.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThreadListScreen(
    tasks: List<TaskEntity>,
    currentTaskId: String?,
    onSelect: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对话列表") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("＋ 新建对话", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            onNewConversation()
                            onDismiss()
                        },
                    )
                    HorizontalDivider()
                }
                if (tasks.isEmpty()) {
                    item { Text("还没有对话，发送一条消息开始吧", modifier = Modifier.padding(16.dp)) }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        ListItem(
                            headlineContent = { Text(task.title, maxLines = 1) },
                            supportingContent = {
                                Text("${statusLabel(task.status)} · ${formatTime(task.updatedAt)}")
                            },
                            trailingContent = {
                                if (task.id == currentTaskId) {
                                    Icon(Icons.Default.Check, contentDescription = "当前")
                                } else {
                                    IconButton(onClick = { onDelete(task.id) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                onSelect(task.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

internal fun statusLabel(status: String): String = when (status) {
    TaskStatus.RUNNING -> "运行中"
    TaskStatus.WAITING_APPROVAL -> "待确认"
    TaskStatus.DONE -> "已完成"
    TaskStatus.FAILED -> "失败"
    else -> status
}

internal fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
