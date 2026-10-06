package com.andloading.assistant.data

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            var v = value.trim()
            if (v.isEmpty()) {
                prefs.edit().remove(KEY_BASE_URL).apply()
                return
            }
            if (!v.endsWith("/")) v += "/"
            prefs.edit().putString(KEY_BASE_URL, v).apply()
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            val v = value.trim()
            if (v.isEmpty()) {
                prefs.edit().remove(KEY_MODEL).apply()
                return
            }
            prefs.edit().putString(KEY_MODEL, v).apply()
        }

    var showThinking: Boolean
        get() = prefs.getBoolean(KEY_SHOW_THINKING, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_THINKING, value).apply()

    var autoEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    var proxyWhitelist: Set<String>
        get() = prefs.getStringSet(KEY_PROXY_WHITELIST, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PROXY_WHITELIST, value).apply()

    var proxyIntervalMinutes: Int
        get() = prefs.getInt(KEY_PROXY_INTERVAL, DEFAULT_PROXY_INTERVAL)
            .coerceIn(MIN_PROXY_INTERVAL, MAX_PROXY_INTERVAL)
        set(value) = prefs.edit().putInt(
            KEY_PROXY_INTERVAL,
            value.coerceIn(MIN_PROXY_INTERVAL, MAX_PROXY_INTERVAL),
        ).apply()

    var proxyScreenshot: Boolean
        get() = prefs.getBoolean(KEY_PROXY_SCREENSHOT, false)
        set(value) = prefs.edit().putBoolean(KEY_PROXY_SCREENSHOT, value).apply()

    var proxyRealtimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROXY_REALTIME, false)
        set(value) = prefs.edit().putBoolean(KEY_PROXY_REALTIME, value).apply()

    var proxyRealtimeSeconds: Int
        get() = prefs.getInt(KEY_PROXY_REALTIME_SECONDS, DEFAULT_PROXY_REALTIME_SECONDS)
            .coerceIn(MIN_PROXY_REALTIME_SECONDS, MAX_PROXY_REALTIME_SECONDS)
        set(value) = prefs.edit().putInt(
            KEY_PROXY_REALTIME_SECONDS,
            value.coerceIn(MIN_PROXY_REALTIME_SECONDS, MAX_PROXY_REALTIME_SECONDS),
        ).apply()

    var agentMaxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, com.andloading.assistant.agent.ToolExecutionLimits.AGENT_MAX_STEPS)
            .coerceIn(MIN_MAX_STEPS, MAX_MAX_STEPS)
        set(value) = prefs.edit().putInt(
            KEY_MAX_STEPS,
            value.coerceIn(MIN_MAX_STEPS, MAX_MAX_STEPS),
        ).apply()

    var agentStepsUnlimited: Boolean
        get() = prefs.getBoolean(KEY_STEPS_UNLIMITED, false)
        set(value) = prefs.edit().putBoolean(KEY_STEPS_UNLIMITED, value).apply()

    var personaPrompt: String
        get() = prefs.getString(KEY_PERSONA, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PERSONA, value.trim()).apply()

    var screenshotEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_ENABLED, value).apply()

    var keepAliveFloating: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE_FLOATING, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE_FLOATING, value).apply()

    var visionModel: String
        get() = prefs.getString(KEY_VISION_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VISION_MODEL, value.trim()).apply()

    var piiSanitizePhone: Boolean
        get() = prefs.getBoolean(KEY_PII_PHONE, true)
        set(value) = prefs.edit().putBoolean(KEY_PII_PHONE, value).apply()

    var piiSanitizeIdCard: Boolean
        get() = prefs.getBoolean(KEY_PII_ID, true)
        set(value) = prefs.edit().putBoolean(KEY_PII_ID, value).apply()

    var piiSanitizeBankCard: Boolean
        get() = prefs.getBoolean(KEY_PII_BANK, true)
        set(value) = prefs.edit().putBoolean(KEY_PII_BANK, value).apply()

    var piiSanitizeEmail: Boolean
        get() = prefs.getBoolean(KEY_PII_EMAIL, true)
        set(value) = prefs.edit().putBoolean(KEY_PII_EMAIL, value).apply()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_SHOW_THINKING = "show_thinking"
        private const val KEY_AUTO_ENABLED = "auto_enabled"
        private const val KEY_KEEP_ALIVE_FLOATING = "keep_alive_floating"
        private const val KEY_MAX_STEPS = "agent_max_steps"
        private const val KEY_STEPS_UNLIMITED = "agent_steps_unlimited"
        private const val KEY_PERSONA = "persona_prompt"
        private const val KEY_PROXY_WHITELIST = "proxy_whitelist"
        private const val KEY_PROXY_INTERVAL = "proxy_interval_minutes"
        private const val KEY_PROXY_SCREENSHOT = "proxy_screenshot"
        private const val KEY_PROXY_REALTIME = "proxy_realtime_enabled"
        private const val KEY_PROXY_REALTIME_SECONDS = "proxy_realtime_seconds"
        const val MIN_PROXY_INTERVAL = 5
        const val MAX_PROXY_INTERVAL = 60
        const val DEFAULT_PROXY_INTERVAL = 15
        const val MIN_PROXY_REALTIME_SECONDS = 3
        const val MAX_PROXY_REALTIME_SECONDS = 60
        const val DEFAULT_PROXY_REALTIME_SECONDS = 5
        private const val KEY_SCREENSHOT_ENABLED = "screenshot_enabled"
        private const val KEY_VISION_MODEL = "vision_model"
        private const val KEY_PII_PHONE = "pii_sanitize_phone"
        private const val KEY_PII_ID = "pii_sanitize_id_card"
        private const val KEY_PII_BANK = "pii_sanitize_bank_card"
        private const val KEY_PII_EMAIL = "pii_sanitize_email"
        const val MIN_MAX_STEPS = 4
        const val MAX_MAX_STEPS = 60
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
    }
}
