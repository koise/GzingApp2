package com.example.gzingapp.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationStatus

class NavigationHistoryAdapter(
    private var historyList: MutableList<NavigationHistory>,
    private val onHistoryClick: (NavigationHistory) -> Unit,
    private val onDeleteClick: (NavigationHistory) -> Unit
) : RecyclerView.Adapter<NavigationHistoryAdapter.HistoryViewHolder>() {
    
    /**
     * Updates the adapter with a new list of history items
     */
    fun updateHistoryList(newList: List<NavigationHistory>) {
        historyList = newList.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_navigation_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRouteDescription: TextView = itemView.findViewById(R.id.tvRouteDescription)
        private val tvStartTime: TextView = itemView.findViewById(R.id.tvStartTime)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvStopsCompleted: TextView = itemView.findViewById(R.id.tvStopsCompleted)
        private val progressBarCompletion: ProgressBar = itemView.findViewById(R.id.progressBarCompletion)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(history: NavigationHistory) {
            try {
                // Set basic information with null safety
                tvRouteDescription.text = history.routeDescription ?: "Unknown Route"
                tvStartTime.text = try { history.getFormattedStartTime() } catch (e: Exception) { "Unknown Time" }
                tvDuration.text = try { history.getFormattedDuration() } catch (e: Exception) { "Unknown Duration" }
                
                // Format distance with null safety
                tvDistance.text = try {
                    "${"%.1f".format(history.totalDistance)} km"
                } catch (e: Exception) {
                    "Unknown Distance"
                }
                
                // Format stops with null safety
                tvStopsCompleted.text = try {
                    "${history.completedStops ?: 0}/${history.totalStops ?: 0} stops"
                } catch (e: Exception) {
                    "0/0 stops"
                }
                
                // Set status with appropriate styling and null safety
                try {
                    tvStatus.text = history.getStatusDisplayText()
                    when (history.status) {
                        NavigationStatus.COMPLETED -> {
                            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.navigation_active))
                            tvStatus.setBackgroundResource(R.drawable.status_background)
                            setCompletionProgress(100, R.color.navigation_active)
                            itemView.alpha = 1.0f
                        }
                        NavigationStatus.CANCELLED -> {
                            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.navigation_warning))
                            tvStatus.setBackgroundResource(R.drawable.status_background)
                            setCompletionProgress(history.getCompletionPercentage(), R.color.navigation_warning)
                            itemView.alpha = 0.7f
                        }
                        NavigationStatus.FAILED -> {
                            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.navigation_error))
                            tvStatus.setBackgroundResource(R.drawable.status_background)
                            setCompletionProgress(history.getCompletionPercentage(), R.color.navigation_error)
                            itemView.alpha = 0.7f
                        }
                        NavigationStatus.IN_PROGRESS -> {
                            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_brown))
                            tvStatus.setBackgroundResource(R.drawable.status_background)
                            setCompletionProgress(history.getCompletionPercentage(), R.color.primary_brown)
                            itemView.alpha = 1.0f
                        }
                        else -> {
                            // Default styling for unknown status
                            tvStatus.setTextColor(Color.GRAY)
                            tvStatus.setBackgroundResource(R.drawable.status_background)
                            setCompletionProgress(0, android.R.color.darker_gray)
                            itemView.alpha = 0.5f
                        }
                    }
                } catch (e: Exception) {
                    // Default status if there's an error
                    tvStatus.text = "Unknown"
                    tvStatus.setTextColor(Color.GRAY)
                    tvStatus.setBackgroundResource(R.drawable.status_background)
                    setCompletionProgress(0, android.R.color.darker_gray)
                    itemView.alpha = 0.5f
                }
                
                // Set click listeners
                itemView.setOnClickListener {
                    try {
                        onHistoryClick(history)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                btnDelete.setOnClickListener {
                    try {
                        onDeleteClick(history)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        private fun setCompletionProgress(progress: Int, colorResId: Int) {
            progressBarCompletion.progress = progress
            progressBarCompletion.progressDrawable.setColorFilter(
                ContextCompat.getColor(itemView.context, colorResId),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }
}