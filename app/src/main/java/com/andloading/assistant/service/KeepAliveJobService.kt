package com.andloading.assistant.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.andloading.assistant.AssistantApp

class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val desired = (applicationContext as? AssistantApp)?.settings?.keepAliveFloating == true
        if (desired && Settings.canDrawOverlays(this) && !FloatingWindowService.isRunning) {
            runCatching { FloatingWindowService.start(this) }
            runCatching { AssistantForegroundService.start(this) }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 4242
        private const val PERIODIC_MILLIS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, KeepAliveJobService::class.java),
            )
                .setPeriodic(PERIODIC_MILLIS)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }
        }
    }
}
