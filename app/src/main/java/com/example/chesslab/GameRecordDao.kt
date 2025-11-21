package com.example.chesslab

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GameRecordDao {
    @Insert
    suspend fun insert(gameRecord: GameRecord)

    @Query("SELECT * FROM game_records WHERE playerName = :playerName ORDER BY date DESC")
    suspend fun getGamesForPlayer(playerName: String): List<GameRecord>
}
