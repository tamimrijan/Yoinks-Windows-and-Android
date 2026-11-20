package com.tamimrijan.yoinks.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tamimrijan.yoinks.R
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.engine.Engine
import com.tamimrijan.yoinks.engine.Phase
import com.tamimrijan.yoinks.ui.MainActivity
import com.tamimrijan.yoinks.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a download going when you leave the app, with live progress and a Cancel button
 * in the notification shade. Stops itself as soon as the yoink finishes.
 */
class DownloadService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val first = buildProgress(this, YoinksApp.instance.engine.phase.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ONGOING_ID, first)
        }
        lifecycleScope.launch {
            var lastUpdate = 0L
            val engine = YoinksApp.instance.engine
            kotlinx.coroutines.flow.combine(engine.phase, engine.queued) { p, _ -> p }.collect { phase ->
                // stay up while something runs or waits in the queue (a shared link starting later
                // couldn't restart this service from the background)
                if (!engine.hasWork) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                // the shade rate-limits updates; twice a second is plenty
                val now = System.currentTimeMillis()
                if (now - lastUpdate < 500) return@collect
                lastUpdate = now
                if (canNotify(this@DownloadService)) {
                    NotificationManagerCompat.from(this@DownloadService).notify(ONGOING_ID, buildProgress(this@DownloadService, phase))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL) YoinksApp.instance.engine.cancel()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ONGOING_ID = 1
        private const val DONE_ID = 2
        private const val ACTION_CANCEL = "com.tamimrijan.yoinks.CANCEL"

        /** Start right away (while the share sheet is still on screen, as Android requires). */
        fun start(context: Context) {
            if (YoinksApp.isRobolectric) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }

        /** Starts the service when a download begins and posts a "Yoinked!" note when it ends. */
        fun watch(context: Context, engine: Engine) {
            if (YoinksApp.isRobolectric) return
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                engine.phase.map { it::class }.distinctUntilChanged().collect {
                    val phase = engine.phase.value
                    when (phase) {
                        is Phase.Downloading -> runCatching {
                            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
                        }
                        is Phase.Done -> if (!MainActivity.isVisible && canNotify(app)) {
                            ensureChannel(app)
                            // one note per finished file, tap to play it
                            NotificationManagerCompat.from(app).notify(
                                DONE_ID + (phase.entry.filePath.hashCode() and 0xFFFF),
                                buildFinished(app, "Yoinked!", phase.entry.title, phase.entry.filePath),
                            )
                        }
                        is Phase.Error -> if (!MainActivity.isVisible && canNotify(app)) {
                            ensureChannel(app)
                            NotificationManagerCompat.from(app).notify(DONE_ID, buildFinished(app, "Couldn't yoink that", phase.message, null))
                        }
                        else -> Unit
                    }
                }
            }
        }

        private fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.channel_downloads_desc)
                    },
                )
            }
        }

        private fun openApp(context: Context) = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun buildProgress(context: Context, phase: Phase): android.app.Notification {
            val cancel = PendingIntent.getService(
                context, 1,
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(context))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .addAction(0, "Cancel", cancel)
            when (phase) {
                is Phase.Downloading -> {
                    b.setContentTitle(phase.info.title)
                    val waiting = YoinksApp.instance.engine.queued.value
                    if (waiting > 0) b.setSubText("$waiting more waiting")
                    val p = phase.progress
                    when {
                        phase.processing -> b.setContentText(if (phase.choice.isAudio) "Converting to MP3…" else "Merging video and audio…").setProgress(0, 0, true)
                        p?.fraction != null -> {
                            val overall = (p.part + p.fraction) / p.totalParts
                            val text = listOfNotNull(
                                "${(overall * 100).toInt()}%",
                                p.speed?.let { Format.speed(it) },
                                p.etaSeconds?.takeIf { it > 0 }?.let { "${Format.duration(it.toDouble())} left" },
                            ).joinToString(" · ")
                            b.setContentText(text).setProgress(1000, (overall * 1000).toInt(), false)
                        }
                        else -> b.setContentText("Starting download…").setProgress(0, 0, true)
                    }
                }
                is Phase.Probing -> b.setContentTitle("Fetching video info…").setContentText(phase.url).setProgress(0, 0, true)
                else -> b.setContentTitle("Yoinks").setContentText("Getting ready…")
            }
            return b.build()
        }

        private fun buildFinished(context: Context, title: String, text: String, filePath: String?): android.app.Notification {
            val play = filePath?.let { path ->
                runCatching {
                    val f = java.io.File(path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, com.tamimrijan.yoinks.util.Files.mime(f))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    PendingIntent.getActivity(context, path.hashCode(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }.getOrNull()
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(play ?: openApp(context))
                .apply { if (play != null) addAction(0, "Play", play) }
                .addAction(0, "Open Yoinks", openApp(context))
                .build()
        }
    }
}
