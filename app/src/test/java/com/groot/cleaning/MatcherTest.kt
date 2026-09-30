package com.groot.cleaning

import com.groot.cleaning.automation.*
import org.junit.Assert.*
import org.junit.Test

class MatcherTest {
    @Test fun exactMatchIgnoresCaseAndSpaces() {
        assertTrue(Matcher.matches("  CLEAR   DATA ", Matcher.CLEAR_DATA))
        assertTrue(Matcher.matches("محو البيانات", Matcher.CLEAR_DATA))
    }
    @Test fun clearAllDataIsNotClearData() {
        assertFalse(Matcher.matches("Clear all data", Matcher.CLEAR_DATA))
        assertTrue(Matcher.matches("Clear all data", Matcher.CLEAR_ALL_DATA))
    }
    @Test fun nullNeverMatches() = assertFalse(Matcher.matches(null, Matcher.STORAGE))
    @Test fun containsAnyWorks() = assertTrue(Matcher.containsAny("Blocked by administrator", Matcher.PROTECTED_HINTS))
}

class VerifierTest {
    private fun s(c: Boolean, b: Boolean, p: Boolean, d: Boolean) = Signals(c, b, p, d)
    @Test fun notClickedFails() = assertEquals(Outcome.FAIL, Verifier.decide(Level.BASIC, s(false, true, true, true)))
    @Test fun clickedWithoutEvidenceIsUncertain() = assertEquals(Outcome.UNCERTAIN, Verifier.decide(Level.STANDARD, s(true, false, true, true)))
    @Test fun standardNeedsDisabledButton() = assertEquals(Outcome.OK, Verifier.decide(Level.STANDARD, s(true, true, false, false)))
    @Test fun strictNeedsAllSignals() {
        assertEquals(Outcome.UNCERTAIN, Verifier.decide(Level.STRICT, s(true, true, true, false)))
        assertEquals(Outcome.OK, Verifier.decide(Level.STRICT, s(true, true, true, true)))
    }
    @Test fun unknownLevelDefaultsToStandard() = assertEquals(Level.STANDARD, Verifier.level("nope"))
}
