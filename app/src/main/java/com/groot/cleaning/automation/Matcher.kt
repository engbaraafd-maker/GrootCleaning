package com.groot.cleaning.automation

/** Matching layer: every system-UI string lives here so it can be edited per device. */
object Matcher {
    val STORAGE = listOf("Storage & cache", "Storage", "التخزين", "مساحة التخزين")
    val CLEAR_CACHE = listOf("Clear cache", "محو ذاكرة التخزين المؤقت", "مسح ذاكرة التخزين المؤقت")
    val CLEAR_DATA = listOf("Clear data", "Clear storage", "محو البيانات", "مسح البيانات")
    val MANAGE_SPACE = listOf("Manage space", "إدارة المساحة")
    val ALL_TAB = listOf("All", "الكل")
    val CLEAR_ALL_DATA = listOf("Clear all data", "محو جميع البيانات", "مسح جميع البيانات")
    val CONFIRM = listOf("OK", "Delete", "Clear", "موافق", "حذف", "محو", "مسح")
    val REMOVE_ACCOUNT = listOf("Remove account", "إزالة الحساب", "حذف الحساب")
    val PROTECTED_HINTS = listOf("administrator", "not allowed", "غير مسموح", "مشرف", "can't remove", "cannot remove")

    fun norm(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    fun matches(text: String?, candidates: List<String>): Boolean {
        if (text == null) return false
        val t = norm(text)
        return candidates.any { norm(it) == t }
    }

    fun containsAny(text: String?, candidates: List<String>): Boolean {
        if (text == null) return false
        val t = norm(text)
        return candidates.any { t.contains(norm(it)) }
    }
}
