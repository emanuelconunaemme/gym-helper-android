package com.emanuel.gymhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.emanuel.gymhelper.databinding.ActivityStretchingBinding

class StretchingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStretchingBinding
    private lateinit var preferences: SharedPreferences

    private var stretchSeconds = DEFAULT_STRETCH_SECONDS
    private var restSeconds = DEFAULT_REST_SECONDS
    private var remainingSeconds = DEFAULT_STRETCH_SECONDS
    private var currentPhase = StretchPhase.IDLE
    private var currentPhaseDuration = DEFAULT_STRETCH_SECONDS
    private var isTimerRunning = false

    private val timerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != StretchingTimerService.ACTION_STATE) return
            bindServiceState(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStretchingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

        loadSavedConfig()
        setupUi()
        resetLocalSessionState()
        renderUi()
    }

    override fun onStart() {
        super.onStart()
        registerTimerStateReceiver()
        sendServiceCommand(StretchingTimerService.ACTION_QUERY)
    }

    override fun onStop() {
        unregisterReceiver(timerStateReceiver)
        super.onStop()
    }

    private fun setupUi() {
        binding.stretchingToolbar.setNavigationOnClickListener { finish() }
        binding.stretchTimeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            stretchSeconds = when (checkedId) {
                R.id.stretchTime15Button -> 15
                R.id.stretchTime30Button -> 30
                R.id.stretchTime45Button -> 45
                R.id.stretchTime60Button -> 60
                R.id.stretchTime90Button -> 90
                R.id.stretchTime120Button -> 120
                else -> DEFAULT_STRETCH_SECONDS
            }
            saveConfig()
            if (isTimerRunning) {
                sendSettingsUpdate()
            } else {
                remainingSeconds = stretchSeconds
                currentPhaseDuration = stretchSeconds
                renderUi()
            }
        }
        binding.restMinusButton.setOnClickListener {
            restSeconds = (restSeconds - REST_STEP_SECONDS).coerceAtLeast(MIN_REST_SECONDS)
            saveConfig()
            if (isTimerRunning) sendSettingsUpdate() else renderUi()
        }
        binding.restPlusButton.setOnClickListener {
            restSeconds = (restSeconds + REST_STEP_SECONDS).coerceAtMost(MAX_REST_SECONDS)
            saveConfig()
            if (isTimerRunning) sendSettingsUpdate() else renderUi()
        }
        binding.voiceSwitch.setOnCheckedChangeListener { _, _ ->
            saveConfig()
            if (isTimerRunning) sendSettingsUpdate()
        }
        binding.stretchingStartButton.setOnClickListener { startSession() }
        binding.stretchingStopButton.setOnClickListener { stopSession() }
    }

    private fun startSession() {
        isTimerRunning = true
        currentPhase = StretchPhase.STRETCH
        currentPhaseDuration = stretchSeconds
        remainingSeconds = stretchSeconds
        renderUi()
        sendServiceCommand(StretchingTimerService.ACTION_START)
    }

    private fun stopSession() {
        sendServiceCommand(StretchingTimerService.ACTION_STOP)
        resetLocalSessionState()
        renderUi()
    }

    private fun sendSettingsUpdate() {
        sendServiceCommand(StretchingTimerService.ACTION_UPDATE_SETTINGS)
    }

    private fun sendServiceCommand(action: String) {
        val intent = Intent(this, StretchingTimerService::class.java)
            .setAction(action)
            .putExtra(StretchingTimerService.EXTRA_STRETCH_SECONDS, stretchSeconds)
            .putExtra(StretchingTimerService.EXTRA_REST_SECONDS, restSeconds)
            .putExtra(StretchingTimerService.EXTRA_VOICE_ENABLED, binding.voiceSwitch.isChecked)

        if (action == StretchingTimerService.ACTION_START) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun bindServiceState(intent: Intent) {
        isTimerRunning = intent.getBooleanExtra(StretchingTimerService.EXTRA_IS_RUNNING, false)
        stretchSeconds = intent.getIntExtra(StretchingTimerService.EXTRA_STRETCH_SECONDS, stretchSeconds)
        restSeconds = intent.getIntExtra(StretchingTimerService.EXTRA_REST_SECONDS, restSeconds)
        remainingSeconds = intent.getIntExtra(StretchingTimerService.EXTRA_REMAINING_SECONDS, remainingSeconds)
        currentPhaseDuration = intent.getIntExtra(
            StretchingTimerService.EXTRA_PHASE_DURATION_SECONDS,
            currentPhaseDuration
        )
        currentPhase = StretchPhase.fromServiceValue(
            intent.getStringExtra(StretchingTimerService.EXTRA_PHASE)
        )
        binding.voiceSwitch.isChecked = intent.getBooleanExtra(
            StretchingTimerService.EXTRA_VOICE_ENABLED,
            binding.voiceSwitch.isChecked
        )
        checkStretchButton(stretchSeconds)
        saveConfig()
        if (!isTimerRunning) resetLocalSessionState()
        renderUi()
    }

    private fun registerTimerStateReceiver() {
        val filter = IntentFilter(StretchingTimerService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerStateReceiver, filter)
        }
    }

    private fun checkStretchButton(seconds: Int) {
        val buttonId = when (seconds) {
            15 -> R.id.stretchTime15Button
            30 -> R.id.stretchTime30Button
            45 -> R.id.stretchTime45Button
            60 -> R.id.stretchTime60Button
            90 -> R.id.stretchTime90Button
            120 -> R.id.stretchTime120Button
            else -> R.id.stretchTime30Button
        }
        if (binding.stretchTimeToggleGroup.checkedButtonId != buttonId) {
            binding.stretchTimeToggleGroup.check(buttonId)
        }
    }

    private fun resetLocalSessionState() {
        isTimerRunning = false
        currentPhase = StretchPhase.IDLE
        currentPhaseDuration = stretchSeconds
        remainingSeconds = stretchSeconds
    }

    private fun loadSavedConfig() {
        stretchSeconds = preferences.getInt(PREF_STRETCH_SECONDS, DEFAULT_STRETCH_SECONDS)
            .takeIf { it in STRETCH_SECOND_OPTIONS }
            ?: DEFAULT_STRETCH_SECONDS
        restSeconds = preferences.getInt(PREF_REST_SECONDS, DEFAULT_REST_SECONDS)
            .coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        binding.voiceSwitch.isChecked = preferences.getBoolean(PREF_VOICE_ENABLED, false)
        checkStretchButton(stretchSeconds)
    }

    private fun saveConfig() {
        preferences.edit()
            .putInt(PREF_STRETCH_SECONDS, stretchSeconds)
            .putInt(PREF_REST_SECONDS, restSeconds)
            .putBoolean(PREF_VOICE_ENABLED, binding.voiceSwitch.isChecked)
            .apply()
    }

    private fun renderUi() {
        binding.restSecondsText.text = getString(R.string.stretching_rest_seconds_template, restSeconds)
        binding.stretchingTimerText.text = formatSeconds(remainingSeconds)
        binding.stretchingPhaseBadge.text = when (currentPhase) {
            StretchPhase.STRETCH -> getString(R.string.stretching_phase_stretch)
            StretchPhase.REST -> getString(R.string.stretching_phase_rest)
            StretchPhase.IDLE -> getString(R.string.stretching_phase_idle)
        }
        binding.stretchingSubtitleText.text = when (currentPhase) {
            StretchPhase.STRETCH -> getString(R.string.stretching_stretch_subtitle, restSeconds)
            StretchPhase.REST -> getString(R.string.stretching_rest_subtitle, stretchSeconds)
            StretchPhase.IDLE -> getString(R.string.stretching_idle_subtitle, stretchSeconds, restSeconds)
        }
        binding.stretchingPhaseProgress.progress = if (currentPhaseDuration <= 0) {
            0
        } else {
            (((currentPhaseDuration - remainingSeconds).coerceAtLeast(0) * 100f) /
                currentPhaseDuration).toInt()
        }

        val phaseBg = when (currentPhase) {
            StretchPhase.STRETCH -> R.color.stretching_stretch_bg
            StretchPhase.REST -> R.color.stretching_rest_bg
            StretchPhase.IDLE -> R.color.stretching_idle_bg
        }
        binding.stretchingHeroCard.setCardBackgroundColor(ContextCompat.getColor(this, phaseBg))
        binding.stretchingPhaseBadge.backgroundTintList =
            ContextCompat.getColorStateList(this, phaseBg)
        binding.stretchingStartButton.isEnabled = !isTimerRunning
        binding.restMinusButton.isEnabled = restSeconds > MIN_REST_SECONDS
        binding.restPlusButton.isEnabled = restSeconds < MAX_REST_SECONDS
    }

    private fun formatSeconds(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return getString(R.string.hiit_timer_format, safeSeconds / 60, safeSeconds % 60)
    }

    private enum class StretchPhase(private val serviceValue: String) {
        IDLE(StretchingTimerService.PHASE_IDLE),
        STRETCH(StretchingTimerService.PHASE_STRETCH),
        REST(StretchingTimerService.PHASE_REST);

        companion object {
            fun fromServiceValue(value: String?): StretchPhase {
                return entries.firstOrNull { it.serviceValue == value } ?: IDLE
            }
        }
    }

    companion object {
        private const val DEFAULT_STRETCH_SECONDS = 30
        private const val DEFAULT_REST_SECONDS = 5
        private const val MIN_REST_SECONDS = 1
        private const val MAX_REST_SECONDS = 120
        private const val REST_STEP_SECONDS = 1
        private val STRETCH_SECOND_OPTIONS = setOf(15, 30, 45, 60, 90, 120)
        private const val PREFERENCES_NAME = "stretching_preferences"
        private const val PREF_STRETCH_SECONDS = "stretch_seconds"
        private const val PREF_REST_SECONDS = "rest_seconds"
        private const val PREF_VOICE_ENABLED = "voice_enabled"
    }
}
