package com.example.gzingapp.ui.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.RoutePoint

class RoutePointAdapter(
    private val onRemovePoint: (RoutePoint) -> Unit,
    private val onAlarmToggle: (RoutePoint, Boolean) -> Unit
) : RecyclerView.Adapter<RoutePointAdapter.RoutePointViewHolder>() {

    private var routePoints: List<RoutePoint> = emptyList()

    fun updateRoutePoints(points: List<RoutePoint>) {
        routePoints = points
        notifyDataSetChanged()
    }

    fun addRoutePoint(point: RoutePoint) {
        val newList = routePoints.toMutableList()
        newList.add(point)
        routePoints = newList
        notifyItemInserted(routePoints.size - 1)
    }

    fun removeRoutePoint(point: RoutePoint) {
        val position = routePoints.indexOfFirst { it.id == point.id }
        if (position != -1) {
            val newList = routePoints.toMutableList()
            newList.removeAt(position)
            routePoints = newList
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutePointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_point, parent, false)
        return RoutePointViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoutePointViewHolder, position: Int) {
        val routePoint = routePoints[position]
        holder.bind(routePoint)
    }

    override fun getItemCount(): Int = routePoints.size

    inner class RoutePointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPointNumber: TextView = itemView.findViewById(R.id.tvPointNumber)
        private val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvPlaceAddress: TextView = itemView.findViewById(R.id.tvPlaceAddress)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvEstimatedTime: TextView = itemView.findViewById(R.id.tvEstimatedTime)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)
        private val switchAlarm: androidx.appcompat.widget.SwitchCompat = itemView.findViewById(R.id.switchAlarm)

        fun bind(routePoint: RoutePoint) {
            tvPointNumber.text = routePoint.pointLabel
            tvPlaceName.text = routePoint.place.name
            tvPlaceAddress.text = routePoint.place.address ?: "No address"
            
            // Set distance if available
            if (routePoint.distanceFromPrevious > 0) {
                tvDistance.text = "${routePoint.distanceFromPrevious} km"
                tvDistance.visibility = View.VISIBLE
            } else {
                tvDistance.visibility = View.GONE
            }
            
            // Set estimated time if available
            if (routePoint.estimatedTimeFromPrevious > 0) {
                tvEstimatedTime.text = "~${routePoint.estimatedTimeFromPrevious} min"
                tvEstimatedTime.visibility = View.VISIBLE
            } else {
                tvEstimatedTime.visibility = View.GONE
            }
            
            // Set alarm switch state
            switchAlarm.isChecked = routePoint.alarmEnabled
            
            // Set click listeners
            btnRemove.setOnClickListener {
                onRemovePoint(routePoint)
            }
            
            switchAlarm.setOnCheckedChangeListener { _, isChecked ->
                onAlarmToggle(routePoint, isChecked)
            }
        }
    }
}