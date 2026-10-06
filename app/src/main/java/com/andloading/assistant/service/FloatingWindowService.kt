package com.andloading.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.R
import com.andloading.assistant.accessibility.ScreenshotTool
import com.andloading.assistant.agent.ScreenshotApprovalScope
import com.andloading.assistant.ui.MainActivity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val session by lazy { AssistantApp.instance.agentSession }
    private var ballView: View? = null
    private var panelView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    @Volatile
    private var ownAppForeground = false

    private var panelScope: CoroutineScope? = null
    private var statusText: TextView? = null
    private var contentText: TextView? = null
    private var approvalBox: LinearLayout? = null
    private var approvalName: TextView? = null
    private var approvalArgs: TextView? = null
    private var approvalNote: TextView? = null
    private var alwaysAllowButton: TextView? = null
    private var screenshotRunButton: TextView? = null
    private var screenshotConversationButton: TextView? = null
    private var sendButton: TextView? = null
    private var inputEdit: EditText? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        overlayHost = this
        startForeground(NOTIFICATION_ID, buildNotification())
        showBall()
    }

    override fun onDestroy() {
        overlayHost = null
        panelScope?.cancel()
        panelScope = null
        removeView(ballView)
        removeView(panelView)
        ballView = null
        panelView = null
        statusText = null
        contentText = null
        approvalBox = null
        approvalName = null
        approvalArgs = null
        approvalNote = null
        alwaysAllowButton = null
        screenshotRunButton = null
        screenshotConversationButton = null
        sendButton = null
        inputEdit = null
        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        AssistantApp.instance.settings.keepAliveFloating = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (AssistantApp.instance.settings.keepAliveFloating) {
            runCatching { start(this) }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun stopByUser() {
        AssistantApp.instance.settings.keepAliveFloating = false
        stopSelf()
    }

    private fun removeView(v: View?) {
        try {
            if (v != null && v.windowToken != null) wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun overlayParams(
        x: Int,
        y: Int,
        width: Int,
        focusable: Boolean,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        },
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun ballSize(): Int = (52 * resources.displayMetrics.density).toInt()

    private fun panelWidth(): Int = (320 * resources.displayMetrics.density).toInt()

    private fun edgeX(forWidth: Int): Int =
        (resources.displayMetrics.widthPixels - forWidth).coerceAtLeast(0)

    private fun showBall() {
        panelScope?.cancel()
        panelScope = null
        removeView(panelView)
        panelView = null
        val size = ballSize()
        val params = overlayParams(
            edgeX(size),
            resources.displayMetrics.heightPixels / 3,
            size,
            focusable = false,
        )
        val ball = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.floating_ball_bg))
            }
            elevation = 6 * resources.displayMetrics.density
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_notification)
                    val pad = (14 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                },
            )
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        ball.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(event.rawX - downRawX) > touchSlop() || abs(event.rawY - downRawY) > touchSlop()) {
                        moved = true
                    }
                    if (moved) {
                        params.x = (startX + dx).coerceIn(0, edgeX(size))
                        params.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - size)
                        wm.updateViewLayout(ball, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        showPanel(params.x, params.y)
                    } else {
                        params.x = if (params.x + size / 2 < resources.displayMetrics.widthPixels / 2) 0 else edgeX(size)
                        wm.updateViewLayout(ball, params)
                    }
                    true
                }
                else -> false
            }
        }

        removeView(ballView)
        ballView = ball
        ballParams = params
        if (!ownAppForeground) wm.addView(ball, params)
    }

    private fun touchSlop(): Float = 8 * resources.displayMetrics.density

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun pillButton(
        label: String,
        bgRes: Int,
        textRes: Int,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(textRes))
        gravity = Gravity.CENTER
        val padH = (14 * resources.displayMetrics.density).toInt()
        val padV = (9 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            cornerRadius = 18 * resources.displayMetrics.density
            setColor(color(bgRes))
        }
        setOnClickListener { onClick() }
    }

    private fun showPanel(fromX: Int, fromY: Int) {
        panelScope?.cancel()
        removeView(ballView)
        ballView = null
        val width = panelWidth()
        val params = overlayParams(
            fromX.coerceIn(0, edgeX(width)),
            fromY,
            width,
            focusable = true,
        )
        val pad = (16 * resources.displayMetrics.density).toInt()

        val status = TextView(this).apply {
            textSize = 13f
            setTextColor(color(R.color.floating_status_idle))
            setPadding(pad, pad / 2, pad, 0)
        }

        val content = TextView(this).apply {
            textSize = 14f
            setTextColor(color(R.color.floating_panel_text))
            maxLines = 6
            setPadding(pad, pad / 2, pad, 0)
        }

        val approval = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad / 2, pad / 2, pad / 2, 0)
            visibility = View.GONE
            val card = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density
                setColor(color(R.color.floating_approval_bg))
            }
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = card
                setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                addView(
                    TextView(context).apply {
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(color(R.color.floating_panel_text))
                        setPadding(0, pad / 3, 0, 0)
                    }.also { approvalName = it },
                )
                addView(
                    TextView(context).apply {
                        textSize = 12f
                        setTextColor(color(R.color.floating_panel_secondary))
                        maxLines = 3
                        setPadding(0, pad / 4, 0, 0)
                    }.also { approvalArgs = it },
                )
                addView(
                    TextView(context).apply {
                        textSize = 12f
                        setTextColor(color(R.color.floating_status_approval))
                        setPadding(0, pad / 4, 0, 0)
                        visibility = View.GONE
                    }.also { approvalNote = it },
                )
                val buttons = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, pad / 2, 0, 0)
                }
                listOf(
                    getString(R.string.floating_approve_once) to Triple(
                        R.color.floating_btn_primary, R.color.floating_btn_on_primary,
                    ) { session.approve(always = false) },
                    getString(R.string.floating_approve_always) to Triple(
                        R.color.floating_btn_tonal, R.color.floating_btn_on_tonal,
                    ) { session.approve(always = true) },
                    getString(R.string.floating_approve_this_run) to Triple(
                        R.color.floating_btn_tonal, R.color.floating_btn_on_tonal,
                    ) { session.approveScreenshot(ScreenshotApprovalScope.THIS_RUN) },
                    getString(R.string.floating_approve_this_conversation) to Triple(
                        R.color.floating_btn_tonal, R.color.floating_btn_on_tonal,
                    ) { session.approveScreenshot(ScreenshotApprovalScope.THIS_CONVERSATION) },
                    getString(R.string.floating_deny) to Triple(
                        R.color.floating_btn_neutral, R.color.floating_btn_on_neutral,
                    ) { session.deny() },
                ).forEachIndexed { index, (label, style) ->
                    val (bg, fg, action) = style
                    val btn = pillButton(label, bg, fg, action)
                    when (index) {
                        1 -> alwaysAllowButton = btn
                        2 -> screenshotRunButton = btn
                        3 -> screenshotConversationButton = btn
                    }
                    buttons.addView(
                        btn,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { marginEnd = pad / 3 },
                    )
                }
                screenshotRunButton?.visibility = View.GONE
                screenshotConversationButton?.visibility = View.GONE
                addView(buttons)
            }
            addView(inner)
        }

        val input = EditText(this).apply {
            hint = getString(R.string.floating_hint_input)
            textSize = 14f
            maxLines = 2
            background = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(color(R.color.floating_input_bg))
            }
            setPadding(pad / 2, pad / 3, pad / 2, pad / 3)
            setTextColor(color(R.color.floating_panel_text))
            setHintTextColor(color(R.color.floating_panel_secondary))
        }
        val send = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.floating_panel_text))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(color(R.color.floating_btn_neutral))
            }
        }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(
                input,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                send,
                LinearLayout.LayoutParams((40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt(),
                ).apply { marginStart = pad / 3 },
            )
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 20 * resources.displayMetrics.density
                setColor(Color.WHITE)
            }
            elevation = 8 * resources.displayMetrics.density
            addView(
                TextView(this@FloatingWindowService).apply {
                    text = getString(R.string.floating_panel_title)
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color(R.color.floating_panel_text))
                    setPadding(pad, pad, pad, 0)
                },
            )
            addView(status)
            addView(content)
            addView(approval)
            addView(inputRow)
            addView(
                LinearLayout(this@FloatingWindowService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(pad / 3, pad / 2, pad / 3, pad / 3)
                    fun entry(textRes: Int, onClick: () -> Unit): TextView =
                        TextView(this@FloatingWindowService).apply {
                            text = getString(textRes)
                            textSize = 13f
                            setTextColor(color(R.color.floating_panel_secondary))
                            gravity = Gravity.CENTER
                            setPadding(0, pad / 3, 0, pad / 3)
                            setOnClickListener { onClick() }
                        }
                    addView(
                        entry(R.string.floating_open_main) {
                            startActivity(
                                Intent(this@FloatingWindowService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            showBall()
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        entry(R.string.floating_collapse) { showBall() },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        entry(R.string.floating_close_window) { stopByUser() },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                },
            )
        }

        fun refreshSendButton() {
            val busy = session.streaming.value != null || session.activeTool.value != null
            send.text = getString(if (busy) R.string.floating_stop_generation else R.string.floating_send)
            send.isEnabled = busy || !input.text.isBlank()
            send.background = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(
                    if (busy) color(R.color.floating_btn_primary) else color(R.color.floating_btn_neutral),
                )
            }
            send.setTextColor(
                if (busy) color(R.color.floating_btn_on_primary) else color(R.color.floating_panel_text),
            )
        }
        send.setOnClickListener {
            if (session.streaming.value != null || session.activeTool.value != null) {
                session.stop()
            } else {
                session.send(input.text.toString())
                input.setText("")
            }
            refreshSendButton()
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshSendButton()
        })

        fun render() {
            val streaming = session.streaming.value
            val tool = session.activeTool.value
            val call = session.pendingApproval.value
            val busy = streaming != null || tool != null

            when {
                call != null -> {
                    status.text = getString(R.string.floating_status_awaiting)
                    status.setTextColor(color(R.color.floating_status_approval))
                }
                tool != null -> {
                    status.text = getString(R.string.floating_status_tool, tool)
                    status.setTextColor(color(R.color.floating_status_tool))
                }
                streaming != null -> {
                    status.text = getString(R.string.floating_status_thinking)
                    status.setTextColor(color(R.color.floating_status_busy))
                }
                else -> {
                    status.text = getString(R.string.floating_status_idle)
                    status.setTextColor(color(R.color.floating_status_idle))
                }
            }

            if (streaming != null) {
                content.visibility = View.VISIBLE
                content.text =
                    if (streaming.length > TAIL_CHARS) "…" + streaming.takeLast(TAIL_CHARS) else streaming
            } else {
                content.visibility = View.GONE
            }

            if (call != null) {
                approval.visibility = View.VISIBLE
                approvalName?.text = getString(R.string.floating_approval_tool, call.name)
                approvalArgs?.text = call.argumentsJson.take(200)
                val privacy = call.name == ScreenshotTool.NAME
                approvalNote?.visibility = if (privacy) View.VISIBLE else View.GONE
                approvalNote?.text =
                    if (privacy) getString(R.string.floating_approval_screenshot_note) else ""
                alwaysAllowButton?.visibility = if (privacy) View.GONE else View.VISIBLE
                screenshotRunButton?.visibility =
                    if (privacy) View.VISIBLE else View.GONE
                screenshotConversationButton?.visibility =
                    if (privacy) View.VISIBLE else View.GONE
            } else {
                approval.visibility = View.GONE
            }

            input.isEnabled = true
            refreshSendButton()
        }

        panelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
            launch { session.streaming.collect { render() } }
            launch { session.activeTool.collect { render() } }
            launch { session.pendingApproval.collect { render() } }
        }
        render()

        removeView(panelView)
        panelView = panel
        panelParams = params
        if (!ownAppForeground) wm.addView(panel, params)
    }

    private fun setOwnAppForegroundInternal(fg: Boolean) {
        if (fg == ownAppForeground) return
        ownAppForeground = fg
        if (fg) {
            removeView(ballView)
            removeView(panelView)
        } else {
            ballView?.let { v -> ballParams?.let { p -> runCatching { wm.addView(v, p) } } }
            panelView?.let { v -> panelParams?.let { p -> runCatching { wm.addView(v, p) } } }
        }
    }

    suspend fun <T> hideOverlaysDuring(block: suspend () -> T): T? =
        withContext(Dispatchers.Main.immediate) {
            val attachedPanel = panelView?.takeIf { it.windowToken != null }
            val attachedBall = if (attachedPanel == null) {
                ballView?.takeIf { it.windowToken != null }
            } else {
                null
            }
            removeView(attachedPanel)
            removeView(attachedBall)
            try {
                delay(250)
                block()
            } finally {
                if (attachedPanel != null) {
                    runCatching { panelParams?.let { wm.addView(attachedPanel, it) } }
                } else if (attachedBall != null) {
                    runCatching { ballParams?.let { wm.addView(attachedBall, it) } }
                }
            }
        }

    suspend fun <T> withoutPanelFocus(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) {
            val panel = panelView?.takeIf { it.windowToken != null }
            val params = panelParams
            if (panel == null || params == null ||
                (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
            ) {
                return@withContext block()
            }
            inputEdit?.clearFocus()
            panel.clearFocus()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(panel, params) }
            try {
                delay(100)
                block()
            } finally {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    {
                        params.flags =
                            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        runCatching {
                            if (panel.windowToken != null) wm.updateViewLayout(panel, params)
                        }
                    },
                    250,
                )
            }
        }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            this, 2, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AssistantApp.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_floating))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.floating_stop), stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val TAIL_CHARS = 600
        const val ACTION_STOP = "com.andloading.assistant.action.STOP_FLOATING"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var overlayHost: FloatingWindowService? = null
            private set

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingWindowService::class.java),
            )
        }

        fun stop(context: Context) {
            (context.applicationContext as? AssistantApp)?.settings?.keepAliveFloating = false
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }

        fun setOwnAppForeground(fg: Boolean) {
            overlayHost?.setOwnAppForegroundInternal(fg)
        }
    }
}
