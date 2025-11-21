package com.example.chesslab

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val themePreference = findPreference<ListPreference>("theme")
        themePreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            App.ThemeManager.applyTheme(newValue as String)
            true
        }
    }
}
