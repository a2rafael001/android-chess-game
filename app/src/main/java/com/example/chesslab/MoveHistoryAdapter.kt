package com.example.chesslab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Move(val moveNumber: Int, val whiteMove: String, val blackMove: String?)

class MoveHistoryAdapter(private val moves: List<Move>) : RecyclerView.Adapter<MoveHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMoveNumber: TextView = view.findViewById(R.id.tvMoveNumber)
        val tvWhiteMove: TextView = view.findViewById(R.id.tvWhiteMove)
        val tvBlackMove: TextView = view.findViewById(R.id.tvBlackMove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_move, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val move = moves[position]
        holder.tvMoveNumber.text = "${move.moveNumber}."
        holder.tvWhiteMove.text = move.whiteMove
        holder.tvBlackMove.text = move.blackMove ?: ""
    }

    override fun getItemCount() = moves.size
}
