package com.dodopayments.checkout

import android.content.Context
import android.content.SharedPreferences

/** [KeyValueStore] backed by app-private SharedPreferences. */
internal class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_NAME = "com.dodopayments.checkout"
    }
}
