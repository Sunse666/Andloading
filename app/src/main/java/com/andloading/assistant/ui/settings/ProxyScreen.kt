package com.andloading.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.data.model.AgentLogEntity
import com.andloading.assistant.data.model.AgentRunStatus
import com.andloading.assistant.data.model.ProxyTrigger
import com.andloading.assistant.ui.theme.AssistantTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class ProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProxyManageScreen(onClose = { finish() })
                }
            }
        }
    }
}

fun isNotificationListenerEnabled(context: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

private fun launchableApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { it.packageName to pm.getApplicationLabel(it).toString() }
        .sortedBy { it.second.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyManageScreen(onClose: () -> Unit) {
    val app = AssistantApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val logs by app.database.agentLogDao().observeRecent().collectAsState(initial = emptyList())
    var proxyEnabled by remember { mutableStateOf(app.settings.autoEnabled) }
    var interval by remember { mutableStateOf(app.settings.proxyIntervalMinutes.toFloat()) }
    var realtimeEnabled by remember { mutableStateOf(app.settings.proxyRealtimeEnabled) }
    var realtimeSeconds by remember { mutableStateOf(app.settings.proxyRealtimeSeconds.toFloat()) }
    var proxyScreenshot by remember { mutableStateOf(app.settings.proxyScreenshot) }
    var whitelist by remember { mutableStateOf(app.settings.proxyWhitelist) }
    var listenerGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val apps = remember { launchableApps(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 代理") },
                navigationIcon = { TextButton(onClick = onClose) { Text("返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "AI 代理总开关",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = { on ->
                                    app.settings.autoEnabled = on
                                    proxyEnabled = on
                                },
                            )
                        }
                        Text(
                            "开启后，白名单应用的新通知和定时巡检会触发代理任务：AI 自主判断是否有" +
                                "需要处理的消息，需要回复时模拟你本人打开应用、输入并发送。" +
                                "默认关闭；白名单为空时不触发。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!listenerGranted) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("授予「通知使用权」（系统设置）") }
                        } else {
                            Text(
                                "✓ 通知使用权已授予",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("定时巡检间隔：${interval.toInt()} 分钟", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = interval,
                            onValueChange = { interval = it },
                            onValueChangeFinished = {
                                app.settings.proxyIntervalMinutes = interval.toInt()
                            },
                            valueRange = SettingsStore.MIN_PROXY_INTERVAL.toFloat()
                                ..SettingsStore.MAX_PROXY_INTERVAL.toFloat(),
                            steps = SettingsStore.MAX_PROXY_INTERVAL - SettingsStore.MIN_PROXY_INTERVAL - 1,
                        )
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("实时模式", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "前台每几秒观测一次白名单应用的屏幕，内容有变化才调用 AI 实时处理；" +
                                        "开启后替代定时巡检，屏幕不变不耗 token，但更费电。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = realtimeEnabled,
                                onCheckedChange = {
                                    app.settings.proxyRealtimeEnabled = it
                                    realtimeEnabled = it
                                },
                            )
                        }
                        if (realtimeEnabled) {
                            Text(
                                "观测间隔：${realtimeSeconds.toInt()} 秒",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Slider(
                                value = realtimeSeconds,
                                onValueChange = { realtimeSeconds = it },
                                onValueChangeFinished = {
                                    app.settings.proxyRealtimeSeconds = realtimeSeconds.toInt()
                                },
                                valueRange = SettingsStore.MIN_PROXY_REALTIME_SECONDS.toFloat()
                                    ..SettingsStore.MAX_PROXY_REALTIME_SECONDS.toFloat(),
                                steps = SettingsStore.MAX_PROXY_REALTIME_SECONDS -
                                    SettingsStore.MIN_PROXY_REALTIME_SECONDS - 1,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("允许代理截图", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "read_screen 读不到内容的应用（如游戏/自绘界面）用截图兜底。" +
                                    "需先在设置开启截屏分析总开关；截图只在本轮代理任务内授权。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = proxyScreenshot,
                            onCheckedChange = { on ->
                                app.settings.proxyScreenshot = on
                                proxyScreenshot = on
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    "应用白名单（${whitelist.size}）",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(apps, key = { it.first }) { (pkg, label) ->
                val checked = pkg in whitelist
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                val next = if (on) whitelist + pkg else whitelist - pkg
                                app.settings.proxyWhitelist = next
                                whitelist = next
                            },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "代理日志（最近 50 条）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch { app.database.agentLogDao().clearAll() }
                    }) { Text("清空") }
                }
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(logs, key = { it.id }) { log -> AgentLogRow(log) }
        }
    }
}

@Composable
private fun AgentLogRow(log: AgentLogEntity) {
    val time = remember(log.createdAt) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt))
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (log.status == AgentRunStatus.FAILED) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusLabel(log.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (log.status == AgentRunStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    "  ${triggerLabel(log.trigger)} · ${log.sourcePackage.ifEmpty { "全局" }} · $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (log.triggerSummary.isNotBlank()) {
                Text(
                    log.triggerSummary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            if (log.detail.isNotBlank()) {
                Text(
                    log.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    AgentRunStatus.SUCCESS -> "执行成功"
    AgentRunStatus.FAILED -> "执行失败"
    AgentRunStatus.COOLDOWN -> "冷却跳过"
    AgentRunStatus.DAILY_LIMIT -> "达到每日上限"
    AgentRunStatus.SKIPPED_BUSY -> "忙线跳过"
    else -> status
}

private fun triggerLabel(trigger: String): String = when (trigger) {
    ProxyTrigger.SCHEDULED -> "定时巡检"
    ProxyTrigger.NOTIFICATION -> "通知"
    ProxyTrigger.REALTIME -> "实时"
    else -> trigger
}
