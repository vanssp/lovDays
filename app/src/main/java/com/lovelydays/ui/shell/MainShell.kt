package com.lovelydays.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lovelydays.ui.alerts.AlertsScreen
import com.lovelydays.ui.calendar.CalendarScreen
import com.lovelydays.ui.forecast.ForecastScreen
import com.lovelydays.ui.profile.ProfileEditorScreen
import com.lovelydays.ui.sync.SyncScreen

private enum class Dest(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    Calendar("calendar", "Календарь", "Календарь", Icons.Outlined.CalendarMonth),
    Forecast("forecast", "Прогноз", "Прогноз", Icons.Outlined.WbSunny),
    Alerts("alerts", "Сигналы", "Напоминания", Icons.Outlined.Notifications),
    Sync("sync", "Синхр.", "Синхронизация с календарём", Icons.Outlined.EventAvailable),
    Profile("profile", "Профиль", "Профиль", Icons.Outlined.Person),
}

@Composable
fun MainShell() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: Dest.Calendar.route
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                LovelyRail(
                    current = current,
                    onSelect = { dest -> nav.openTab(dest.route) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!wide) {
                        LovelyBar(current = current, onSelect = { dest -> nav.openTab(dest.route) })
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    NavHost(
                        navController = nav,
                        startDestination = Dest.Calendar.route,
                    ) {
                        composable(Dest.Calendar.route) { CalendarScreen() }
                        composable(Dest.Forecast.route) { ForecastScreen() }
                        composable(Dest.Alerts.route) { AlertsScreen() }
                        composable(Dest.Sync.route) { SyncScreen() }
                        composable(Dest.Profile.route) { ProfileEditorScreen(onboarding = false) }
                    }
                }
            }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LovelyBar(current: String, onSelect: (Dest) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Dest.entries.forEach { dest ->
            NavigationBarItem(
                selected = current == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.description) },
                label = { Text(dest.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun LovelyRail(current: String, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Dest.entries.forEach { dest ->
            NavigationRailItem(
                selected = current == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.description) },
                label = { Text(dest.label, maxLines = 1) },
            )
        }
    }
}
