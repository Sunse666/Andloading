package com.andloading.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.ui.chat.ChatScreen
import com.andloading.assistant.ui.chat.ChatViewModel
import com.andloading.assistant.ui.theme.AssistantTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AssistantApp
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(app.agentSession)
            }
        }
        setContent {
            AssistantTheme {
                val vm: ChatViewModel = viewModel(factory = factory)
                ChatScreen(
                    vm = vm,
                    onOpenSettings = {
                        startActivity(android.content.Intent(this, com.andloading.assistant.ui.settings.SettingsActivity::class.java))
                    },
                    onOpenPermissions = {
                        startActivity(android.content.Intent(this, PermissionActivity::class.java))
                    },
                )
            }
        }
    }
}
