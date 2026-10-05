package com.emanuel.gymhelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.emanuel.gymhelper.data.importer.ProgramJsonImporter
import com.emanuel.gymhelper.data.local.room.GymHelperDatabase
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus
import com.emanuel.gymhelper.data.tracker.ProgramTrackerState
import com.emanuel.gymhelper.data.tracker.WorkoutTrackerRepository
import com.emanuel.gymhelper.databinding.ActivityMainBinding
import com.emanuel.gymhelper.ui.TrainingMenuAdapter
import com.emanuel.gymhelper.ui.TrainingMenuItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private enum class TrainingAction {
        SKIP,
        RESET,
        DONE
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: GymHelperDatabase
    private lateinit var importer: ProgramJsonImporter
    private lateinit var trackerRepository: WorkoutTrackerRepository

    private val trainingAdapter = TrainingMenuAdapter(
        onTrainingClicked = { trainingId -> openTrainingDetail(trainingId) },
        onTrainingLongPressed = { trainingProgressId -> showTrainingActionsMenu(trainingProgressId) }
    )

    private var currentProgramState: ProgramTrackerState? = null
    private val localJsonPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadNewWaveFromPickedFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDependencies.database(this)
        importer = AppDependencies.importer(this)
        trackerRepository = AppDependencies.trackerRepository(this)

        setupUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.mainDrawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        loadStartState()
    }

    override fun onResume() {
        super.onResume()
        if (currentProgramState != null) {
            lifecycleScope.launch {
                loadProgramAndRender()
            }
        }
    }

    private fun setupUi() {
        binding.topToolbar.title = ""
        binding.topToolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_24)
        binding.topToolbar.setNavigationOnClickListener {
            binding.mainDrawerLayout.openDrawer(GravityCompat.START)
        }

        binding.trainingRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = trainingAdapter
        }

        binding.importStatusText.text = getString(R.string.import_status_ready)

        binding.loadLocalButton.setOnClickListener {
            importProgram(getString(R.string.import_status_loading_local)) {
                assets.open(LOCAL_PROGRAM_ASSET).bufferedReader().use { it.readText() }
            }
        }
        binding.loadUrlButton.setOnClickListener {
            val url = binding.importUrlEditText.text?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                binding.importStatusText.text = getString(R.string.import_url_required)
                return@setOnClickListener
            }
            importProgram(getString(R.string.import_status_loading_url)) {
                URL(url).openConnection().run {
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                    getInputStream().bufferedReader().use { it.readText() }
                }
            }
        }

        binding.prevWeekButton.setOnClickListener { moveWeek(-1) }
        binding.nextWeekButton.setOnClickListener { moveWeek(1) }
        binding.hiitCard.setOnClickListener {
            openHiit()
        }
        binding.stretchingCard.setOnClickListener {
            openStretching()
        }
        binding.loadNewWaveAction.setOnClickListener {
            showLoadNewWaveOptions()
            binding.mainDrawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.openWhoopAction.setOnClickListener {
            openWhoopApp()
        }
        binding.openCitysportsAction.setOnClickListener {
            openCitysportsApp()
        }

        bindWhoopIconIfAvailable()
        bindCitysportsIconIfAvailable()
    }

    private fun loadStartState() {
        lifecycleScope.launch {
            val hasProgram = withContext(Dispatchers.IO) {
                database.programDao().getProgramCount() > 0
            }
            if (hasProgram) {
                loadProgramAndRender()
            } else {
                showImportUi()
            }
        }
    }

    private fun importProgram(loadingMessage: String, loadJson: suspend () -> String) {
        setImportLoading(true)
        if (binding.importContainer.visibility == View.VISIBLE) {
            binding.importStatusText.text = loadingMessage
        } else {
            Toast.makeText(this, loadingMessage, Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { loadJson() }
                withContext(Dispatchers.IO) { importer.import(json) }
                if (binding.importContainer.visibility == View.VISIBLE) {
                    binding.importStatusText.text = getString(R.string.import_status_success)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.new_wave_loaded_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                loadProgramAndRender()
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                if (binding.importContainer.visibility == View.VISIBLE) {
                    binding.importStatusText.text = getString(R.string.import_status_error, message)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_status_error, message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                showImportUi()
            } finally {
                setImportLoading(false)
            }
        }
    }

    private fun moveWeek(delta: Int) {
        val state = currentProgramState ?: return

        lifecycleScope.launch {
            val program = state.program.program
            val currentWeek = state.programProgress.currentWeek
            withContext(Dispatchers.IO) {
                trackerRepository.updateProgramWeek(
                    programId = program.programId,
                    numberOfWeeks = program.numberOfWeeks,
                    requestedWeek = currentWeek + delta
                )
            }
            loadProgramAndRender()
        }
    }

    private suspend fun loadProgramAndRender() {
        val programState = withContext(Dispatchers.IO) {
            trackerRepository.loadProgramState()
        }
        if (programState == null) {
            showImportUi()
            return
        }

        currentProgramState = programState
        showContentUi()
        bindProgramHeader(programState)
        bindHiitCard(programState)

        val menuItems = buildTrainingMenuItems(programState)

        trainingAdapter.submitItems(menuItems)
        binding.trainingRecyclerView.scheduleLayoutAnimation()
    }

    private suspend fun buildTrainingMenuItems(programState: ProgramTrackerState): List<TrainingMenuItem> {
        return withContext(Dispatchers.IO) {
            val programId = programState.program.program.programId
            val weekNumber = programState.programProgress.currentWeek

            programState.program.trainings.map { training ->
                val trainingState = trackerRepository.loadTrainingState(
                    programId = programId,
                    training = training,
                    weekNumber = weekNumber,
                    programNumberOfWeeks = programState.program.program.numberOfWeeks
                )
                val weekExercises = trainingState.exerciseStates.filter { it.weekPlan != null }
                val plannedSets = weekExercises.sumOf { it.progress.plannedSets }
                val doneExercises = weekExercises.count { it.progress.status == ProgressStatus.DONE }
                val skippedExercises = weekExercises.count { it.progress.status == ProgressStatus.SKIPPED }

                TrainingMenuItem(
                    trainingProgressId = trainingState.trainingProgress.trainingProgressId,
                    trainingId = training.training.trainingId,
                    title = training.training.name,
                    plannedSets = plannedSets,
                    doneExercises = doneExercises,
                    skippedExercises = skippedExercises,
                    totalExercises = weekExercises.size,
                    status = trainingState.trainingProgress.status
                )
            }
        }
    }

    private fun showTrainingActionsMenu(trainingProgressId: Long) {
        lifecycleScope.launch {
            val programState = currentProgramState ?: return@launch
            val item = buildTrainingMenuItems(programState).firstOrNull {
                it.trainingProgressId == trainingProgressId
            } ?: return@launch

            val actions = buildList {
                if (item.status != ProgressStatus.DONE) add(TrainingAction.SKIP)
                if (item.plannedSets > 0) add(TrainingAction.RESET)
                if (item.status != ProgressStatus.DONE) add(TrainingAction.DONE)
            }
            if (actions.isEmpty()) return@launch

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(item.title)
                .setItems(actions.map { actionLabel(it) }.toTypedArray()) { dialog, which ->
                    dialog.dismiss()
                    val selectedAction = actions[which]
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            when (selectedAction) {
                                TrainingAction.SKIP -> trackerRepository.skipTraining(item.trainingProgressId)
                                TrainingAction.RESET -> trackerRepository.resetTraining(item.trainingProgressId)
                                TrainingAction.DONE -> trackerRepository.markTrainingDone(item.trainingProgressId)
                            }
                        }
                        loadProgramAndRender()
                    }
                }
                .show()
        }
    }

    private fun actionLabel(action: TrainingAction): String {
        return when (action) {
            TrainingAction.SKIP -> getString(R.string.exercise_action_skip)
            TrainingAction.RESET -> getString(R.string.exercise_action_reset)
            TrainingAction.DONE -> getString(R.string.exercise_action_done)
        }
    }

    private fun bindProgramHeader(programState: ProgramTrackerState) {
        val totalWeeks = programState.program.program.numberOfWeeks + 1
        binding.programNameText.text = programState.program.program.name
        binding.currentWeekText.text = getString(
            R.string.week_label_with_total_template,
            programState.programProgress.currentWeek.toString(),
            totalWeeks.toString()
        )
        binding.prevWeekButton.isEnabled = programState.programProgress.currentWeek > 1
        binding.nextWeekButton.isEnabled =
            programState.programProgress.currentWeek < totalWeeks
    }

    private fun openTrainingDetail(trainingId: Long) {
        startActivity(
            Intent(this, TrainingDetailActivity::class.java)
                .putExtra(TrainingDetailActivity.EXTRA_TRAINING_ID, trainingId)
        )
    }

    private fun openHiit() {
        val programState = currentProgramState ?: return
        startActivity(
            Intent(this, HiitActivity::class.java)
                .putExtra(HiitActivity.EXTRA_PROGRAM_ID, programState.program.program.programId)
                .putExtra(HiitActivity.EXTRA_WEEK_NUMBER, programState.programProgress.currentWeek)
        )
    }

    private fun openStretching() {
        startActivity(Intent(this, StretchingActivity::class.java))
    }

    private fun showImportUi() {
        binding.importContainer.visibility = View.VISIBLE
        binding.contentContainer.visibility = View.GONE
    }

    private fun showContentUi() {
        binding.importContainer.visibility = View.GONE
        binding.contentContainer.visibility = View.VISIBLE
    }

    private fun setImportLoading(loading: Boolean) {
        binding.loadLocalButton.isEnabled = !loading
        binding.loadUrlButton.isEnabled = !loading
        binding.importUrlEditText.isEnabled = !loading
    }

    private fun bindWhoopIconIfAvailable() {
        val launchIntent = resolveWhoopLaunchIntent() ?: return
        val packageName = launchIntent.`package` ?: return
        val icon = packageManager.getApplicationIcon(packageName)
        binding.whoopIconImage.setImageDrawable(icon)
    }

    private fun bindCitysportsIconIfAvailable() {
        val launchIntent = resolveCitysportsLaunchIntent() ?: return
        val packageName = launchIntent.`package` ?: return
        val icon = packageManager.getApplicationIcon(packageName)
        binding.citysportsIconImage.setImageDrawable(icon)
    }

    private fun showLoadNewWaveOptions() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.load_new_wave_dialog_title)
            .setItems(
                arrayOf(
                    getString(R.string.load_new_wave_local_option),
                    getString(R.string.load_new_wave_url_option)
                )
            ) { _, which ->
                when (which) {
                    0 -> loadNewWaveFromLocalJson()
                    1 -> showLoadNewWaveFromUrlDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadNewWaveFromLocalJson() {
        localJsonPicker.launch(arrayOf("application/json"))
    }

    private fun loadNewWaveFromPickedFile(uri: Uri) {
        importProgram(getString(R.string.import_status_loading_local)) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException(getString(R.string.local_json_open_failed))
        }
    }

    private fun showLoadNewWaveFromUrlDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.load_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.load_new_wave_url_option)
            .setView(input)
            .setPositiveButton(R.string.load_url_button) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (url.isBlank()) {
                    Toast.makeText(this, getString(R.string.import_url_required), Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }
                if (!isJsonUrl(url)) {
                    Toast.makeText(this, getString(R.string.import_only_json_error), Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                importProgram(getString(R.string.import_status_loading_url)) {
                    URL(url).openConnection().run {
                        connectTimeout = REQUEST_TIMEOUT_MS
                        readTimeout = REQUEST_TIMEOUT_MS
                        getInputStream().bufferedReader().use { it.readText() }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isJsonUrl(url: String): Boolean {
        return try {
            URL(url).path.lowercase().endsWith(".json")
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun bindHiitCard(programState: ProgramTrackerState) {
        val hiitProgress = withContext(Dispatchers.IO) {
            trackerRepository.getHiitProgress(
                programId = programState.program.program.programId,
                weekNumber = programState.programProgress.currentWeek
            )
        }
        val completedCycles = hiitProgress.completedCycles
        val isDone = completedCycles > 0

        binding.hiitProgressText.text = getString(
            R.string.hiit_progress_template,
            completedCycles
        )
        binding.hiitStatusBadge.text = getString(
            if (isDone) R.string.hiit_done_badge else R.string.hiit_ready_badge
        )
        binding.hiitStatusBadge.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (isDone) R.color.status_done else R.color.hiit_run_bg
        )
        binding.hiitCard.strokeColor = ContextCompat.getColor(
            this,
            if (isDone) R.color.status_done else R.color.hiit_card_stroke
        )
    }

    private fun openWhoopApp() {
        val launchIntent = resolveWhoopLaunchIntent()
        if (launchIntent == null) {
            Toast.makeText(this, getString(R.string.whoop_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(launchIntent)
        binding.mainDrawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun openCitysportsApp() {
        val launchIntent = resolveCitysportsLaunchIntent()
        if (launchIntent == null) {
            Toast.makeText(this, getString(R.string.citysports_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(launchIntent)
        binding.mainDrawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun resolveWhoopLaunchIntent(): Intent? {
        return packageManager.getLaunchIntentForPackage(WHOOP_PACKAGE_NAME)
    }

    private fun resolveCitysportsLaunchIntent(): Intent? {
        return packageManager.getLaunchIntentForPackage(CITYSPORTS_PACKAGE_NAME)
    }

    companion object {
        private const val LOCAL_PROGRAM_ASSET = "program_emanuel_mazzilli_9.json"
        private const val REQUEST_TIMEOUT_MS = 15000
        private const val WHOOP_PACKAGE_NAME = "com.whoop.android"
        private const val CITYSPORTS_PACKAGE_NAME = "com.citysportsfitness.android"
    }
}
