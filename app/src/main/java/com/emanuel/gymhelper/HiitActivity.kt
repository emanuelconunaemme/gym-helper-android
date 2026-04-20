package com.emanuel.gymhelper

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.emanuel.gymhelper.data.tracker.WorkoutTrackerRepository
import com.emanuel.gymhelper.databinding.ActivityHiitBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil

class HiitActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityHiitBinding
    private lateinit var trackerRepository: WorkoutTrackerRepository

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private var programId: Long = -1L
    private var weekNumber: Int = 1

    private var completedCycles = 0
    private var currentCycleNumber = 1
    private var currentPhase = HiitPhase.IDLE
    private var remainingSeconds = RUN_SECONDS
    private var currentPhaseDuration = RUN_SECONDS
    private var activeTimer: CountDownTimer? = null
    private var isPaused = false
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        programId = intent.getLongExtra(EXTRA_PROGRAM_ID, -1L)
        weekNumber = intent.getIntExtra(EXTRA_WEEK_NUMBER, 1)
        if (programId <= 0L) {
            finish()
            return
        }

        trackerRepository = AppDependencies.trackerRepository(this)
        textToSpeech = TextToSpeech(this, this)

        setupUi()
        lifecycleScope.launch {
            completedCycles = withContext(Dispatchers.IO) {
                trackerRepository.getHiitProgress(programId, weekNumber).completedCycles
            }
            resetSessionState(keepCompletedCycles = true)
            renderUi()
        }
    }

    override fun onDestroy() {
        activeTimer?.cancel()
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

    private fun setupUi() {
        binding.hiitToolbar.setNavigationOnClickListener { finish() }
        binding.hiitPrimaryButton.setOnClickListener {
            when {
                activeTimer != null -> pauseTimer()
                isPaused -> resumeTimer()
                else -> startSession()
            }
        }
        binding.hiitResetButton.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    trackerRepository.updateHiitCompletedCycles(programId, weekNumber, 0)
                }
                completedCycles = 0
                resetSessionState(keepCompletedCycles = false)
                renderUi()
            }
        }
    }

    private fun startSession() {
        if (completedCycles >= TOTAL_CYCLES && currentPhase == HiitPhase.COMPLETE) {
            resetSessionState(keepCompletedCycles = false)
        }
        if (currentPhase == HiitPhase.IDLE) {
            currentCycleNumber = (completedCycles + 1).coerceAtMost(TOTAL_CYCLES)
            currentPhase = HiitPhase.RUN
            currentPhaseDuration = RUN_SECONDS
            remainingSeconds = RUN_SECONDS
            announce(getString(R.string.hiit_start_running_cycle, currentCycleNumber))
        }
        isPaused = false
        startTimer()
        renderUi()
    }

    private fun resumeTimer() {
        isPaused = false
        startTimer()
        renderUi()
    }

    private fun pauseTimer() {
        activeTimer?.cancel()
        activeTimer = null
        isPaused = true
        renderUi()
    }

    private fun startTimer() {
        activeTimer?.cancel()
        activeTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                maybeAnnounceRemaining()
                renderUi()
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
            HiitPhase.RUN -> {
                currentPhase = HiitPhase.RECOVER
                currentPhaseDuration = RECOVER_SECONDS
                remainingSeconds = RECOVER_SECONDS
                announce(getString(R.string.hiit_rest_prompt))
                startTimer()
            }
            HiitPhase.RECOVER -> {
                completedCycles = (completedCycles + 1).coerceAtMost(TOTAL_CYCLES)
                persistCompletedCycles()
                if (completedCycles >= TOTAL_CYCLES) {
                    currentPhase = HiitPhase.COMPLETE
                    currentPhaseDuration = RUN_SECONDS
                    remainingSeconds = 0
                    isPaused = false
                    renderUi()
                    return
                }

                currentCycleNumber = completedCycles + 1
                currentPhase = HiitPhase.RUN
                currentPhaseDuration = RUN_SECONDS
                remainingSeconds = RUN_SECONDS
                announce(getString(R.string.hiit_start_running_cycle, currentCycleNumber))
                startTimer()
            }
            else -> Unit
        }
        renderUi()
    }

    private fun persistCompletedCycles() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                trackerRepository.updateHiitCompletedCycles(programId, weekNumber, completedCycles)
            }
        }
    }

    private fun maybeAnnounceRemaining() {
        when (currentPhase) {
            HiitPhase.RUN -> {
                if (remainingSeconds == 180 || remainingSeconds == 120 || remainingSeconds == 60) {
                    val minutes = remainingSeconds / 60
                    announce(
                        if (minutes == 1) {
                            getString(R.string.hiit_run_remaining, minutes)
                        } else {
                            getString(R.string.hiit_run_remaining_plural, minutes)
                        }
                    )
                }
            }
            HiitPhase.RECOVER -> {
                if (remainingSeconds == 120 || remainingSeconds == 60) {
                    val minutes = remainingSeconds / 60
                    announce(
                        if (minutes == 1) {
                            getString(R.string.hiit_recover_remaining, minutes)
                        } else {
                            getString(R.string.hiit_recover_remaining_plural, minutes)
                        }
                    )
                }
            }
            else -> Unit
        }
    }

    private fun announce(message: String) {
        if (ttsReady) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, message)
        }
    }

    private fun playBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    }

    private fun resetSessionState(keepCompletedCycles: Boolean) {
        activeTimer?.cancel()
        activeTimer = null
        isPaused = false
        if (!keepCompletedCycles) {
            completedCycles = 0
        }
        currentCycleNumber = (completedCycles + 1).coerceAtMost(TOTAL_CYCLES)
        currentPhase = if (completedCycles >= TOTAL_CYCLES) HiitPhase.COMPLETE else HiitPhase.IDLE
        currentPhaseDuration = RUN_SECONDS
        remainingSeconds = if (currentPhase == HiitPhase.COMPLETE) 0 else RUN_SECONDS
    }

    private fun renderUi() {
        val phaseLabel = when (currentPhase) {
            HiitPhase.RUN -> getString(R.string.hiit_phase_run)
            HiitPhase.RECOVER -> getString(R.string.hiit_phase_recover)
            HiitPhase.COMPLETE -> getString(R.string.hiit_complete_title)
            HiitPhase.IDLE -> getString(R.string.hiit_phase_idle)
        }
        binding.hiitPhaseBadge.text = phaseLabel
        binding.hiitTimerText.text = if (currentPhase == HiitPhase.COMPLETE) {
            getString(R.string.hiit_complete_title)
        } else {
            formatSeconds(remainingSeconds)
        }
        binding.hiitCycleText.text = if (currentPhase == HiitPhase.COMPLETE) {
            getString(R.string.hiit_completed_template, completedCycles)
        } else {
            getString(
                R.string.hiit_cycle_template,
                currentCycleNumber.coerceAtMost(TOTAL_CYCLES)
            )
        }
        binding.hiitSubtitleText.text = if (currentPhase == HiitPhase.COMPLETE) {
            getString(R.string.hiit_complete_subtitle)
        } else {
            getString(R.string.hiit_completed_template, completedCycles)
        }

        val progressPercent = if (currentPhaseDuration <= 0) {
            0
        } else {
            (((currentPhaseDuration - remainingSeconds).coerceAtLeast(0) * 100f) /
                currentPhaseDuration).toInt()
        }
        binding.hiitPhaseProgress.progress = progressPercent

        val phaseBg = when (currentPhase) {
            HiitPhase.RUN -> R.color.hiit_run_bg
            HiitPhase.RECOVER -> R.color.hiit_recover_bg
            HiitPhase.COMPLETE -> R.color.card_bg_done
            HiitPhase.IDLE -> R.color.hiit_idle_bg
        }
        binding.hiitHeroCard.setCardBackgroundColor(ContextCompat.getColor(this, phaseBg))
        binding.hiitPhaseBadge.backgroundTintList =
            ContextCompat.getColorStateList(this, phaseBg)

        val cycleViews = listOf(
            binding.cycleOneText,
            binding.cycleTwoText,
            binding.cycleThreeText,
            binding.cycleFourText
        )
        cycleViews.forEachIndexed { index, textView ->
            val cycleNumber = index + 1
            val colorRes = when {
                cycleNumber <= completedCycles -> R.color.status_done
                currentPhase != HiitPhase.IDLE &&
                    currentPhase != HiitPhase.COMPLETE &&
                    cycleNumber == currentCycleNumber -> R.color.status_ongoing
                else -> R.color.status_pending
            }
            textView.backgroundTintList = ContextCompat.getColorStateList(this, colorRes)
            textView.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (colorRes == R.color.status_pending) R.color.text_primary else R.color.white
                )
            )
        }

        binding.hiitPrimaryButton.text = when {
            activeTimer != null -> getString(R.string.hiit_pause_button)
            isPaused -> getString(R.string.hiit_resume_button)
            else -> getString(R.string.hiit_start_button)
        }
    }

    private fun formatSeconds(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return getString(R.string.hiit_timer_format, safeSeconds / 60, safeSeconds % 60)
    }

    private enum class HiitPhase {
        IDLE,
        RUN,
        RECOVER,
        COMPLETE
    }

    companion object {
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_WEEK_NUMBER = "week_number"
        private const val TOTAL_CYCLES = 4
        private const val RUN_SECONDS = 4 * 60
        private const val RECOVER_SECONDS = 3 * 60
    }
}
