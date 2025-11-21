package com.example.chesslab

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey val playerName: String,
    val opponentName: String,
    val boardState: String,
    val isWhiteTurn: Boolean,
    val whiteCapturedPieces: String,
    val blackCapturedPieces: String,
    val savedAt: Date
)
