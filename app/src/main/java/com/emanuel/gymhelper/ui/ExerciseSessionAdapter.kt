package com.emanuel.gymhelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.emanuel.gymhelper.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class ExerciseSessionAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<ExerciseSessionAdapter.ViewHolder>() {

    interface Listener {
        fun onExerciseCardTapped(exerciseProgressId: Long)
        fun onEditWeight(exerciseProgressId: Long)
        fun onFinishSet(exerciseProgressId: Long)
        fun onSkipExercise(exerciseProgressId: Long)
    }

    private var items: List<ExerciseSessionItem> = emptyList()

    fun submitItems(newItems: List<ExerciseSessionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_session, parent, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val listener: Listener
    ) : RecyclerView.ViewHolder(itemView) {

        private val card = itemView.findViewById<MaterialCardView>(R.id.exerciseCard)
        private val statusBadge = itemView.findViewById<TextView>(R.id.exerciseStatusBadge)
        private val nameText = itemView.findViewById<TextView>(R.id.exerciseNameText)
        private val setsRepsText = itemView.findViewById<TextView>(R.id.exerciseSetsRepsText)
        private val setProgressText = itemView.findViewById<TextView>(R.id.setProgressText)
        private val timerCompactText = itemView.findViewById<TextView>(R.id.timerCompactText)
        private val intensityTag = itemView.findViewById<TextView>(R.id.intensityTagText)
        private val expandedContent = itemView.findViewById<View>(R.id.expandedContent)
        private val weightValueText = itemView.findViewById<TextView>(R.id.weightValueText)
        private val finishButton = itemView.findViewById<MaterialButton>(R.id.finishButton)
        private val skipButton = itemView.findViewById<MaterialButton>(R.id.skipButton)
        private val timerText = itemView.findViewById<TextView>(R.id.timerText)

        fun bind(item: ExerciseSessionItem) {
            val context = itemView.context

            nameText.text = item.name
            setsRepsText.text = item.setsReps
            setProgressText.text = item.setProgressText

            when {
                item.isDone -> {
                    statusBadge.visibility = View.VISIBLE
                    statusBadge.text = context.getString(R.string.status_done_symbol)
                    statusBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.status_done)
                    statusBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_done)
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_done)
                    )
                }
                item.isSkipped -> {
                    statusBadge.visibility = View.VISIBLE
                    statusBadge.text = context.getString(R.string.status_skipped_symbol)
                    statusBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.status_skipped)
                    statusBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_skipped)
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_not_started)
                    )
                }
                item.isOngoing -> {
                    statusBadge.visibility = View.INVISIBLE
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_ongoing)
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_in_progress)
                    )
                }
                else -> {
                    statusBadge.visibility = View.INVISIBLE
                    card.strokeColor = ContextCompat.getColor(context, R.color.card_stroke_default)
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_not_started)
                    )
                }
            }

            when (item.intensityType) {
                "rest_pause_2x" -> {
                    intensityTag.visibility = View.VISIBLE
                    intensityTag.text = context.getString(R.string.intensity_rest_pause_2x_short)
                    intensityTag.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.intensity_rest_pause)
                }
                "stripping_2x" -> {
                    intensityTag.visibility = View.VISIBLE
                    intensityTag.text = context.getString(R.string.intensity_stripping_2x_short)
                    intensityTag.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.intensity_stripping)
                }
                else -> intensityTag.visibility = View.GONE
            }

            expandedContent.visibility = if (item.expanded) View.VISIBLE else View.GONE
            weightValueText.text = item.currentWeight?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.weight_value_template, it)
            } ?: context.getString(R.string.weight_empty)

            if (item.timerRemainingSeconds != null) {
                val timerColor = if (item.timerRemainingSeconds <= 10) {
                    R.color.intensity_stripping
                } else {
                    R.color.text_primary
                }
                val color = ContextCompat.getColor(context, timerColor)

                if (item.expanded) {
                    timerCompactText.visibility = View.GONE
                    timerText.visibility = View.VISIBLE
                    timerText.text = context.getString(
                        R.string.timer_large_template,
                        item.timerRemainingSeconds.toString()
                    )
                    timerText.setTextColor(color)
                } else {
                    timerText.visibility = View.GONE
                    timerCompactText.visibility = View.VISIBLE
                    timerCompactText.text = context.getString(
                        R.string.timer_inline_template,
                        item.timerRemainingSeconds.toString()
                    )
                    timerCompactText.setTextColor(color)
                }
            } else {
                timerText.visibility = View.GONE
                timerCompactText.visibility = View.GONE
            }

            val actionEnabled = !item.isDone && !item.isSkipped
            finishButton.isEnabled = actionEnabled
            skipButton.isEnabled = actionEnabled

            card.setOnClickListener { listener.onExerciseCardTapped(item.exerciseProgressId) }
            weightValueText.setOnClickListener { listener.onEditWeight(item.exerciseProgressId) }
            finishButton.setOnClickListener { listener.onFinishSet(item.exerciseProgressId) }
            skipButton.setOnClickListener { listener.onSkipExercise(item.exerciseProgressId) }
        }
    }
}
