package com.example.chesslab

import android.content.Context

class UserPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private fun kUsers() = "users"
    private fun kName(email: String) = "user:$email:name"
    private fun kPass(email: String) = "user:$email:password"
    private fun kCountry(email: String) = "user:$email:country"

    private fun kSetting(key: String) = "setting:$key"

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
    }
    private fun kSessionType() = "session_type"
    private fun kSessionEmail() = "session_email"

    fun getTheme(): String = getString(KEY_THEME, "dark") ?: "dark"
    fun setTheme(theme: String) = setString(KEY_THEME, theme)

    fun getLanguage(): String = getString(KEY_LANGUAGE, "ru") ?: "ru"
    fun setLanguage(language: String) = setString(KEY_LANGUAGE, language)



    fun register(name: String, email: String, password: String): Boolean {
        val users = HashSet(sp.getStringSet(kUsers(), emptySet()) ?: emptySet())
        if (users.contains(email)) return false
        users.add(email)
        sp.edit()
            .putStringSet(kUsers(), users)
            .putString(kName(email), name)
            .putString(kPass(email), password)
            .apply()
        loginAs(email)
        return true
    }

    fun authenticate(email: String, password: String): Boolean {
        val saved = sp.getString(kPass(email), null) ?: return false
        val authenticated = saved == password
        if (authenticated) {
            loginAs(email)
        }
        return authenticated
    }

    fun getName(email: String): String? = sp.getString(kName(email), null)
    fun setName(email: String, name: String) {
        sp.edit().putString(kName(email), name).apply()
    }

    fun getCountry(email: String): String? = sp.getString(kCountry(email), null)

    fun setCountry(email: String, country: String) {
        sp.edit().putString(kCountry(email), country).apply()
    }

    fun setBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(kSetting(key), value).apply()
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return sp.getBoolean(kSetting(key), defValue)
    }

    fun setString(key: String, value: String) {
        sp.edit().putString(kSetting(key), value).apply()
    }

    fun getString(key: String, defValue: String?): String? {
        return sp.getString(kSetting(key), defValue)
    }

    fun exists(email: String): Boolean = sp.getString(kPass(email), null) != null
    fun allUsers(): Set<String> = sp.getStringSet(kUsers(), emptySet()) ?: emptySet()

    fun loginAs(email: String) {
        sp.edit()
            .putString(kSessionType(), "user")
            .putString(kSessionEmail(), email)
            .apply()
    }

    fun loginAsGuest() {
        sp.edit()
            .putString(kSessionType(), "guest")
            .remove(kSessionEmail())
            .apply()
    }

    fun logout() {
        sp.edit()
            .remove(kSessionType())
            .remove(kSessionEmail())
            .apply()
    }

    fun isLoggedIn(): Boolean = sp.getString(kSessionType(), null) == "user"
    fun isGuest(): Boolean = sp.getString(kSessionType(), null) == "guest"
    fun sessionType(): String = sp.getString(kSessionType(), "none")!!

    fun currentUserEmail(): String? =
        if (isLoggedIn()) sp.getString(kSessionEmail(), null) else null

    fun currentDisplayName(): String =
        currentUserEmail()?.let { getName(it) } ?: "Гость"
}
