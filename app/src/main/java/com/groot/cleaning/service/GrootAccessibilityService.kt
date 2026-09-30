package com.groot.cleaning.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.groot.cleaning.automation.Bridge
import com.groot.cleaning.automation.Runner
import com.groot.cleaning.automation.Task
import com.groot.cleaning.data.GcDb
import com.groot.cleaning.data.Prefs
import kotlinx.coroutines.*

class GrootAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bridge.service = this
        Bridge.a11yEnabled.value = true
        Log.i("GC-Accessibility", "Service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        job?.cancel()
        Bridge.service = null
        Bridge.a11yEnabled.value = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { job?.cancel() }

    fun start(tasks: List<Task>) {
        if (job?.isActive == true) return
        val runner = Runner(this, Prefs(this), GcDb.get(this).dao())
        job = scope.launch { runner.runQueue(tasks) }
    }

    fun stop() { job?.cancel() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
