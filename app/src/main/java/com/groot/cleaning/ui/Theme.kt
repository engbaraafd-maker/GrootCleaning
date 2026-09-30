package com.groot.cleaning.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Central color tokens. No hex values anywhere else. */
object GC {
    val backgroundPrimary = Color(0xFF07080A)   // Royal Black
    val backgroundSecondary = Color(0xFF0C0F16)
    val surface = Color(0xFF0F1E3A)             // Deep Navy
    val surfaceElevated = Color(0xFF16284A)
    val primary = Color(0xFF800020)             // Maroon
    val primaryDark = Color(0xFF5A0016)
    val success = Color(0xFF10B981)             // Emerald
    val error = Color(0xFFEF4444)
    val warning = Color(0xFFF59E0B)
    val textPrimary = Color(0xFFF3F4F6)
    val textSecondary = Color(0xFF9CA3AF)
    val divider = Color(0xFF24345A)
}

object L { var ar by mutableStateOf(false) }
fun tr(en: String, ar: String): String = if (L.ar) ar else en

@Composable
fun GcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GC.primary, onPrimary = Color.White,
            secondary = GC.success, background = GC.backgroundPrimary, onBackground = GC.textPrimary,
            surface = GC.surface, onSurface = GC.textPrimary,
            surfaceVariant = GC.surfaceElevated, onSurfaceVariant = GC.textSecondary,
            error = GC.error, outline = GC.divider
        ),
        content = content
    )
}
