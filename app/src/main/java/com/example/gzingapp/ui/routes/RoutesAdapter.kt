package com.example.gzingapp.ui.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R

class RoutesAdapter(
    private val routes: List<RouteItem>,
    private val onRouteClick: (RouteItem) -> Unit
) : RecyclerView.Adapter<RoutesAdapter.RouteViewHolder>() {

    class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvRouteTitle)
        val description: TextView = itemView.findViewById(R.id.tvRouteDescription)
        val duration: TextView = itemView.findViewById(R.id.tvRouteDuration)
        val distance: TextView = itemView.findViewById(R.id.tvRouteDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        holder.title.text = route.title
        // Use the new A->B->C->D route description
        holder.description.text = route.detailedPointsDescription
        holder.duration.text = route.duration
        holder.distance.text = route.distance

        // Add visual indicator for active routes
        if (route.isActive) {
            holder.title.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.title.setTextColor(holder.itemView.context.getColor(android.R.color.black))
        }

        holder.itemView.setOnClickListener {
            onRouteClick(route)
        }
    }

    override fun getItemCount(): Int = routes.size
}