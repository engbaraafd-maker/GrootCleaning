package com.groot.cleaning.automation

enum class Level { BASIC, STANDARD, STRICT }

data class Signals(
    val clicked: Boolean,
    val buttonDisabled: Boolean,
    val pageStable: Boolean,
    val dialogGone: Boolean
)

object Verifier {
    /** OK only with evidence. Clicked-but-unproven => UNCERTAIN. Not clicked => FAIL. */
    fun decide(level: Level, s: Signals): Outcome {
        if (!s.clicked) return Outcome.FAIL
        val ok = when (level) {
            Level.BASIC -> s.buttonDisabled || s.pageStable || s.dialogGone
            Level.STANDARD -> s.buttonDisabled
            Level.STRICT -> s.buttonDisabled && s.pageStable && s.dialogGone
        }
        return if (ok) Outcome.OK else Outcome.UNCERTAIN
    }

    fun level(name: String): Level = runCatching { Level.valueOf(name) }.getOrDefault(Level.STANDARD)
}
