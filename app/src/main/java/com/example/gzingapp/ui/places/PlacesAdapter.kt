package com.example.gzingapp.ui.places

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class PlacesAdapter(
    private var places: List<PlaceItem>,
    private val onPlaceClick: (PlaceItem) -> Unit
) : RecyclerView.Adapter<PlacesAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.ivPlaceImage)
        val name: TextView = itemView.findViewById(R.id.tvPlaceName)
        val category: TextView = itemView.findViewById(R.id.tvPlaceCategory)
        val location: TextView = itemView.findViewById(R.id.tvPlaceLocation)
        val rating: TextView = itemView.findViewById(R.id.tvPlaceRating)
        val distance: TextView = itemView.findViewById(R.id.tvPlaceDistance)
        val openStatus: TextView = itemView.findViewById(R.id.tvOpenStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        val context = holder.itemView.context

        // Set place name
        holder.name.text = place.name

        // Set category
        holder.category.text = place.category

        // Set location
        holder.location.text = place.location

        // Set rating if available
        if (place.rating != null) {
            val ratingText = "★ ${String.format("%.1f", place.rating)}"
            holder.rating.text = ratingText
            holder.rating.visibility = View.VISIBLE
        } else {
            holder.rating.visibility = View.GONE
        }

        // Set distance if available
        if (place.distance != null) {
            val distanceText = if (place.distance < 1.0) {
                "${(place.distance * 1000).toInt()} m"
            } else {
                String.format("%.1f km", place.distance)
            }
            holder.distance.text = distanceText
            holder.distance.visibility = View.VISIBLE
        } else {
            holder.distance.visibility = View.GONE
        }

        // Set open status if available
        when (place.isOpen) {
            true -> {
                holder.openStatus.text = "Open"
                holder.openStatus.setTextColor(context.getColor(R.color.success))
                holder.openStatus.setBackgroundResource(R.drawable.status_background)
                holder.openStatus.visibility = View.VISIBLE
            }
            false -> {
                holder.openStatus.text = "Closed"
                holder.openStatus.setTextColor(context.getColor(R.color.error))
                holder.openStatus.setBackgroundResource(R.drawable.status_background)
                holder.openStatus.visibility = View.VISIBLE
            }
            null -> {
                holder.openStatus.visibility = View.GONE
            }
        }

        // Load image with enhanced options
        loadPlaceImage(holder, place)

        // Set click listener
        holder.itemView.setOnClickListener {
            onPlaceClick(place)
        }

        // Add subtle animation
        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
            false
        }
    }

    private fun loadPlaceImage(holder: PlaceViewHolder, place: PlaceItem) {
        val context = holder.itemView.context

        if (!place.photoUrl.isNullOrEmpty()) {
            // Load from URL if available
            Glide.with(context)
                .load(place.photoUrl)
                .apply(RequestOptions()
                    .placeholder(getCategoryIcon(place.category))
                    .error(getCategoryIcon(place.category))
                    .centerCrop()
                    .transform(RoundedCorners(16))
                )
                .into(holder.image)
        } else {
            // Use category-specific icon
            val iconRes = getCategoryIcon(place.category)

            Glide.with(context)
                .load(iconRes)
                .apply(RequestOptions()
                    .centerInside()
                    .transform(RoundedCorners(16))
                )
                .into(holder.image)
        }
    }

    override fun getItemCount(): Int = places.size

    /**
     * Update the places list with diff callback for better performance
     */
    fun updatePlaces(newPlaces: List<PlaceItem>) {
        val diffCallback = PlacesDiffCallback(places, newPlaces)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        places = newPlaces
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Get category-specific icon
     */
    private fun getCategoryIcon(category: String): Int {
        return when (category.lowercase()) {
            "restaurant", "food", "food & dining" -> R.drawable.ic_restaurant
            "shopping mall", "store", "shopping" -> R.drawable.ic_shopping
            "healthcare", "hospital", "pharmacy" -> R.drawable.ic_hospital
            "education", "school", "university" -> R.drawable.ic_school
            "recreation", "park", "tourist attraction" -> R.drawable.ic_park
            "religious site", "church", "place of worship" -> R.drawable.ic_church
            "gas station" -> R.drawable.ic_gas_station
            "financial", "bank", "atm" -> R.drawable.ic_bank
            "accommodation", "lodging", "hotel" -> R.drawable.ic_hotel
            else -> R.drawable.ic_places
        }
    }

    /**
     * Filter places by category
     */
    fun filterByCategory(category: String?) {
        val filteredPlaces = if (category.isNullOrBlank()) {
            places
        } else {
            places.filter { it.category.contains(category, ignoreCase = true) }
        }
        updatePlaces(filteredPlaces)
    }

    /**
     * Sort places by distance
     */
    fun sortByDistance() {
        val sortedPlaces = places.sortedBy { it.distance ?: Double.MAX_VALUE }
        updatePlaces(sortedPlaces)
    }

    /**
     * Sort places by rating
     */
    fun sortByRating() {
        val sortedPlaces = places.sortedByDescending { it.rating ?: 0f }
        updatePlaces(sortedPlaces)
    }

    /**
     * Get place at position
     */
    fun getPlaceAt(position: Int): PlaceItem? {
        return if (position in 0 until places.size) places[position] else null
    }

    /**
     * Clear all places
     */
    fun clearPlaces() {
        updatePlaces(emptyList())
    }

    /**
     * Check if adapter is empty
     */
    fun isEmpty(): Boolean = places.isEmpty()

    /**
     * Get places count
     */
    fun getPlacesCount(): Int = places.size
}

/**
 * DiffCallback for efficient RecyclerView updates
 */
class PlacesDiffCallback(
    private val oldList: List<PlaceItem>,
    private val newList: List<PlaceItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        // Use ID if available, otherwise use name and location
        return if (oldItem.id.isNotEmpty() && newItem.id.isNotEmpty()) {
            oldItem.id == newItem.id
        } else {
            oldItem.name == newItem.name && oldItem.location == newItem.location
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return oldItem.name == newItem.name &&
                oldItem.category == newItem.category &&
                oldItem.location == newItem.location &&
                oldItem.rating == newItem.rating &&
                oldItem.distance == newItem.distance &&
                oldItem.isOpen == newItem.isOpen &&
                oldItem.photoUrl == newItem.photoUrl
    }
}