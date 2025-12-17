package com.moez.QKSMS.feature.compose

import android.content.Context

class TokenUploadManager(context: Context, token_key : String) {
    private val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    private val TOKEN_KEY = token_key

    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

    fun clearToken() {
        prefs.edit().remove(TOKEN_KEY).apply()
    }

    fun hasToken(): Boolean {
        return prefs.contains(TOKEN_KEY)
    }
}