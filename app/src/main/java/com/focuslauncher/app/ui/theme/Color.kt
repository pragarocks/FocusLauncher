package com.focuslauncher.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Seeded from the plan's "Frosted Teal" spec (§7). These are only the app-chrome
// defaults for pre-Android-12 devices (no dynamic colour); per-theme colours arrive
// once the icon-theme engine drives them (Phase 3+).
private val Teal = Color(0xFF8FE3CE)
private val DeepTeal = Color(0xFF0E2F2A)
private val Mist = Color(0xFFDFF7EF)

val DarkColorScheme = darkColorScheme(
    primary = Teal,
    background = DeepTeal,
    surface = DeepTeal,
)

val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    secondary = Teal,
    background = Mist,
)
