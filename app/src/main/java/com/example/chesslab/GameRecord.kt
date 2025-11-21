package com.example.chesslab

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val opponentName: String,
    val result: String, // e.g., "Win", "Loss", "Draw"
    val date: Date
)
