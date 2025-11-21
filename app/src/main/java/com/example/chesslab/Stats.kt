package com.example.chesslab

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object Stats {
    private const val PREFS = "stats_prefs"
    private const val KEY_DATE = "games_date"
    private const val KEY_COUNT = "games_count"

    private fun todayStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun incrementGamesToday(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayStr()
        val last = p.getString(KEY_DATE, null)
        var count = p.getInt(KEY_COUNT, 0)
        if (last != today) count = 0
        p.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, count + 1).apply()
    }

    fun getGamesToday(ctx: Context): Int {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayStr()
        val last = p.getString(KEY_DATE, null)
        return if (last == today) p.getInt(KEY_COUNT, 0) else 0
    }
}
