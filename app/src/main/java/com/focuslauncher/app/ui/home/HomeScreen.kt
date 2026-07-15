package com.focuslauncher.app.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.ui.components.PlaceholderScreen

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = "Focus Launcher", modifier = modifier) {
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onOpenDrawer) { Text("App drawer") }
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}
