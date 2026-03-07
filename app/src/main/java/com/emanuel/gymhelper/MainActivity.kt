package com.emanuel.gymhelper

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: GymHelperDatabase
    private lateinit var importer: ProgramJsonImporter
    private lateinit var trackerRepository: WorkoutTrackerRepository

    private val trainingAdapter = TrainingMenuAdapter { trainingId ->
        openTrainingDetail(trainingId)
    }

    private var currentProgramState: ProgramTrackerState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDependencies.database(this)
        importer = AppDependencies.importer(this)
        trackerRepository = AppDependencies.trackerRepository(this)

        setupUi()
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
        binding.importStatusText.text = loadingMessage

        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { loadJson() }
                withContext(Dispatchers.IO) { importer.import(json) }
                binding.importStatusText.text = getString(R.string.import_status_success)
                loadProgramAndRender()
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                binding.importStatusText.text = getString(R.string.import_status_error, message)
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

        val menuItems = withContext(Dispatchers.IO) {
            val programId = programState.program.program.programId
            val weekNumber = programState.programProgress.currentWeek

            programState.program.trainings.map { training ->
                val trainingState = trackerRepository.loadTrainingState(
                    programId = programId,
                    training = training,
                    weekNumber = weekNumber
                )
                val weekExercises = trainingState.exerciseStates.filter { it.weekPlan != null }
                val doneExercises = weekExercises.count { it.progress.status == ProgressStatus.DONE }
                val skippedExercises = weekExercises.count { it.progress.status == ProgressStatus.SKIPPED }

                TrainingMenuItem(
                    trainingId = training.training.trainingId,
                    title = training.training.name,
                    doneExercises = doneExercises,
                    skippedExercises = skippedExercises,
                    totalExercises = weekExercises.size,
                    status = trainingState.trainingProgress.status
                )
            }
        }

        trainingAdapter.submitItems(menuItems)
    }

    private fun bindProgramHeader(programState: ProgramTrackerState) {
        binding.programNameText.text = programState.program.program.name
        binding.currentWeekText.text = getString(
            R.string.week_label_with_total_template,
            programState.programProgress.currentWeek.toString(),
            programState.program.program.numberOfWeeks.toString()
        )
        binding.prevWeekButton.isEnabled = programState.programProgress.currentWeek > 1
        binding.nextWeekButton.isEnabled =
            programState.programProgress.currentWeek < programState.program.program.numberOfWeeks
    }

    private fun openTrainingDetail(trainingId: Long) {
        startActivity(
            Intent(this, TrainingDetailActivity::class.java)
                .putExtra(TrainingDetailActivity.EXTRA_TRAINING_ID, trainingId)
        )
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

    companion object {
        private const val LOCAL_PROGRAM_ASSET = "program_emanuel_mazzilli_5.json"
        private const val REQUEST_TIMEOUT_MS = 15000
    }
}
