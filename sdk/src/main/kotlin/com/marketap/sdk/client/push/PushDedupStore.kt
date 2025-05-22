package com.marketap.sdk.client.push

import android.content.Context
import com.marketap.sdk.client.SharedPreferenceInternalStorage.Companion.MARKETAP_SDK_STORAGE

internal object PushDedupStore {
    private const val KEY_SET = "marketap_seen_ids"

    fun isDuplicate(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(MARKETAP_SDK_STORAGE, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SET, mutableSetOf()) ?: mutableSetOf()

        return if (seen.contains(id)) {
            true
        } else {
            prefs.edit().putStringSet(KEY_SET, seen + id).apply()
            false
        }
    }
}