package com.andloading.assistant.shizuku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NOT_AUTHORIZED, READY }

object ShizukuGate {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"

    private var binderReceivedListener: Shizuku.OnBinderReceivedListener? = null
    private var binderDeadListener: Shizuku.OnBinderDeadListener? = null
    private var requestResultListener: Shizuku.OnRequestPermissionResultListener? = null

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun state(context: Context): ShizukuState = try {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.NOT_AUTHORIZED
            }
        } else if (isInstalled(context)) {
            ShizukuState.NOT_RUNNING
        } else {
            ShizukuState.NOT_INSTALLED
        }
    } catch (e: Exception) {
        if (isInstalled(context)) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
    }

    fun attach(onBinderChanged: () -> Unit) {
        if (binderReceivedListener != null) return
        binderReceivedListener = Shizuku.OnBinderReceivedListener { onBinderChanged() }.also {
            Shizuku.addBinderReceivedListenerSticky(it)
        }
        binderDeadListener = Shizuku.OnBinderDeadListener { onBinderChanged() }.also {
            Shizuku.addBinderDeadListener(it)
        }
    }

    fun detach() {
        binderReceivedListener?.let { Shizuku.removeBinderReceivedListener(it) }
        binderDeadListener?.let { Shizuku.removeBinderDeadListener(it) }
        binderReceivedListener = null
        binderDeadListener = null
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    fun addOnRequestResultListener(listener: (requestCode: Int, grantResult: Int) -> Unit): () -> Unit {
        val l = Shizuku.OnRequestPermissionResultListener { code, result -> listener(code, result) }
        Shizuku.addRequestPermissionResultListener(l)
        requestResultListener = l
        return { Shizuku.removeRequestPermissionResultListener(l) }
    }

    fun launchShizukuApp(context: Context): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun openInstallPage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
