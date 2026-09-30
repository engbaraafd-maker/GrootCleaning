package com.groot.cleaning.automation

import com.groot.cleaning.data.GcDao
import com.groot.cleaning.service.GrootAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow

enum class Outcome { OK, FAIL, USER, UNCERTAIN }
data class StepResult(val outcome: Outcome, val detail: String = "")
enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, REQUIRES_USER_ACTION, UNCERTAIN }
data class StepUi(val name: String, val status: StepStatus, val detail: String = "")
data class Summary(val success: Int, val failed: Int, val user: Int, val skipped: Int, val uncertain: Int)

data class RunUi(
    val running: Boolean = false,
    val title: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val steps: List<StepUi> = emptyList(),
    val summary: Summary? = null,
    val userAction: String? = null,
    val stopped: Boolean = false
)

sealed class Task {
    abstract val key: String
    abstract val title: String

    data class CleanApp(val pkg: String, val name: String, val op: String) : Task() {
        override val key get() = "A|$pkg"
        override val title get() = name
    }

    data class RemoveAccount(val label: String) : Task() {
        override val key get() = "C|$label"
        override val title get() = label
    }
}

object Bridge {
    @Volatile var service: GrootAccessibilityService? = null
    val a11yEnabled = MutableStateFlow(false)
    val ui = MutableStateFlow(RunUi())
    val failed = MutableStateFlow<List<Task>>(emptyList())
}

object TaskBuilder {
    suspend fun all(dao: GcDao): List<Task> =
        dao.appsNow().map { Task.CleanApp(it.packageName, it.appName, it.operation) } +
            dao.accountsNow().map { Task.RemoveAccount(it.label) }

    suspend fun fromKeys(dao: GcDao, keys: List<String>): List<Task> {
        val apps = dao.appsNow().associateBy { it.packageName }
        val accs = dao.accountsNow().map { it.label }.toSet()
        return keys.mapNotNull { k ->
            val p = k.split("|", limit = 2)
            if (p.size < 2) null
            else if (p[0] == "A") apps[p[1]]?.let { Task.CleanApp(it.packageName, it.appName, it.operation) }
            else if (p[0] == "C" && p[1] in accs) Task.RemoveAccount(p[1])
            else null
        }
    }
}
