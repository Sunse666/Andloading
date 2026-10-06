package com.andloading.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.andloading.assistant.accessibility.AssistantAccessibilityService
import com.andloading.assistant.service.FloatingWindowService
import com.andloading.assistant.shizuku.ShizukuGate
import com.andloading.assistant.shizuku.ShizukuState
import com.andloading.assistant.ui.settings.SettingsActivity
import com.andloading.assistant.ui.theme.AssistantTheme

class PermissionActivity : ComponentActivity() {

    private companion object {
        const val SHIZUKU_REQUEST_CODE = 42
    }

    private var notificationGranted by mutableStateOf(false)
    private var shizukuState by mutableStateOf(ShizukuState.NOT_INSTALLED)
    private var accessibilityGranted by mutableStateOf(false)
    private var overlayGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)
    private var listenerGranted by mutableStateOf(false)
    private var batteryExempted by mutableStateOf(false)
    private var removeShizukuResultListener: (() -> Unit)? = null

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationGranted = granted || checkNotificationGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeShizukuResultListener = ShizukuGate.addOnRequestResultListener { code, result ->
            if (code == SHIZUKU_REQUEST_CODE) {
                shizukuState =
                    if (result == PackageManager.PERMISSION_GRANTED) ShizukuState.READY
                    else ShizukuState.NOT_AUTHORIZED
            }
        }
        setContent {
            AssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionScreen(
                        notificationGranted = notificationGranted,
                        shizukuState = shizukuState,
                        accessibilityGranted = accessibilityGranted,
                        overlayGranted = overlayGranted,
                        overlayRunning = overlayRunning,
                        listenerGranted = listenerGranted,
                        batteryExempted = batteryExempted,
                        onRequestNotification = { requestNotification() },
                        onShizukuAction = { shizukuAction() },
                        onAccessibilityAction = { openAccessibilitySettings() },
                        onOverlayAction = { overlayAction() },
                        onListenerAction = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onBatteryAction = { requestBatteryExemption() },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuGate.attach { shizukuState = ShizukuGate.state(this) }
        refreshStates()
    }

    override fun onStop() {
        ShizukuGate.detach()
        super.onStop()
    }

    override fun onDestroy() {
        removeShizukuResultListener?.invoke()
        removeShizukuResultListener = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
    }

    private fun refreshStates() {
        notificationGranted = checkNotificationGranted()
        shizukuState = ShizukuGate.state(this)
        accessibilityGranted = AssistantAccessibilityService.instance != null
        overlayGranted = Settings.canDrawOverlays(this)
        overlayRunning = FloatingWindowService.isRunning
        listenerGranted = com.andloading.assistant.ui.settings.isNotificationListenerEnabled(this)
        batteryExempted = batteryExemptionGranted()
    }

    private fun batteryExemptionGranted(): Boolean {
        val pm = getSystemService(android.os.PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        if (batteryExemptionGranted()) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun checkNotificationGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun shizukuAction() {
        when (shizukuState) {
            ShizukuState.NOT_INSTALLED -> ShizukuGate.openInstallPage(this)
            ShizukuState.NOT_RUNNING -> {
                if (!ShizukuGate.launchShizukuApp(this)) {
                    ShizukuGate.openInstallPage(this)
                }
            }
            ShizukuState.NOT_AUTHORIZED -> ShizukuGate.requestPermission(SHIZUKU_REQUEST_CODE)
            ShizukuState.READY -> Unit
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun overlayAction() {
        when {
            !Settings.canDrawOverlays(this) -> startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            FloatingWindowService.isRunning -> FloatingWindowService.stop(this)
            else -> FloatingWindowService.start(this)
        }
        overlayRunning = FloatingWindowService.isRunning
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionScreen(
    notificationGranted: Boolean,
    shizukuState: ShizukuState,
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    listenerGranted: Boolean,
    batteryExempted: Boolean,
    onRequestNotification: () -> Unit,
    onShizukuAction: () -> Unit,
    onAccessibilityAction: () -> Unit,
    onOverlayAction: () -> Unit,
    onListenerAction: () -> Unit,
    onBatteryAction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("权限引导") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionCard(
                title = "通知权限",
                granted = notificationGranted,
                description = "保证前台服务通知常驻、对话完成可提醒。",
                buttonLabel = "授予",
                onAction = onRequestNotification,
            )
            PermissionCard(
                title = "Shizuku（执行跨应用操作）",
                granted = shizukuState == ShizukuState.READY,
                description = when (shizukuState) {
                    ShizukuState.NOT_INSTALLED -> "未安装。需要先安装 Shizuku 宿主 App（应用商店或官网）。"
                    ShizukuState.NOT_RUNNING -> "已安装但未运行。打开 Shizuku 后通过「无线调试」或 adb 激活，然后回到本页。"
                    ShizukuState.NOT_AUTHORIZED -> "Shizuku 运行中但尚未授权。点下方按钮拉起授权框。"
                    ShizukuState.READY -> "就绪。可以执行跨应用操作。"
                },
                buttonLabel = when (shizukuState) {
                    ShizukuState.NOT_INSTALLED -> "安装 Shizuku"
                    ShizukuState.NOT_RUNNING -> "打开 Shizuku"
                    ShizukuState.NOT_AUTHORIZED -> "授权"
                    ShizukuState.READY -> "已就绪"
                },
                onAction = onShizukuAction,
            )
            PermissionCard(
                title = "无障碍（读屏与界面操控）",
                granted = accessibilityGranted,
                description = "开启后 AI 可读取屏幕元素并执行点击/输入/滑动，用于完成「帮我操作某个 App」类任务。写操作均需逐次确认。",
                buttonLabel = "去系统设置开启",
                onAction = onAccessibilityAction,
            )
            PermissionCard(
                title = "悬浮窗快捷面板（可选）",
                granted = overlayRunning,
                description = when {
                    !overlayGranted -> "未授权。授权后可在任意应用上方呼出快捷审批与输入面板。"
                    !overlayRunning -> "已授权。开启悬浮球后可在任何界面快速审批工具调用、收发消息。"
                    else -> "悬浮窗运行中。可通过面板「关闭悬浮窗」或下方按钮停止。"
                },
                buttonLabel = when {
                    !overlayGranted -> "去授权悬浮窗"
                    !overlayRunning -> "开启悬浮窗"
                    else -> "关闭悬浮窗"
                },
                onAction = onOverlayAction,
                showButtonWhenGranted = true,
            )
            PermissionCard(
                title = "通知读取（自动执行，可选）",
                granted = listenerGranted,
                description = "授予后，自动执行规则可监听指定应用的新通知（如收到消息），并模拟你本人操作回复。" +
                    "总开关默认关闭，需在设置 → 自动执行中开启并逐条启用规则。",
                buttonLabel = "去系统设置开启",
                onAction = onListenerAction,
            )
            PermissionCard(
                title = "常驻保活（可选，推荐）",
                granted = batteryExempted,
                description = "把本应用加入电池优化白名单，清理后台或划掉最近任务后悬浮球和权限能更快自动恢复。" +
                    "另外建议：在最近任务里长按 Andloading 卡片选择「锁定」；ROM 的自启动管理里允许后台运行。",
                buttonLabel = if (batteryExempted) "已加白名单" else "申请电池优化白名单",
                onAction = onBatteryAction,
                showButtonWhenGranted = true,
            )
            if (shizukuState == ShizukuState.NOT_RUNNING) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")),
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("激活方法见 Shizuku 官网说明")
                }
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("API 配置（中转站地址 / Key / 模型）")
            }
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}")),
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text("打开系统应用设置")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    buttonLabel: String,
    onAction: () -> Unit,
    showButtonWhenGranted: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) " ✓ 已授权" else " · 未授权",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted || showButtonWhenGranted) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
