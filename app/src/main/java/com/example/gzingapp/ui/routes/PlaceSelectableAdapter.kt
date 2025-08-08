package com.example.gzingapp.ui.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.ui.places.PlaceItem
import com.google.android.material.button.MaterialButton

class PlaceSelectableAdapter(
    private var places: List<PlaceItem> = emptyList(),
    private val onAddToRoute: (PlaceItem) -> Unit = {}
) : RecyclerView.Adapter<PlaceSelectableAdapter.PlaceSelectableViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceSelectableViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_selectable, parent, false)
        return PlaceSelectableViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceSelectableViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    fun updatePlaces(newPlaces: List<PlaceItem>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    inner class PlaceSelectableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPlaceImage: ImageView = itemView.findViewById(R.id.ivPlaceImage)
        private val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvPlaceCategory: TextView = itemView.findViewById(R.id.tvPlaceCategory)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val ivRating: ImageView = itemView.findViewById(R.id.ivRating)
        private val btnAddToRoute: MaterialButton = itemView.findViewById(R.id.btnAddToRoute)

        fun bind(place: PlaceItem) {
            tvPlaceName.text = place.name
            tvPlaceCategory.text = place.category
            
            // Format distance
            val distanceText = if (place.distance != null && place.distance > 0) {
                "%.1f km away".format(place.distance)
            } else {
                "Location unknown"
            }
            tvDistance.text = distanceText
            
            // Set rating
            place.rating?.let { rating ->
                tvRating.text = "%.1f".format(rating)
                ivRating.visibility = View.VISIBLE
                tvRating.visibility = View.VISIBLE
            } ?: run {
                ivRating.visibility = View.GONE
                tvRating.visibility = View.GONE
            }
            
            // Load image (placeholder for now)
            ivPlaceImage.setImageResource(R.drawable.ic_places)
            
            // Set add button click
            btnAddToRoute.setOnClickListener {
                onAddToRoute(place)
            }
        }
    }
}