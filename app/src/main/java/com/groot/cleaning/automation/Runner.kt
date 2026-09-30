package com.groot.cleaning.automation

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.groot.cleaning.data.GcDao
import com.groot.cleaning.data.LogEntry
import com.groot.cleaning.data.Prefs
import com.groot.cleaning.service.GrootAccessibilityService
import com.groot.cleaning.ui.MainActivity
import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update

private const val TA = "GC-Automation"
private const val TX = "GC-Accessibility"
private const val TV = "GC-Verification"
private const val TS = "GC-Storage"
private const val TC = "GC-Accounts"

class Runner(
    private val svc: GrootAccessibilityService,
    private val prefs: Prefs,
    private val dao: GcDao
) {
    private data class TaskResult(val status: String, val message: String, val steps: List<StepUi>)

    private fun mapStatus(o: Outcome) = when (o) {
        Outcome.OK -> StepStatus.SUCCESS
        Outcome.FAIL -> StepStatus.FAILED
        Outcome.USER -> StepStatus.REQUIRES_USER_ACTION
        Outcome.UNCERTAIN -> StepStatus.UNCERTAIN
    }

    private inner class Ctx(names: List<String>) {
        val steps = names.map { StepUi(it, StepStatus.PENDING) }.toMutableList()
        var uncertain = false

        private fun set(i: Int, s: StepUi) {
            steps[i] = s
            Bridge.ui.update { it.copy(steps = steps.toList()) }
        }

        suspend fun run(name: String, block: suspend () -> StepResult): StepResult {
            val i = steps.indexOfFirst { it.name == name }
            set(i, StepUi(name, StepStatus.RUNNING))
            var r = StepResult(Outcome.FAIL, "not run")
            val attempts = prefs.retries.coerceAtLeast(1)
            for (a in 1..attempts) {
                r = block()
                if (r.outcome != Outcome.FAIL) break
                Log.w(TA, "$name failed (attempt $a/$attempts): ${r.detail}")
                if (a < attempts) delay(600)
            }
            if (r.outcome == Outcome.UNCERTAIN) uncertain = true
            Log.i(TV, "$name -> ${r.outcome}: ${r.detail}")
            set(i, StepUi(name, mapStatus(r.outcome), r.detail))
            return r
        }

        fun result(f: StepResult?): TaskResult {
            for (i in steps.indices) {
                if (steps[i].status == StepStatus.PENDING || steps[i].status == StepStatus.RUNNING)
                    steps[i] = steps[i].copy(status = StepStatus.SKIPPED)
            }
            Bridge.ui.update { it.copy(steps = steps.toList()) }
            val status = when {
                f?.outcome == Outcome.FAIL -> "FAILED"
                f?.outcome == Outcome.USER -> "REQUIRES_USER_ACTION"
                uncertain -> "VERIFICATION_UNCERTAIN"
                prefs.simulation -> "SIMULATED"
                else -> "SUCCESS"
            }
            val msg = f?.detail ?: steps.lastOrNull { it.detail.isNotBlank() }?.detail ?: ""
            return TaskResult(status, msg, steps.toList())
        }
    }

    // ---------------- queue ----------------

    suspend fun runQueue(tasks: List<Task>) {
        val remaining = tasks.map { it.key }.toMutableList()
        prefs.pending = remaining.joinToString("\n")
        val failedTasks = mutableListOf<Task>()
        var ok = 0; var failed = 0; var user = 0; var unc = 0
        var userMsg: String? = null
        var cancelled = false
        Bridge.ui.value = RunUi(running = true, total = tasks.size)
        try {
            for ((i, t) in tasks.withIndex()) {
                currentCoroutineContext().ensureActive()
                Bridge.ui.update { it.copy(title = t.title, index = i + 1, steps = emptyList(), userAction = null) }
                Log.i(TA, "Starting ${t.title}")
                val started = System.currentTimeMillis()
                val res = when (t) {
                    is Task.CleanApp -> cleanApp(t)
                    is Task.RemoveAccount -> removeAccount(t)
                }
                val dur = System.currentTimeMillis() - started
                withContext(NonCancellable) {
                    dao.insertLog(
                        LogEntry(
                            time = started, target = t.title,
                            operation = if (t is Task.CleanApp) t.op else "REMOVE_ACCOUNT",
                            status = res.status, durationMs = dur, message = res.message,
                            steps = res.steps.joinToString("\n") { "${it.name}: ${it.status}${if (it.detail.isNotBlank()) " - " + it.detail else ""}" }
                        )
                    )
                }
                remaining.remove(t.key)
                prefs.pending = remaining.joinToString("\n")
                when (res.status) {
                    "SUCCESS", "SIMULATED" -> ok++
                    "FAILED" -> { failed++; failedTasks.add(t) }
                    "REQUIRES_USER_ACTION" -> { user++; failedTasks.add(t); userMsg = "${t.title}: ${res.message}" }
                    "VERIFICATION_UNCERTAIN" -> unc++
                }
                if (res.status != "REQUIRES_USER_ACTION") exitSettings()
                if (res.status == "FAILED" && !prefs.continueAfterFailure) break
                delay(prefs.delayMs)
            }
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            withContext(NonCancellable) {
                val skipped = if (cancelled) 0 else remaining.size
                if (!cancelled) prefs.pending = remaining.joinToString("\n")
                prefs.lastRun = System.currentTimeMillis()
                prefs.lastResult = when {
                    cancelled -> "Stopped"
                    failed == 0 && user == 0 && unc == 0 && skipped == 0 -> "Success"
                    ok > 0 -> "Partial"
                    else -> "Failed"
                }
                Bridge.failed.value = failedTasks
                Bridge.ui.update {
                    it.copy(running = false, summary = Summary(ok, failed, user, skipped, unc), userAction = userMsg, stopped = cancelled)
                }
                runCatching {
                    svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            }
        }
    }

    // ---------------- app cleaning ----------------

    private suspend fun cleanApp(t: Task.CleanApp): TaskResult {
        val wantCache = t.op == "CACHE" || t.op == "BOTH"
        val wantData = t.op == "DATA" || t.op == "BOTH"
        val names = buildList {
            add("Open App Info"); add("Open Storage")
            if (wantCache) add("Clear Cache")
            if (wantData) add("Clear Data")
        }
        val c = Ctx(names)
        var r = c.run("Open App Info") { openAppInfo(t) }
        if (r.outcome != Outcome.OK) return c.result(r)
        r = c.run("Open Storage") { openStorage() }
        if (r.outcome != Outcome.OK) return c.result(r)
        if (wantCache) {
            r = c.run("Clear Cache") { clearCache() }
            if (r.outcome == Outcome.FAIL || r.outcome == Outcome.USER) return c.result(r)
        }
        if (wantData) {
            r = c.run("Clear Data") { clearData(t) }
            if (r.outcome == Outcome.FAIL || r.outcome == Outcome.USER) return c.result(r)
        }
        return c.result(null)
    }

    private suspend fun openAppInfo(t: Task.CleanApp): StepResult {
        try {
            svc.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${t.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            return StepResult(Outcome.FAIL, "Cannot open App Info: ${e.message}")
        }
        val r = waitFor { isSettings(it.packageName) && Nodes.hasText(it, listOf(t.name)) }
        return if (r != null) StepResult(Outcome.OK, "App Info detected for ${t.name}")
        else StepResult(Outcome.FAIL, "App Info page for ${t.name} not detected")
    }

    private suspend fun openStorage(): StepResult {
        val n = findScrolling(Matcher.STORAGE) ?: return StepResult(Outcome.FAIL, "Storage entry not found")
        if (!safeClick(n)) return StepResult(Outcome.FAIL, "Could not click Storage")
        val p = waitFor {
            Nodes.hasText(it, Matcher.CLEAR_CACHE) || Nodes.hasText(it, Matcher.CLEAR_DATA) || Nodes.hasText(it, Matcher.MANAGE_SPACE)
        }
        Log.i(TS, "Storage page ${if (p != null) "detected" else "NOT detected"}")
        return if (p != null) StepResult(Outcome.OK, "Storage page detected")
        else StepResult(Outcome.FAIL, "Storage page not detected")
    }

    private suspend fun clearCache(): StepResult {
        val r = root() ?: return StepResult(Outcome.FAIL, "No window")
        val n = Nodes.findByTexts(r, Matcher.CLEAR_CACHE) ?: return StepResult(Outcome.FAIL, "Clear cache button not found")
        if (!n.isEnabled) return StepResult(Outcome.OK, "Cache already empty")
        if (prefs.simulation) return StepResult(Outcome.OK, "Would click Clear Cache")
        val clicked = safeClick(n)
        val disabled = waitFor(4000) { Nodes.findByTexts(it, Matcher.CLEAR_CACHE)?.isEnabled == false } != null
        val stable = isSettings(root()?.packageName)
        return verdict(Signals(clicked, disabled, stable, true), "Cache cleared", "Clicked Clear cache; could not confirm")
    }

    private suspend fun clearData(t: Task.CleanApp): StepResult {
        val r = root() ?: return StepResult(Outcome.FAIL, "No window")
        val n = Nodes.findByTexts(r, Matcher.CLEAR_DATA)
        if (n != null) {
            if (!n.isEnabled) return StepResult(Outcome.OK, "Data already empty")
            if (prefs.simulation) return StepResult(Outcome.OK, "Would click Clear Data")
            if (!safeClick(n)) return StepResult(Outcome.FAIL, "Could not click Clear data")
            if (!confirmDialog(t.pkg)) return StepResult(Outcome.USER, "Confirmation dialog not handled. Finish manually.")
            val disabled = waitFor(5000) { Nodes.findByTexts(it, Matcher.CLEAR_DATA)?.isEnabled == false } != null
            val stable = isSettings(root()?.packageName)
            val dialogGone = root()?.let { Nodes.findByViewId(it, "android:id/button1") == null } ?: true
            return verdict(Signals(true, disabled, stable, dialogGone), "Data cleared", "Confirmed Clear data; could not confirm result")
        }
        val m = Nodes.findByTexts(r, Matcher.MANAGE_SPACE)
        if (m != null) {
            if (prefs.simulation) return StepResult(Outcome.OK, "Would open Manage space > All > Clear all data")
            if (!safeClick(m)) return StepResult(Outcome.FAIL, "Could not open Manage space")
            val page = waitFor { it.packageName?.toString() == t.pkg || isSettings(it.packageName) }
                ?: return StepResult(Outcome.USER, "Manage space page not detected. Finish manually.")
            Nodes.findByTexts(page, Matcher.ALL_TAB)?.let { safeClick(it, t.pkg); delay(600) }
            val btn = findScrolling(Matcher.CLEAR_ALL_DATA)
                ?: return StepResult(Outcome.USER, "Clear all data not found in Manage space. Finish manually.")
            if (!safeClick(btn, t.pkg)) return StepResult(Outcome.USER, "Could not click Clear all data. Finish manually.")
            if (!confirmDialog(t.pkg)) return StepResult(Outcome.USER, "Confirmation not handled. Finish manually.")
            delay(1000)
            val nowRoot = root()
            val gone = nowRoot == null || Nodes.findByTexts(nowRoot, Matcher.CLEAR_ALL_DATA)?.isEnabled != true
            val dialogGone = nowRoot?.let { Nodes.findByViewId(it, "android:id/button1") == null } ?: true
            return verdict(Signals(true, gone, isSettings(nowRoot?.packageName) || nowRoot?.packageName?.toString() == t.pkg, dialogGone),
                "Manage space data cleared", "Confirmed Clear all data; could not confirm result")
        }
        return StepResult(Outcome.FAIL, "Clear data button not found")
    }

    private fun verdict(s: Signals, okMsg: String, unsure: String): StepResult {
        val o = Verifier.decide(Verifier.level(prefs.verification), s)
        return when (o) {
            Outcome.OK -> StepResult(o, "$okMsg (verified)")
            Outcome.UNCERTAIN -> StepResult(o, unsure)
            else -> StepResult(Outcome.FAIL, "Click failed")
        }
    }

    private suspend fun confirmDialog(pkg: String?): Boolean {
        val d = waitFor(4000) {
            Nodes.findByViewId(it, "android:id/button1") != null ||
                (Nodes.findByViewId(it, "android:id/alertTitle") != null && Nodes.hasText(it, Matcher.CONFIRM))
        } ?: return false
        val btn = Nodes.findByViewId(d, "android:id/button1") ?: Nodes.findByTexts(d, Matcher.CONFIRM) ?: return false
        return safeClick(btn, pkg)
    }

    // ---------------- accounts ----------------

    private suspend fun removeAccount(t: Task.RemoveAccount): TaskResult {
        val c = Ctx(listOf("Open Accounts", "Find Account", "Remove Account", "Verify"))
        var r = c.run("Open Accounts") { openAccounts() }
        if (r.outcome != Outcome.OK) return c.result(r)
        r = c.run("Find Account") { locateAccount(t.label) }
        if (r.outcome != Outcome.OK) return c.result(r)
        r = c.run("Remove Account") { doRemove() }
        if (r.outcome != Outcome.OK) return c.result(r)
        r = c.run("Verify") { verifyRemoved(t.label) }
        return c.result(if (r.outcome == Outcome.USER || r.outcome == Outcome.FAIL) r else null)
    }

    private suspend fun openAccounts(): StepResult {
        try {
            svc.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            return StepResult(Outcome.FAIL, "Cannot open Accounts: ${e.message}")
        }
        val r = waitFor { isSettings(it.packageName) }
        return if (r != null) StepResult(Outcome.OK, "Accounts page opened") else StepResult(Outcome.FAIL, "Accounts page not detected")
    }

    private suspend fun locateAccount(label: String): StepResult {
        for (hop in 1..3) {
            delay(800)
            val r = root() ?: return StepResult(Outcome.FAIL, "No window")
            if (Nodes.hasText(r, Matcher.REMOVE_ACCOUNT)) return StepResult(Outcome.OK, "Account page detected")
            val n = findScrollingContains(label) ?: return StepResult(Outcome.FAIL, "Account '$label' not found")
            if (!safeClick(n)) return StepResult(Outcome.FAIL, "Could not open account entry")
            Log.i(TC, "Opened entry for $label (hop $hop)")
        }
        val r = root()
        return if (r != null && Nodes.hasText(r, Matcher.REMOVE_ACCOUNT)) StepResult(Outcome.OK, "Account page detected")
        else StepResult(Outcome.FAIL, "Remove account button not reachable")
    }

    private suspend fun doRemove(): StepResult {
        val r = root() ?: return StepResult(Outcome.FAIL, "No window")
        val b = Nodes.findByTexts(r, Matcher.REMOVE_ACCOUNT) ?: return StepResult(Outcome.FAIL, "Remove account button not found")
        if (prefs.simulation) return StepResult(Outcome.OK, "Would click Remove account")
        if (!safeClick(b)) return StepResult(Outcome.FAIL, "Could not click Remove account")
        val dlg = waitFor(4000) { Nodes.findByViewId(it, "android:id/button1") != null }
            ?: return StepResult(Outcome.USER, "No confirmation dialog: account is likely protected. Complete manually.")
        val btn = Nodes.findByViewId(dlg, "android:id/button1") ?: return StepResult(Outcome.USER, "Confirm button not found")
        if (!safeClick(btn)) return StepResult(Outcome.USER, "Could not confirm removal")
        delay(1500)
        val after = root()
        if (after != null && Nodes.hasTextContaining(after, Matcher.PROTECTED_HINTS))
            return StepResult(Outcome.USER, "Account removal is protected on this device. Complete manually.")
        return StepResult(Outcome.OK, "Removal confirmed")
    }

    private suspend fun verifyRemoved(label: String): StepResult {
        if (prefs.simulation) return StepResult(Outcome.OK, "Simulation: nothing removed, not verified")
        val o = openAccounts()
        if (o.outcome != Outcome.OK) return StepResult(Outcome.UNCERTAIN, "Could not reopen accounts to verify")
        delay(1500)
        val r = root() ?: return StepResult(Outcome.UNCERTAIN, "No window to verify")
        return if (Nodes.findByTextContains(r, label) == null) StepResult(Outcome.OK, "Account no longer listed (verified)")
        else StepResult(Outcome.USER, "Account still listed. Complete manually.")
    }

    // ---------------- helpers ----------------

    private fun root(): AccessibilityNodeInfo? = svc.rootInActiveWindow

    private fun isSettings(pkg: CharSequence?): Boolean = pkg?.toString()?.contains("settings", ignoreCase = true) == true

    private suspend fun waitFor(
        ms: Long = prefs.timeoutSec * 1000L,
        cond: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) {
            val r = root()
            if (r != null && cond(r)) return r
            delay(300)
        }
        return null
    }

    private suspend fun findScrolling(texts: List<String>): AccessibilityNodeInfo? {
        for (i in 0 until 6) {
            val r = root()
            if (r != null) {
                Nodes.findByTexts(r, texts)?.let { return it }
                if (!Nodes.scrollForward(r)) return null
            }
            delay(500)
        }
        return null
    }

    private suspend fun findScrollingContains(needle: String): AccessibilityNodeInfo? {
        for (i in 0 until 6) {
            val r = root()
            if (r != null) {
                Nodes.findByTextContains(r, needle)?.let { return it }
                if (!Nodes.scrollForward(r)) return null
            }
            delay(500)
        }
        return null
    }

    /** Clicks only inside Settings or the target app, only visible+enabled clickable targets. */
    private fun safeClick(node: AccessibilityNodeInfo, extraPkg: String? = null): Boolean {
        val pkg = node.packageName?.toString()
        if (!(isSettings(pkg) || (extraPkg != null && pkg == extraPkg))) {
            Log.w(TX, "Refused click outside allowed packages: $pkg")
            return false
        }
        if (!node.isVisibleToUser) return false
        val target = Nodes.clickableParent(node) ?: return false
        if (!target.isEnabled) return false
        Log.i(TX, "Click '${Nodes.text(node)}' in $pkg")
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private suspend fun exitSettings() {
        for (i in 0 until 4) {
            val r = root()
            if (r != null && isSettings(r.packageName)) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(400)
            } else return
        }
    }
}
