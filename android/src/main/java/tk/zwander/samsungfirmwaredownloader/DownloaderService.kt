package tk.zwander.samsungfirmwaredownloader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import tk.zwander.common.IDownloaderService
import tk.zwander.common.R
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventManager
import tk.zwander.common.util.eventManager
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

/**
 * A Service to manage downloading and decrypting on Android. This is used mostly to just keep the
 * app running when it's in the background. It will run as long as the app is running.
 */
@OptIn(ExperimentalTime::class)
class DownloaderService : Service(), EventManager.EventListener, CoroutineScope by MainScope() {
    companion object {
        /**
         * Start the Service.
         * @param context a Context object.
         */
        @Suppress("unused")
        fun start(context: Context) {
            val startIntent = Intent(context, DownloaderService::class.java)

            ContextCompat.startForegroundService(context, startIntent)
        }

        fun bind(context: Context, connection: ServiceConnection) {
            val startIntent = Intent(context, DownloaderService::class.java)

            context.bindService(startIntent, connection, BIND_AUTO_CREATE)
        }

        /**
         * Stop the Service.
         * @param context a Context object.
         */
        @Suppress("unused")
        fun stop(context: Context) {
            val stopIntent = Intent(context, DownloaderService::class.java)

            context.stopService(stopIntent)
        }
    }

    /**
     * Used to communicate with MainActivity.
     */
    private var runningJobs = 0
        set(value) {
            field = value

            launch {
                if (value == 0) {
                    nm.cancel(100)
                    nm.notify(100, makeForegroundNotification(null))
                }

                if (value == 0 && !activityRunning) {
                    nm.notify(
                        100,
                        NotificationCompat.Builder(this@DownloaderService, "notification")
                            .setContentTitle(org.jetbrains.compose.resources.getString(Res.string.notification_finished_channel_name))
                            .setContentText(org.jetbrains.compose.resources.getString(Res.string.notification_finished_channel_text))
                            .setSmallIcon(R.mipmap.ic_launcher_foreground)
                            .setContentIntent(
                                PendingIntent.getActivity(
                                    this@DownloaderService, 101,
                                    Intent(this@DownloaderService, MainActivity::class.java),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                                )
                            )
                            .build()
                    )

                    stopSelf()
                }
            }
        }
    private var activityRunning = false

    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityStarted(activity: Activity) {
            if (activity is MainActivity) {
                activityRunning = true
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is MainActivity) {
                activityRunning = false
                if (runningJobs == 0) {
                    stopSelf()
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()

        eventManager.addListener(this)
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        launch {
            //Create the notification channel if applicable.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        "progress", org.jetbrains.compose.resources.getString(Res.string.notification_progress_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )

                nm.createNotificationChannel(
                    NotificationChannel(
                        "notification", org.jetbrains.compose.resources.getString(Res.string.notification_finished_channel_text),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }

            //Create the foreground notification.
            val foregroundNotification = makeForegroundNotification(null)

            //Start in the foreground.
            ServiceCompat.startForeground(this@DownloaderService, 100, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        eventManager.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return object : IDownloaderService.Stub() {
            override fun destroy() {
                stopSelf()
            }
        }
    }

    override suspend fun onEvent(event: Event) {
        when (event) {
            Event.Download.Start, Event.Decrypt.Start -> onJobStarted()
            Event.Download.Finish, Event.Decrypt.Finish -> onJobFinished()
            is Event.Download.Progress -> {
                onProgress(event.status, event.current, event.max)
            }
            is Event.Decrypt.Progress -> {
                onProgress(event.status, event.current, event.max)
            }
        }
    }

    private var lastUpdate = 0L

    private suspend fun onProgress(status: String, current: Long, max: Long) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUpdate > 500) {
            nm.notify(100, makeForegroundNotification(Triple(status, current, max)))
            lastUpdate = currentTime
        }
    }

    private fun onJobStarted() {
        runningJobs++
    }

    private fun onJobFinished() {
        runningJobs = max(0, runningJobs - 1)
    }

    private suspend fun makeForegroundNotification(progress: Triple<String, Long, Long>? = null): Notification {
        return NotificationCompat.Builder(this, "progress")
            .setContentTitle(getString(tk.zwander.samsungfirmwaredownloader.R.string.app_name))
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentText(org.jetbrains.compose.resources.getString(Res.string.notification_progress_text))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 101,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (progress != null) {
                    setStyle(NotificationCompat.DecoratedCustomViewStyle())

                    val percent =
                        ((progress.second.toDouble() / progress.third.toDouble() * 100.0 * 100.0).roundToInt() / 100.0)

                    setCustomBigContentView(
                        RemoteViews(packageName, R.layout.progress)
                            .apply {
                                setTextViewText(R.id.title, progress.first)
                                setProgressBar(R.id.progress, 100, percent.roundToInt(), false)
                                setTextViewText(R.id.progress_text, "$percent%")
                            }
                    )
                }
            }
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .build()
    }
}
