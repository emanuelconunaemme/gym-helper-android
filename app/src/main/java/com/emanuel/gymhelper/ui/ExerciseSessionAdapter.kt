package com.emanuel.gymhelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.emanuel.gymhelper.R
import com.emanuel.gymhelper.data.local.room.model.IntensityType
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class ExerciseSessionAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<ExerciseSessionAdapter.ViewHolder>() {

    interface Listener {
        fun onExerciseCardTapped(exerciseProgressId: Long)
        fun onExerciseCardLongPressed(exerciseProgressId: Long)
        fun onEditWeight(exerciseProgressId: Long)
        fun onFinishSet(exerciseProgressId: Long)
    }

    private var items: List<ExerciseSessionItem> = emptyList()
    private var lastAnimatedPosition = -1

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
        if (position > lastAnimatedPosition) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 18f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start()
            lastAnimatedPosition = position
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val listener: Listener
    ) : RecyclerView.ViewHolder(itemView) {

        private val card = itemView.findViewById<MaterialCardView>(R.id.exerciseCard)
        private val stateStripe = itemView.findViewById<View>(R.id.exerciseStateStripe)
        private val statusBadge = itemView.findViewById<TextView>(R.id.exerciseStatusBadge)
        private val nameText = itemView.findViewById<TextView>(R.id.exerciseNameText)
        private val setsRepsText = itemView.findViewById<TextView>(R.id.exerciseSetsRepsText)
        private val setProgressText = itemView.findViewById<TextView>(R.id.setProgressText)
        private val timerCompactText = itemView.findViewById<TextView>(R.id.timerCompactText)
        private val intensityTag = itemView.findViewById<TextView>(R.id.intensityTagText)
        private val expandedContent = itemView.findViewById<View>(R.id.expandedContent)
        private val weightValueText = itemView.findViewById<TextView>(R.id.weightValueText)
        private val finishButton = itemView.findViewById<MaterialButton>(R.id.finishButton)
        private val timerContainer = itemView.findViewById<MaterialCardView>(R.id.timerContainer)
        private val timerText = itemView.findViewById<TextView>(R.id.timerText)

        fun bind(item: ExerciseSessionItem) {
            val context = itemView.context
            val layoutParams = card.layoutParams as MarginLayoutParams
            layoutParams.topMargin = if (item.showCompletedDivider) {
                (84 * context.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            card.layoutParams = layoutParams

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
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_done)
                    )
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
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_skipped)
                    )
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_not_started)
                    )
                }
                item.isOngoing -> {
                    statusBadge.visibility = View.INVISIBLE
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_ongoing)
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_ongoing)
                    )
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_in_progress)
                    )
                }
                else -> {
                    statusBadge.visibility = View.INVISIBLE
                    card.strokeColor = ContextCompat.getColor(context, R.color.card_stroke_default)
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_stroke_default)
                    )
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_not_started)
                    )
                }
            }

            val intensityType = IntensityType.fromDbValue(item.intensityType)
            if (intensityType.shouldShowIntensityBadge()) {
                intensityTag.visibility = View.VISIBLE
                intensityTag.text = context.getString(intensityType.badgeTextRes())
                intensityTag.backgroundTintList =
                    ContextCompat.getColorStateList(context, intensityType.accentColorRes())
            } else {
                intensityTag.visibility = View.GONE
            }

            expandedContent.visibility = if (item.expanded) View.VISIBLE else View.GONE
            weightValueText.text = item.currentWeight?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.weight_value_template, it)
            } ?: context.getString(R.string.weight_empty)

            if (item.timerRemainingSeconds != null) {
                val warning = item.timerRemainingSeconds <= 10
                val timerColor = if (warning) {
                    R.color.intensity_stripping
                } else {
                    R.color.text_primary
                }
                val color = ContextCompat.getColor(context, timerColor)
                timerContainer.isVisible = item.expanded
                timerContainer.setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (warning) R.color.timer_bg_critical else R.color.timer_bg_default
                    )
                )
                if (warning) {
                    timerContainer.startAnimation(
                        android.view.animation.AnimationUtils.loadAnimation(
                            context,
                            R.anim.pulse_soft
                        )
                    )
                } else {
                    timerContainer.clearAnimation()
                }

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
                    timerCompactText.backgroundTintList = ContextCompat.getColorStateList(
                        context,
                        if (warning) R.color.timer_bg_critical else R.color.status_pending
                    )
                    timerCompactText.text = context.getString(
                        R.string.timer_inline_template,
                        item.timerRemainingSeconds.toString()
                    )
                    timerCompactText.setTextColor(color)
                }
            } else {
                timerContainer.clearAnimation()
                timerContainer.isVisible = false
                timerText.visibility = View.GONE
                timerCompactText.visibility = View.GONE
            }

            val actionEnabled = !item.isDone && !item.isSkipped
            finishButton.isEnabled = actionEnabled

            card.setOnClickListener { listener.onExerciseCardTapped(item.exerciseProgressId) }
            card.setOnLongClickListener {
                listener.onExerciseCardLongPressed(item.exerciseProgressId)
                true
            }
            weightValueText.setOnClickListener { listener.onEditWeight(item.exerciseProgressId) }
            finishButton.setOnClickListener { listener.onFinishSet(item.exerciseProgressId) }
        }
    }
}
