package com.emanuel.gymhelper

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.emanuel.gymhelper.data.local.room.model.IntensityType
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus
import com.emanuel.gymhelper.data.tracker.ExerciseTrackerState
import com.emanuel.gymhelper.data.tracker.ProgramTrackerState
import com.emanuel.gymhelper.data.tracker.TrainingTrackerState
import com.emanuel.gymhelper.data.tracker.WorkoutTrackerRepository
import com.emanuel.gymhelper.databinding.ActivityTrainingDetailBinding
import com.emanuel.gymhelper.ui.ExerciseSessionAdapter
import com.emanuel.gymhelper.ui.ExerciseSessionItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class TrainingDetailActivity : AppCompatActivity(), ExerciseSessionAdapter.Listener {

    private lateinit var binding: ActivityTrainingDetailBinding
    private lateinit var trackerRepository: WorkoutTrackerRepository

    private val sessionAdapter = ExerciseSessionAdapter(this)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private var selectedTrainingId: Long = -1L
    private var currentProgramState: ProgramTrackerState? = null
    private var currentTrainingState: TrainingTrackerState? = null
    private var sessionItems: List<ExerciseSessionItem> = emptyList()

    private var expandedExerciseProgressId: Long? = null

    private var activeTimer: CountDownTimer? = null
    private var activeTimerExerciseProgressId: Long? = null
    private var timerRemainingSeconds: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedTrainingId = intent.getLongExtra(EXTRA_TRAINING_ID, -1L)
        if (selectedTrainingId <= 0) {
            finish()
            return
        }

        trackerRepository = AppDependencies.trackerRepository(this)

        setupUi()
        lifecycleScope.launch {
            if (!refreshTrainingState()) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
        toneGenerator.release()
    }

    private fun setupUi() {
        binding.detailToolbar.setNavigationOnClickListener { finish() }

        binding.exerciseRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TrainingDetailActivity)
            adapter = sessionAdapter
        }
    }

    private suspend fun refreshTrainingState(): Boolean {
        val loaded = withContext(Dispatchers.IO) {
            val programState = trackerRepository.loadProgramState() ?: return@withContext null
            val weekNumber = programState.programProgress.currentWeek
            val training = programState.program.trainings.firstOrNull {
                it.training.trainingId == selectedTrainingId
            } ?: return@withContext null

            val trainingState = trackerRepository.loadTrainingState(
                programId = programState.program.program.programId,
                training = training,
                weekNumber = weekNumber
            )

            LoadedTrainingState(
                programState = programState,
                trainingName = training.training.name,
                trainingState = trainingState
            )
        } ?: return false

        currentProgramState = loaded.programState
        currentTrainingState = loaded.trainingState

        val allowedIds = loaded.trainingState.exerciseStates
            .filter { it.weekPlan != null }
            .map { it.progress.exerciseProgressId }
            .toSet()

        if (expandedExerciseProgressId != null && expandedExerciseProgressId !in allowedIds) {
            expandedExerciseProgressId = null
        }

        binding.trainingTitleText.text = loaded.trainingName

        val visibleExercises = loaded.trainingState.exerciseStates.filter { it.weekPlan != null }
        val totalSets = visibleExercises.sumOf { it.progress.plannedSets }
        val resolvedSets = visibleExercises.sumOf { it.progress.completedSets + it.progress.skippedSets }
        val progressPercent = if (totalSets <= 0) {
            0
        } else {
            ((resolvedSets * 100f) / totalSets).toInt()
        }
        binding.trainingProgressCircle.progress = progressPercent
        binding.trainingProgressPercentText.text = getString(
            R.string.progress_percent_template,
            progressPercent
        )

        renderSessionItems()
        return true
    }

    private fun renderSessionItems() {
        val trainingState = currentTrainingState ?: return

        val ongoingExerciseId = ongoingExerciseProgressId()
        sessionItems = trainingState.exerciseStates
            .filter { it.weekPlan != null }
            .map { exerciseState ->
                val progress = exerciseState.progress
                val currentWeight = progress.weightText ?: exerciseState.lastSessionWeightText
                ExerciseSessionItem(
                    exerciseProgressId = progress.exerciseProgressId,
                    name = exerciseState.exercise.exerciseType.name,
                    setsReps = formatSetReps(exerciseState),
                    setProgressText = getString(
                        R.string.set_progress_fraction_template,
                        (progress.completedSets + progress.skippedSets).toString(),
                        progress.plannedSets.toString()
                    ),
                    intensityType = exerciseState.exercise.exercise.intensityType.dbValue,
                    isDone = progress.status == ProgressStatus.DONE,
                    isSkipped = progress.status == ProgressStatus.SKIPPED,
                    isOngoing = progress.status == ProgressStatus.PENDING &&
                        progress.exerciseProgressId == ongoingExerciseId,
                    currentWeight = currentWeight,
                    timerRemainingSeconds = if (progress.exerciseProgressId == activeTimerExerciseProgressId) {
                        timerRemainingSeconds
                    } else {
                        null
                    },
                    expanded = expandedExerciseProgressId == progress.exerciseProgressId
                )
            }

        sessionAdapter.submitItems(sessionItems)
    }

    override fun onExerciseCardTapped(exerciseProgressId: Long) {
        expandedExerciseProgressId =
            if (expandedExerciseProgressId == exerciseProgressId) null else exerciseProgressId
        renderSessionItems()
    }

    override fun onEditWeight(exerciseProgressId: Long) {
        val item = sessionItems.firstOrNull { it.exerciseProgressId == exerciseProgressId } ?: return

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(item.currentWeight.orEmpty())
            setSelection(text.length)
            hint = getString(R.string.current_weight_hint)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_weight_title, item.name))
            .setView(input)
            .setPositiveButton(R.string.save_weight_button) { _, _ ->
                val weight = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        trackerRepository.saveWeight(exerciseProgressId, weight)
                    }
                    expandedExerciseProgressId = exerciseProgressId
                    refreshTrainingState()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        dialog.setOnShowListener {
            input.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onFinishSet(exerciseProgressId: Long) {
        if (activeTimer != null) {
            Toast.makeText(this, getString(R.string.timer_already_running), Toast.LENGTH_SHORT).show()
            return
        }

        val trainingState = currentTrainingState ?: return
        val exerciseState = trainingState.exerciseStates
            .firstOrNull { it.progress.exerciseProgressId == exerciseProgressId }
            ?: return

        val hasPendingSet = exerciseState.setProgress.any { it.status == ProgressStatus.PENDING }
        if (!hasPendingSet) {
            Toast.makeText(this, getString(R.string.exercise_no_pending_sets), Toast.LENGTH_SHORT).show()
            return
        }

        val pendingSetIndex = exerciseState.setProgress
            .firstOrNull { it.status == ProgressStatus.PENDING }
            ?.setIndex
            ?: return
        val restSeconds = restSecondsAfterSet(exerciseState, pendingSetIndex)
        val weightText = sessionItems
            .firstOrNull { it.exerciseProgressId == exerciseProgressId }
            ?.currentWeight
            .orEmpty()

        lifecycleScope.launch {
            val action = withContext(Dispatchers.IO) {
                trackerRepository.finishNextSetWithRest(exerciseProgressId, weightText)
            }

            expandedExerciseProgressId = exerciseProgressId
            if (!refreshTrainingState()) return@launch

            val moveToNextExercise: () -> Unit = {
                if (action.exerciseFinished) {
                    expandedExerciseProgressId = findNextPendingExercise(exerciseProgressId)
                    renderSessionItems()
                }
            }

            if (restSeconds > 0) {
                startRestTimer(exerciseProgressId, restSeconds, moveToNextExercise)
            } else {
                moveToNextExercise()
            }
        }
    }

    override fun onSkipExercise(exerciseProgressId: Long) {
        if (activeTimer != null) {
            Toast.makeText(this, getString(R.string.timer_already_running), Toast.LENGTH_SHORT).show()
            return
        }

        val weightText = sessionItems
            .firstOrNull { it.exerciseProgressId == exerciseProgressId }
            ?.currentWeight
            .orEmpty()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                trackerRepository.skipExercise(exerciseProgressId, weightText)
            }
            if (!refreshTrainingState()) return@launch

            expandedExerciseProgressId = findNextPendingExercise(exerciseProgressId)
            renderSessionItems()
        }
    }

    private fun startRestTimer(
        exerciseProgressId: Long,
        seconds: Int,
        onFinished: () -> Unit
    ) {
        cancelTimer()

        if (seconds <= 0) {
            onFinished()
            return
        }

        activeTimerExerciseProgressId = exerciseProgressId
        timerRemainingSeconds = seconds
        renderSessionItems()

        val durationMs = seconds * 1000L
        activeTimer = object : CountDownTimer(durationMs, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                timerRemainingSeconds = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                renderSessionItems()
            }

            override fun onFinish() {
                activeTimer = null
                activeTimerExerciseProgressId = null
                timerRemainingSeconds = null
                renderSessionItems()
                playTimerBeep()
                onFinished()
            }
        }.start()
    }

    private fun cancelTimer() {
        activeTimer?.cancel()
        activeTimer = null
        activeTimerExerciseProgressId = null
        timerRemainingSeconds = null
    }

    private fun playTimerBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
    }

    private fun ongoingExerciseProgressId(): Long? {
        activeTimerExerciseProgressId?.let { return it }
        expandedExerciseProgressId?.let { return it }

        return currentTrainingState?.exerciseStates
            ?.asSequence()
            ?.filter { it.weekPlan != null }
            ?.firstOrNull { it.progress.status == ProgressStatus.PENDING }
            ?.progress
            ?.exerciseProgressId
    }

    private fun findNextPendingExercise(currentExerciseProgressId: Long): Long? {
        if (sessionItems.isEmpty()) return null

        val currentIndex = sessionItems.indexOfFirst {
            it.exerciseProgressId == currentExerciseProgressId
        }

        val nextAfterCurrent = sessionItems
            .drop((currentIndex + 1).coerceAtLeast(0))
            .firstOrNull { !it.isDone && !it.isSkipped }

        return nextAfterCurrent?.exerciseProgressId
            ?: sessionItems.firstOrNull { !it.isDone && !it.isSkipped }?.exerciseProgressId
    }

    private fun formatSetReps(exerciseState: ExerciseTrackerState): String {
        val setGroups = exerciseState.weekPlan?.setRepetitions
            ?.sortedBy { it.sortOrder }
            ?.joinToString(separator = getString(R.string.week_set_separator)) { setGroup ->
                if (setGroup.repetitionsMin == setGroup.repetitionsMax) {
                    getString(
                        R.string.week_set_format_single,
                        setGroup.setCount.toString(),
                        setGroup.repetitionsMin.toString()
                    )
                } else {
                    getString(
                        R.string.week_set_format_range,
                        setGroup.setCount.toString(),
                        setGroup.repetitionsMin.toString(),
                        setGroup.repetitionsMax.toString()
                    )
                }
            }

        return setGroups ?: getString(R.string.sets_reps_empty)
    }

    private fun restSecondsAfterSet(exerciseState: ExerciseTrackerState, pendingSetIndex: Int): Int {
        val normalRest = exerciseState.exercise.exercise.restSeconds.coerceAtLeast(0)
        if (exerciseState.exercise.exercise.intensityType != IntensityType.REST_PAUSE_2X) {
            return normalRest
        }

        val baseSetCount = exerciseState.weekPlan?.setRepetitions?.sumOf { it.setCount } ?: 0
        if (baseSetCount <= 0) return normalRest

        return when {
            pendingSetIndex < baseSetCount -> normalRest
            pendingSetIndex == baseSetCount -> REST_PAUSE_REST_SECONDS
            pendingSetIndex == baseSetCount + 1 -> REST_PAUSE_REST_SECONDS
            else -> normalRest
        }
    }

    companion object {
        const val EXTRA_TRAINING_ID = "training_id"
        private const val REST_PAUSE_REST_SECONDS = 15
    }

    private data class LoadedTrainingState(
        val programState: ProgramTrackerState,
        val trainingName: String,
        val trainingState: TrainingTrackerState
    )
}
