package com.groot.cleaning.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("gc_prefs", Context.MODE_PRIVATE)

    var simulation: Boolean
        get() = sp.getBoolean("sim", true)
        set(v) { sp.edit().putBoolean("sim", v).apply() }
    var arabic: Boolean
        get() = sp.getBoolean("ar", false)
        set(v) { sp.edit().putBoolean("ar", v).apply() }
    var timeoutSec: Int
        get() = sp.getInt("timeout", 12)
        set(v) { sp.edit().putInt("timeout", v).apply() }
    var retries: Int
        get() = sp.getInt("retries", 3)
        set(v) { sp.edit().putInt("retries", v).apply() }
    var delayMs: Long
        get() = sp.getLong("delay", 800L)
        set(v) { sp.edit().putLong("delay", v).apply() }
    var continueAfterFailure: Boolean
        get() = sp.getBoolean("continue", true)
        set(v) { sp.edit().putBoolean("continue", v).apply() }
    var verification: String
        get() = sp.getString("verif", "STANDARD") ?: "STANDARD"
        set(v) { sp.edit().putString("verif", v).apply() }
    /** Task keys not yet executed (for Resume). One key per line. */
    var pending: String
        get() = sp.getString("pending", "") ?: ""
        set(v) { sp.edit().putString("pending", v).apply() }
    var lastRun: Long
        get() = sp.getLong("lastRun", 0L)
        set(v) { sp.edit().putLong("lastRun", v).apply() }
    var lastResult: String
        get() = sp.getString("lastResult", "-") ?: "-"
        set(v) { sp.edit().putString("lastResult", v).apply() }
}
