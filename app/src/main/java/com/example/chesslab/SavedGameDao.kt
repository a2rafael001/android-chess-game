package com.example.chesslab

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(savedGame: SavedGame)

    @Query("SELECT * FROM saved_games WHERE playerName = :playerName")
    suspend fun getSavedGame(playerName: String): SavedGame?

    @Query("DELETE FROM saved_games WHERE playerName = :playerName")
    suspend fun deleteSavedGame(playerName: String)
}
