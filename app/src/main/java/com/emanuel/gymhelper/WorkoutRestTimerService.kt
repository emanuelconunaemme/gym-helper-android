package com.emanuel.gymhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.ceil

class WorkoutRestTimerService : Service() {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private var activeTimer: CountDownTimer? = null
    private var exerciseProgressId: Long = -1L
    private var remainingSeconds = 0
    private var durationSeconds = 0
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> startTimerFromIntent(intent)
            ACTION_STOP -> stopTimerAndSelf(finished = false)
            ACTION_QUERY -> {
                sendState(finished = false)
                if (!isRunning) stopSelf()
            }
        }
        return if (isRunning) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopTimerAndSelf(finished = false)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        activeTimer?.cancel()
        toneGenerator.release()
        super.onDestroy()
    }

    private fun startTimerFromIntent(intent: Intent) {
        exerciseProgressId = intent.getLongExtra(EXTRA_EXERCISE_PROGRESS_ID, -1L)
        durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0).coerceAtLeast(0)
        remainingSeconds = durationSeconds
        if (exerciseProgressId <= 0L || durationSeconds <= 0) {
            stopTimerAndSelf(finished = false)
            return
        }

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        startTimer()
        sendState(finished = false)
    }

    private fun startTimer() {
        activeTimer?.cancel()
        activeTimer = object : CountDownTimer(remainingSeconds * 1000L, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                updateNotification()
                sendState(finished = false)
            }

            override fun onFinish() {
                activeTimer = null
                remainingSeconds = 0
                playTimerBeep()
                stopTimerAndSelf(finished = true)
            }
        }.start()
    }

    private fun stopTimerAndSelf(finished: Boolean) {
        activeTimer?.cancel()
        activeTimer = null
        isRunning = false
        sendState(finished = finished)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playTimerBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_DURATION_MS)
    }

    private fun sendState(finished: Boolean) {
        val stateIntent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_IS_RUNNING, isRunning)
            .putExtra(EXTRA_FINISHED, finished)
            .putExtra(EXTRA_EXERCISE_PROGRESS_ID, exerciseProgressId)
            .putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
        sendBroadcast(stateIntent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, WorkoutRestTimerService::class.java)
            .setAction(ACTION_STOP)
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.workout_rest_notification_title))
            .setContentText(getString(R.string.workout_rest_notification_template, formatSeconds(remainingSeconds)))
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stretching_stop_button), pendingStopIntent)
            .build()
    }

    private fun updateNotification() {
        if (!isRunning) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun formatSeconds(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return getString(R.string.hiit_timer_format, safeSeconds / 60, safeSeconds % 60)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.workout_rest_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.emanuel.gymhelper.workoutRest.START"
        const val ACTION_STOP = "com.emanuel.gymhelper.workoutRest.STOP"
        const val ACTION_QUERY = "com.emanuel.gymhelper.workoutRest.QUERY"
        const val ACTION_STATE = "com.emanuel.gymhelper.workoutRest.STATE"

        const val EXTRA_EXERCISE_PROGRESS_ID = "exercise_progress_id"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_FINISHED = "finished"

        private const val BEEP_DURATION_MS = 600
        private const val NOTIFICATION_ID = 4001
        private const val NOTIFICATION_CHANNEL_ID = "workout_rest_timer"
    }
}
