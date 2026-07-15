package com.focuslauncher.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.focuslauncher.app.ui.drawer.DrawerScreen
import com.focuslauncher.app.ui.home.HomeScreen
import com.focuslauncher.app.ui.settings.SettingsScreen
import com.focuslauncher.app.ui.themes.ThemesScreen

@Composable
fun FocusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = FocusDestination.Home.route,
        modifier = modifier,
    ) {
        composable(FocusDestination.Home.route) {
            HomeScreen(
                onOpenDrawer = { navController.navigate(FocusDestination.Drawer.route) },
                onOpenSettings = { navController.navigate(FocusDestination.Settings.route) },
            )
        }
        composable(FocusDestination.Drawer.route) { DrawerScreen() }
        composable(FocusDestination.Themes.route) { ThemesScreen() }
        composable(FocusDestination.Settings.route) {
            SettingsScreen(
                onOpenThemes = { navController.navigate(FocusDestination.Themes.route) },
            )
        }
    }
}
