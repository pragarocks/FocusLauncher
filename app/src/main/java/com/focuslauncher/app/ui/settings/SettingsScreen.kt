package com.focuslauncher.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.ui.components.PlaceholderScreen

@Composable
fun SettingsScreen(
    onOpenThemes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = "Settings", modifier = modifier) {
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onOpenThemes) { Text("Themes") }
    }
}
