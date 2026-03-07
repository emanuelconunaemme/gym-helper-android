package com.emanuel.gymhelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.emanuel.gymhelper.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class ExerciseAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    constructor(onExerciseClicked: (Long) -> Unit) : this(
        object : Listener {
            override fun onExerciseClicked(exerciseProgressId: Long) {
                onExerciseClicked(exerciseProgressId)
            }

            override fun onSaveWeight(exerciseProgressId: Long, weightText: String) = Unit
            override fun onFinishSetWithTimer(exerciseProgressId: Long, weightText: String) = Unit
            override fun onMarkSetDone(exerciseProgressId: Long, weightText: String) = Unit
            override fun onSkipSet(exerciseProgressId: Long, weightText: String) = Unit
            override fun onMarkExerciseDone(exerciseProgressId: Long, weightText: String) = Unit
            override fun onSkipExercise(exerciseProgressId: Long, weightText: String) = Unit
        }
    )

    private var items: List<ExerciseCardItem> = emptyList()

    fun submitItems(newItems: List<ExerciseCardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_card, parent, false)
        return ExerciseViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    interface Listener {
        fun onExerciseClicked(exerciseProgressId: Long)
        fun onSaveWeight(exerciseProgressId: Long, weightText: String)
        fun onFinishSetWithTimer(exerciseProgressId: Long, weightText: String)
        fun onMarkSetDone(exerciseProgressId: Long, weightText: String)
        fun onSkipSet(exerciseProgressId: Long, weightText: String)
        fun onMarkExerciseDone(exerciseProgressId: Long, weightText: String)
        fun onSkipExercise(exerciseProgressId: Long, weightText: String)
    }

    class ExerciseViewHolder(
        itemView: View,
        private val listener: Listener
    ) : RecyclerView.ViewHolder(itemView) {

        private val card = itemView.findViewById<MaterialCardView>(R.id.exerciseCard)
        private val nameText = itemView.findViewById<TextView>(R.id.exerciseNameText)
        private val metaText = itemView.findViewById<TextView>(R.id.exerciseMetaText)
        private val statusText = itemView.findViewById<TextView>(R.id.exerciseStatusText)
        private val setProgressText = itemView.findViewById<TextView>(R.id.setProgressText)
        private val nextSetText = itemView.findViewById<TextView>(R.id.nextSetText)
        private val lastWeightText = itemView.findViewById<TextView>(R.id.lastWeightText)
        private val weightEditText = itemView.findViewById<EditText>(R.id.weightEditText)
        private val saveWeightButton = itemView.findViewById<MaterialButton>(R.id.saveWeightButton)
        private val finishSetButton = itemView.findViewById<MaterialButton>(R.id.finishSetButton)
        private val markSetDoneButton = itemView.findViewById<MaterialButton>(R.id.markSetDoneButton)
        private val skipSetButton = itemView.findViewById<MaterialButton>(R.id.skipSetButton)
        private val markExerciseDoneButton =
            itemView.findViewById<MaterialButton>(R.id.markExerciseDoneButton)
        private val skipExerciseButton = itemView.findViewById<MaterialButton>(R.id.skipExerciseButton)
        private val expandHintText = itemView.findViewById<TextView>(R.id.expandHintText)
        private val detailsContainer = itemView.findViewById<View>(R.id.exerciseDetailsContainer)
        private val detailsText = itemView.findViewById<TextView>(R.id.exerciseDetailsText)

        fun bind(item: ExerciseCardItem) {
            nameText.text = item.name
            metaText.text = itemView.context.getString(
                R.string.exercise_meta_template,
                item.restSeconds.toString(),
                item.intensityLabel
            )
            statusText.text = item.statusLabel
            setProgressText.text = itemView.context.getString(
                R.string.set_progress_template,
                item.completedSets.toString(),
                item.skippedSets.toString(),
                item.plannedSets.toString()
            )
            nextSetText.text = item.nextSetNumber?.let {
                itemView.context.getString(R.string.next_set_template, it.toString())
            } ?: itemView.context.getString(R.string.exercise_no_pending_sets)
            lastWeightText.text = item.lastSessionWeightText?.let {
                itemView.context.getString(R.string.last_weight_template, it)
            } ?: itemView.context.getString(R.string.last_weight_empty)
            val currentWeight = item.currentWeightText ?: ""
            if (weightEditText.text?.toString() != currentWeight) {
                weightEditText.setText(currentWeight)
            }

            applyCardStyle(item)
            detailsText.text = item.detailsText
            detailsContainer.visibility = if (item.expanded) View.VISIBLE else View.GONE
            expandHintText.text = if (item.expanded) {
                itemView.context.getString(R.string.exercise_collapse_hint)
            } else {
                itemView.context.getString(R.string.exercise_expand_hint)
            }

            val hasPendingSets = item.nextSetNumber != null
            finishSetButton.isEnabled = hasPendingSets
            markSetDoneButton.isEnabled = hasPendingSets
            skipSetButton.isEnabled = hasPendingSets
            markExerciseDoneButton.isEnabled = !item.isCompleted
            skipExerciseButton.isEnabled = !item.isSkipped

            card.setOnClickListener { listener.onExerciseClicked(item.exerciseProgressId) }
            saveWeightButton.setOnClickListener {
                listener.onSaveWeight(item.exerciseProgressId, weightEditText.text?.toString().orEmpty())
            }
            finishSetButton.setOnClickListener {
                listener.onFinishSetWithTimer(
                    item.exerciseProgressId,
                    weightEditText.text?.toString().orEmpty()
                )
            }
            markSetDoneButton.setOnClickListener {
                listener.onMarkSetDone(item.exerciseProgressId, weightEditText.text?.toString().orEmpty())
            }
            skipSetButton.setOnClickListener {
                listener.onSkipSet(item.exerciseProgressId, weightEditText.text?.toString().orEmpty())
            }
            markExerciseDoneButton.setOnClickListener {
                listener.onMarkExerciseDone(
                    item.exerciseProgressId,
                    weightEditText.text?.toString().orEmpty()
                )
            }
            skipExerciseButton.setOnClickListener {
                listener.onSkipExercise(item.exerciseProgressId, weightEditText.text?.toString().orEmpty())
            }
        }

        private fun applyCardStyle(item: ExerciseCardItem) {
            val context = itemView.context
            val strokeColor = when {
                item.isCompleted -> R.color.status_done
                item.isSkipped -> R.color.status_skipped
                item.intensityTypeValue == "stripping_2x" -> R.color.intensity_stripping
                item.intensityTypeValue == "rest_pause_2x" -> R.color.intensity_rest_pause
                else -> R.color.card_stroke_default
            }
            card.strokeColor = ContextCompat.getColor(context, strokeColor)
            card.strokeWidth = if (strokeColor == R.color.card_stroke_default) 2 else 4
        }
    }
}
