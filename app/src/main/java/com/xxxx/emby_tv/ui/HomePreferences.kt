package com.xxxx.emby_tv.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Per-install Android TV homepage settings. SharedPreferences survive upgrades.
 */
class HomePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jemby_home_v1", Context.MODE_PRIVATE)

    var hideWatched by mutableStateOf(prefs.getBoolean("hideWatched", true))
        private set
    var showContinue by mutableStateOf(prefs.getBoolean("showContinue", true))
        private set
    var showFavorites by mutableStateOf(prefs.getBoolean("showFavorites", true))
        private set
    var showLibraries by mutableStateOf(prefs.getBoolean("showLibraries", true))
        private set
    var posterSize by mutableStateOf(prefs.getString("posterSize", "medium") ?: "medium")
        private set

    fun setHideWatched(value: Boolean) { hideWatched = value; prefs.edit().putBoolean("hideWatched", value).apply() }
    fun setShowContinue(value: Boolean) { showContinue = value; prefs.edit().putBoolean("showContinue", value).apply() }
    fun setShowFavorites(value: Boolean) { showFavorites = value; prefs.edit().putBoolean("showFavorites", value).apply() }
    fun setShowLibraries(value: Boolean) { showLibraries = value; prefs.edit().putBoolean("showLibraries", value).apply() }
    fun setPosterSize(value: String) {
        if (value !in listOf("small", "medium", "large")) return
        posterSize = value
        prefs.edit().putString("posterSize", value).apply()
    }
}
