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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.ui.theme.AssistantTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AssistantApp
        setContent {
            AssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(settings = app.settings, onSaved = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsStore, onSaved: () -> Unit) {
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }
    var maxSteps by remember { mutableStateOf(settings.agentMaxSteps.toFloat()) }
    var stepsUnlimited by remember { mutableStateOf(settings.agentStepsUnlimited) }
    var screenshotEnabled by remember { mutableStateOf(settings.screenshotEnabled) }
    var visionModel by remember { mutableStateOf(settings.visionModel) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "API 配置（保存后立即生效，无需重启）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("接口地址 Base URL") },
                placeholder = { Text(SettingsStore.DEFAULT_BASE_URL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型") },
                placeholder = { Text(SettingsStore.DEFAULT_MODEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "截屏分析（默认关）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = screenshotEnabled,
                    onCheckedChange = {
                        screenshotEnabled = it
                        settings.screenshotEnabled = it
                    },
                )
                Text("允许 Agent 截取屏幕发给 AI 分析（每次截图都会询问）")
            }
            OutlinedTextField(
                value = visionModel,
                onValueChange = { visionModel = it },
                label = { Text("视觉模型（可选）") },
                placeholder = { Text("如 gpt-4o / glm-4v，须中转站支持") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "注意：DeepSeek 主模型不支持视觉——截屏功能必须在中转站配置视觉模型，" +
                    "否则截图后分析会失败。每次截图都会弹出审批卡且不能设为始终允许。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "高级",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(context, MemoryActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("记忆与人设") }
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(context, ProxyActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI 代理（定时 + 通知触发，默认关）") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "步数无上限",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = stepsUnlimited,
                    onCheckedChange = {
                        stepsUnlimited = it
                        settings.agentStepsUnlimited = it
                    },
                )
            }
            Text(
                if (stepsUnlimited) {
                    "无上限（相同操作连续重复时自动熔断）"
                } else {
                    "单次回复最多执行 ${maxSteps.toInt()} 步（拖动即时生效）"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (stepsUnlimited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = maxSteps,
                onValueChange = { maxSteps = it },
                onValueChangeFinished = { settings.agentMaxSteps = maxSteps.toInt() },
                valueRange = SettingsStore.MIN_MAX_STEPS.toFloat()..SettingsStore.MAX_MAX_STEPS.toFloat(),
                steps = SettingsStore.MAX_MAX_STEPS - SettingsStore.MIN_MAX_STEPS - 1,
                enabled = !stepsUnlimited,
            )
            Button(
                onClick = {
                    settings.baseUrl = baseUrl
                    settings.apiKey = apiKey
                    settings.model = model
                    settings.visionModel = visionModel
                    settings.screenshotEnabled = screenshotEnabled
                    settings.agentMaxSteps = maxSteps.toInt()
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}
