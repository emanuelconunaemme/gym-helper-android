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

    private enum class ExerciseAction {
        SKIP,
        RESET,
        DONE
    }

    private lateinit var binding: ActivityTrainingDetailBinding
    private lateinit var trackerRepository: WorkoutTrackerRepository

    private val sessionAdapter = ExerciseSessionAdapter(this)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private var selectedTrainingId: Long = -1L
    private var currentProgramState: ProgramTrackerState? = null
    private var currentTrainingState: TrainingTrackerState? = null
    private var sessionItems: List<ExerciseSessionItem> = emptyList()

    private var expandedExerciseProgressId: Long? = null
    private var showRemainingSetCount = false
    private var totalSetsForHeader = 0
    private var resolvedSetsForHeader = 0

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
        binding.trainingProgressContainer.setOnClickListener {
            showRemainingSetCount = !showRemainingSetCount
            renderTrainingProgressHeader()
        }

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
                weekNumber = weekNumber,
                programNumberOfWeeks = programState.program.program.numberOfWeeks
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
        totalSetsForHeader = visibleExercises.sumOf { it.progress.plannedSets }
        resolvedSetsForHeader = visibleExercises.sumOf {
            it.progress.completedSets + it.progress.skippedSets
        }
        renderTrainingProgressHeader()

        renderSessionItems()
        return true
    }

    private fun renderTrainingProgressHeader() {
        val progressPercent = if (totalSetsForHeader <= 0) {
            0
        } else {
            ((resolvedSetsForHeader * 100f) / totalSetsForHeader).toInt()
        }
        val remainingSets = (totalSetsForHeader - resolvedSetsForHeader).coerceAtLeast(0)

        binding.trainingProgressCircle.setProgressCompat(progressPercent, true)
        binding.trainingProgressPercentText.text = if (showRemainingSetCount) {
            remainingSets.toString()
        } else {
            getString(R.string.progress_percent_template, progressPercent)
        }
    }

    private fun renderSessionItems() {
        val trainingState = currentTrainingState ?: return

        val ongoingExerciseId = ongoingExerciseProgressId()
        val visibleExerciseStates = trainingState.exerciseStates
            .filter { it.weekPlan != null }
        val unresolvedExercises = visibleExerciseStates.filter {
            it.progress.status == ProgressStatus.PENDING
        }
        val currentExercise = unresolvedExercises.firstOrNull {
            it.progress.exerciseProgressId == ongoingExerciseId
        }
        val unresolvedOrdered = buildList {
            if (currentExercise != null) {
                add(currentExercise)
            }
            addAll(
                unresolvedExercises.filterNot {
                    it.progress.exerciseProgressId == currentExercise?.progress?.exerciseProgressId
                }
            )
        }
        val completedOrdered = visibleExerciseStates.filter {
            it.progress.status != ProgressStatus.PENDING
        }
        val orderedStates = unresolvedOrdered + completedOrdered
        val firstCompletedExerciseId = completedOrdered.firstOrNull()?.progress?.exerciseProgressId

        sessionItems = orderedStates.map { exerciseState ->
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
                    plannedSets = progress.plannedSets,
                    intensityType = exerciseState.effectiveIntensityType.dbValue,
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
                    expanded = expandedExerciseProgressId == progress.exerciseProgressId,
                    showCompletedDivider = progress.exerciseProgressId == firstCompletedExerciseId &&
                        unresolvedOrdered.isNotEmpty()
                )
            }

        sessionAdapter.submitItems(sessionItems)
    }

    override fun onExerciseCardTapped(exerciseProgressId: Long) {
        expandedExerciseProgressId =
            if (expandedExerciseProgressId == exerciseProgressId) null else exerciseProgressId
        renderSessionItems()
    }

    override fun onExerciseCardLongPressed(exerciseProgressId: Long) {
        showExerciseActionsMenu(exerciseProgressId)
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

    private fun showExerciseActionsMenu(exerciseProgressId: Long) {
        if (activeTimer != null) {
            Toast.makeText(this, getString(R.string.timer_already_running), Toast.LENGTH_SHORT).show()
            return
        }

        val item = sessionItems.firstOrNull { it.exerciseProgressId == exerciseProgressId } ?: return
        val actions = buildList {
            if (!item.isDone) add(ExerciseAction.SKIP)
            if (item.plannedSets > 0) add(ExerciseAction.RESET)
            if (!item.isDone) add(ExerciseAction.DONE)
        }
        if (actions.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(actions.map { actionLabel(it) }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                val selectedAction = actions[which]
                lifecycleScope.launch {
                    when (selectedAction) {
                        ExerciseAction.SKIP -> withContext(Dispatchers.IO) {
                            trackerRepository.skipExercise(
                                exerciseProgressId,
                                item.currentWeight.orEmpty()
                            )
                        }
                        ExerciseAction.RESET -> withContext(Dispatchers.IO) {
                            trackerRepository.resetExercise(exerciseProgressId)
                        }
                        ExerciseAction.DONE -> withContext(Dispatchers.IO) {
                            trackerRepository.markExerciseDone(
                                exerciseProgressId,
                                item.currentWeight.orEmpty()
                            )
                        }
                    }

                    if (!refreshTrainingState()) return@launch

                    expandedExerciseProgressId = if (selectedAction == ExerciseAction.RESET) {
                        exerciseProgressId
                    } else {
                        findNextPendingExercise(exerciseProgressId)
                    }
                    renderSessionItems()
                }
            }
            .show()
    }

    private fun actionLabel(action: ExerciseAction): String {
        return when (action) {
            ExerciseAction.SKIP -> getString(R.string.exercise_action_skip)
            ExerciseAction.RESET -> getString(R.string.exercise_action_reset)
            ExerciseAction.DONE -> getString(R.string.exercise_action_done)
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

            val hasPendingExercises = sessionItems.any { !it.isDone && !it.isSkipped }
            if (action.exerciseFinished && !hasPendingExercises) {
                expandedExerciseProgressId = null
                renderSessionItems()
                return@launch
            }

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
        val normalRest = exerciseState.effectiveRestSeconds.coerceAtLeast(0)
        if (exerciseState.effectiveIntensityType != IntensityType.REST_PAUSE_2X) {
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
