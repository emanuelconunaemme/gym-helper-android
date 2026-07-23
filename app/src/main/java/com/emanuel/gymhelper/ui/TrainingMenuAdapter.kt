package com.emanuel.gymhelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.emanuel.gymhelper.R
import com.emanuel.gymhelper.data.local.room.progress.ProgressStatus
import com.google.android.material.card.MaterialCardView

class TrainingMenuAdapter(
    private val onTrainingClicked: (Long) -> Unit,
    private val onTrainingLongPressed: (Long) -> Unit
) : RecyclerView.Adapter<TrainingMenuAdapter.ViewHolder>() {

    private var items: List<TrainingMenuItem> = emptyList()
    private var lastAnimatedPosition = -1

    fun submitItems(newItems: List<TrainingMenuItem>) {
        items = newItems
        lastAnimatedPosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_training_menu, parent, false)
        return ViewHolder(view, onTrainingClicked, onTrainingLongPressed)
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
        private val onTrainingClicked: (Long) -> Unit,
        private val onTrainingLongPressed: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val card = itemView.findViewById<MaterialCardView>(R.id.trainingCard)
        private val stateStripe = itemView.findViewById<View>(R.id.trainingStateStripe)
        private val titleText = itemView.findViewById<TextView>(R.id.trainingTitleText)
        private val summaryText = itemView.findViewById<TextView>(R.id.trainingSummaryText)
        private val statusBadge = itemView.findViewById<TextView>(R.id.trainingStatusBadge)

        fun bind(item: TrainingMenuItem) {
            val context = itemView.context

            titleText.text = item.title
            if (item.skippedExercises > 0) {
                summaryText.visibility = View.VISIBLE
                summaryText.text = context.getString(
                    R.string.training_summary_skipped_template,
                    item.skippedExercises.toString()
                )
            } else {
                summaryText.visibility = View.GONE
            }

            when {
                item.status == ProgressStatus.DONE -> {
                    statusBadge.visibility = View.VISIBLE
                    statusBadge.text = context.getString(R.string.status_done_symbol)
                    statusBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
                    statusBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.status_done)
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_done)
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_done)
                    )
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_done)
                    )
                }
                item.status == ProgressStatus.SKIPPED -> {
                    statusBadge.visibility = View.VISIBLE
                    statusBadge.text = context.getString(R.string.status_skipped_symbol)
                    statusBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
                    statusBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.status_skipped)
                    card.strokeColor = ContextCompat.getColor(context, R.color.status_skipped)
                    stateStripe.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.status_skipped)
                    )
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.card_bg_not_started)
                    )
                }
                item.doneExercises > 0 || item.skippedExercises > 0 -> {
                    statusBadge.visibility = View.VISIBLE
                    statusBadge.text = ""
                    statusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    statusBadge.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.status_ongoing)
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

            card.setOnClickListener { onTrainingClicked(item.trainingId) }
            card.setOnLongClickListener {
                onTrainingLongPressed(item.trainingProgressId)
                true
            }
        }
    }
}
