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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import kotlin.math.ceil

class StretchingTimerService : Service(), TextToSpeech.OnInitListener {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val voiceHandler = Handler(Looper.getMainLooper())

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var activeTimer: CountDownTimer? = null
    private var pendingVoicePrompt: Runnable? = null

    private var stretchSeconds = DEFAULT_STRETCH_SECONDS
    private var restSeconds = DEFAULT_REST_SECONDS
    private var remainingSeconds = DEFAULT_STRETCH_SECONDS
    private var currentPhaseDuration = DEFAULT_STRETCH_SECONDS
    private var currentPhase = StretchPhase.IDLE
    private var voiceEnabled = false
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> startTimerFromIntent(intent)
            ACTION_STOP -> stopTimerAndSelf()
            ACTION_UPDATE_SETTINGS -> updateSettings(intent)
            ACTION_QUERY -> {
                sendState()
                if (!isRunning) stopSelf()
            }
        }
        return if (isRunning) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopTimerAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        activeTimer?.cancel()
        clearPendingVoicePrompt()
        toneGenerator.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = textToSpeech?.setLanguage(Locale.getDefault()) != TextToSpeech.LANG_MISSING_DATA
        }
    }

    private fun startTimerFromIntent(intent: Intent) {
        stretchSeconds = intent.getIntExtra(EXTRA_STRETCH_SECONDS, DEFAULT_STRETCH_SECONDS)
        restSeconds = intent.getIntExtra(EXTRA_REST_SECONDS, DEFAULT_REST_SECONDS)
            .coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, false)
        currentPhase = StretchPhase.STRETCH
        currentPhaseDuration = stretchSeconds
        remainingSeconds = stretchSeconds
        isRunning = true

        startForeground(NOTIFICATION_ID, buildNotification())
        startTimer()
        sendState()
    }

    private fun updateSettings(intent: Intent) {
        stretchSeconds = intent.getIntExtra(EXTRA_STRETCH_SECONDS, stretchSeconds)
        restSeconds = intent.getIntExtra(EXTRA_REST_SECONDS, restSeconds)
            .coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, voiceEnabled)

        if (currentPhase == StretchPhase.IDLE) {
            currentPhaseDuration = stretchSeconds
            remainingSeconds = stretchSeconds
        }
        updateNotification()
        sendState()
    }

    private fun startTimer() {
        activeTimer?.cancel()
        activeTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                updateNotification()
                sendState()
            }

            override fun onFinish() {
                activeTimer = null
                remainingSeconds = 0
                handlePhaseFinished()
            }
        }.start()
    }

    private fun handlePhaseFinished() {
        playBeep()
        when (currentPhase) {
            StretchPhase.STRETCH -> {
                announceIfVoiceEnabled(getString(R.string.stretching_change_position_prompt))
                currentPhase = StretchPhase.REST
                currentPhaseDuration = restSeconds
                remainingSeconds = restSeconds
                startTimer()
            }
            StretchPhase.REST -> {
                announceIfVoiceEnabled(getString(R.string.stretching_start_stretching_prompt))
                currentPhase = StretchPhase.STRETCH
                currentPhaseDuration = stretchSeconds
                remainingSeconds = stretchSeconds
                startTimer()
            }
            StretchPhase.IDLE -> Unit
        }
        updateNotification()
        sendState()
    }

    private fun stopTimerAndSelf() {
        activeTimer?.cancel()
        activeTimer = null
        clearPendingVoicePrompt()
        textToSpeech?.stop()
        isRunning = false
        currentPhase = StretchPhase.IDLE
        currentPhaseDuration = stretchSeconds
        remainingSeconds = stretchSeconds
        sendState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun announceIfVoiceEnabled(message: String) {
        clearPendingVoicePrompt()
        if (!voiceEnabled || !ttsReady) return

        pendingVoicePrompt = Runnable {
            pendingVoicePrompt = null
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, message)
        }.also { voiceHandler.postDelayed(it, VOICE_PROMPT_DELAY_MS) }
    }

    private fun clearPendingVoicePrompt() {
        pendingVoicePrompt?.let { voiceHandler.removeCallbacks(it) }
        pendingVoicePrompt = null
    }

    private fun playBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_DURATION_MS)
    }

    private fun sendState() {
        val stateIntent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_IS_RUNNING, isRunning)
            .putExtra(EXTRA_PHASE, currentPhase.value)
            .putExtra(EXTRA_STRETCH_SECONDS, stretchSeconds)
            .putExtra(EXTRA_REST_SECONDS, restSeconds)
            .putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            .putExtra(EXTRA_PHASE_DURATION_SECONDS, currentPhaseDuration)
            .putExtra(EXTRA_VOICE_ENABLED, voiceEnabled)
        sendBroadcast(stateIntent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, StretchingActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, StretchingTimerService::class.java)
            .setAction(ACTION_STOP)
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.stretching_title))
            .setContentText(notificationText())
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

    private fun notificationText(): String {
        val phase = when (currentPhase) {
            StretchPhase.STRETCH -> getString(R.string.stretching_phase_stretch)
            StretchPhase.REST -> getString(R.string.stretching_phase_rest)
            StretchPhase.IDLE -> getString(R.string.stretching_phase_idle)
        }
        return getString(R.string.stretching_notification_template, phase, formatSeconds(remainingSeconds))
    }

    private fun formatSeconds(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return getString(R.string.hiit_timer_format, safeSeconds / 60, safeSeconds % 60)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.stretching_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private enum class StretchPhase(val value: String) {
        IDLE(PHASE_IDLE),
        STRETCH(PHASE_STRETCH),
        REST(PHASE_REST)
    }

    companion object {
        const val ACTION_START = "com.emanuel.gymhelper.stretching.START"
        const val ACTION_STOP = "com.emanuel.gymhelper.stretching.STOP"
        const val ACTION_UPDATE_SETTINGS = "com.emanuel.gymhelper.stretching.UPDATE_SETTINGS"
        const val ACTION_QUERY = "com.emanuel.gymhelper.stretching.QUERY"
        const val ACTION_STATE = "com.emanuel.gymhelper.stretching.STATE"

        const val EXTRA_STRETCH_SECONDS = "stretch_seconds"
        const val EXTRA_REST_SECONDS = "rest_seconds"
        const val EXTRA_VOICE_ENABLED = "voice_enabled"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
        const val EXTRA_PHASE_DURATION_SECONDS = "phase_duration_seconds"

        const val PHASE_IDLE = "idle"
        const val PHASE_STRETCH = "stretch"
        const val PHASE_REST = "rest"

        private const val DEFAULT_STRETCH_SECONDS = 30
        private const val DEFAULT_REST_SECONDS = 5
        private const val MIN_REST_SECONDS = 1
        private const val MAX_REST_SECONDS = 120
        private const val BEEP_DURATION_MS = 400
        private const val VOICE_PROMPT_DELAY_MS = 750L
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_CHANNEL_ID = "stretching_timer"
    }
}
