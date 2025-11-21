package com.example.chesslab

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        val userPrefs = UserPrefs(this)
        val theme = userPrefs.getString("theme", "system")
        ThemeManager.applyTheme(theme ?: "system")
    }

    companion object {
        lateinit var instance: App
            private set
    }

    object ThemeManager {
        fun applyTheme(theme: String) {
            when (theme) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    object LanguageManager {
        fun applyLanguage(language: String) {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(language)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }
}
