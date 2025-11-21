package com.example.chesslab

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class Player(val name: String, val rating: Int, val wins: Int, val losses: Int)

class RatingAdapter(private val players: List<Player>, private val currentUser: String) : RecyclerView.Adapter<RatingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPlayerRating: TextView = view.findViewById(R.id.tvPlayerRating)
        val tvPlayerStats: TextView = view.findViewById(R.id.tvPlayerStats)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val highlightBorder: View = view.findViewById(R.id.highlight_border)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_rating, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        holder.tvPlayerName.text = player.name
        holder.tvPlayerRating.text = player.rating.toString()
        holder.tvPlayerStats.text = "Wins: ${player.wins} / Losses: ${player.losses}"

        // Highlight current user
        if (player.name == currentUser) {
            holder.card.setCardBackgroundColor(Color.parseColor("#334455"))
            holder.highlightBorder.visibility = View.VISIBLE
        } else {
            holder.card.setCardBackgroundColor(Color.parseColor("#223344"))
            holder.highlightBorder.visibility = View.GONE
        }

        // You can set different avatars based on player info later
        // holder.ivAvatar.setImageResource(R.drawable.some_avatar)
    }

    override fun getItemCount() = players.size
}
