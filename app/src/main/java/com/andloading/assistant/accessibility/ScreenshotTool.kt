package com.andloading.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.util.Base64
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.agent.AssistantTool
import com.andloading.assistant.agent.schemaOf
import com.andloading.assistant.service.FloatingWindowService
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenshotTool : AssistantTool {

    override val name = NAME
    override val dangerous = true
    override val description =
        "截取当前屏幕并发送给视觉模型分析"
    override val parameters = schemaOf()

    override val enabled: Boolean
        get() = AssistantApp.instance.settings.screenshotEnabled

    override val persistableApproval = false

    override suspend fun execute(args: JsonObject): String {
        val service = AssistantAccessibilityService.instance
            ?: return "无障碍服务未开启：请在系统设置 → 无障碍中启用「Andloading」，然后重试"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "系统不支持"
        }
        val host = FloatingWindowService.overlayHost
        val bitmap = if (host != null) {
            host.hideOverlaysDuring { captureBitmap(service) }
        } else {
            captureBitmap(service)
        } ?: return "截屏失败：系统未返回图像"
        val dataUrl = encodeDataUrl(bitmap)
        if (dataUrl == null) {
            bitmap.recycle()
            return "截屏失败：图像编码异常"
        }
        pendingImage = dataUrl
        pendingImageMeta = "${bitmap.width}x${bitmap.height}"
        bitmap.recycle()
        return "已截取当前屏幕（$pendingImageMeta）"
    }

    companion object {
        const val NAME = "take_screenshot"

        private suspend fun captureBitmap(service: AssistantAccessibilityService): Bitmap? =
            suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardware = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                        hardware.close()
                        val software = wrapped?.let { it.copy(Bitmap.Config.ARGB_8888, false) }
                        wrapped?.recycle()
                        cont.resume(software)
                    }

                    override fun onFailure(errorCode: Int) = cont.resume(null)
                }
                val executor = java.util.concurrent.Executor { it.run() }
                val cbClass = AccessibilityService.TakeScreenshotCallback::class.java
                val invoked = runCatching {
                    AccessibilityService::class.java.getMethod(
                        "takeScreenshot",
                        Int::class.javaPrimitiveType,
                        java.util.concurrent.Executor::class.java,
                        cbClass,
                    ).invoke(service, android.view.Display.DEFAULT_DISPLAY, executor, callback)
                    true
                }.getOrElse {
                    runCatching {
                        AccessibilityService::class.java.getMethod(
                            "takeScreenshot",
                            java.util.concurrent.Executor::class.java,
                            cbClass,
                        ).invoke(service, executor, callback)
                        true
                    }.getOrDefault(false)
                }
                if (!invoked) cont.resume(null)
            }

        private fun encodeDataUrl(src: Bitmap): String? {
            val bytes = jpegBytes(src) ?: return null
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        private fun jpegBytes(src: Bitmap): ByteArray? {
            val maxEdge = 1280
            val scale = maxEdge.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                src
            }
            return try {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    out.toByteArray()
                }
            } catch (_: Exception) {
                null
            } finally {
                if (bitmap !== src) bitmap.recycle()
            }
        }

        suspend fun captureChangeHash(): Int? {
            val service = AssistantAccessibilityService.instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val host = FloatingWindowService.overlayHost
            val bitmap = if (host != null) {
                host.hideOverlaysDuring { captureBitmap(service) }
            } else {
                captureBitmap(service)
            } ?: return null
            return try {
                jpegBytes(bitmap)?.contentHashCode()
            } finally {
                bitmap.recycle()
            }
        }

        @Volatile
        var pendingImage: String? = null
            private set

        @Volatile
        var pendingImageMeta: String = ""
            private set

        fun consumePendingImage(): String? {
            val img = pendingImage
            pendingImage = null
            pendingImageMeta = ""
            return img
        }
    }
}
