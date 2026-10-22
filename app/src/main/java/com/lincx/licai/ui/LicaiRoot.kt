package com.lincx.licai.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lincx.licai.LicaiApp
import com.lincx.licai.data.FinanceRepository
import com.lincx.licai.ui.assets.AssetsScreen
import com.lincx.licai.ui.goals.GoalDetailScreen
import com.lincx.licai.ui.goals.GoalsScreen
import com.lincx.licai.ui.home.HomeScreen
import com.lincx.licai.ui.ledger.LedgerScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "总览", Icons.Outlined.Home),
    Tab("ledger", "记账", Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab("goals", "目标", Icons.Outlined.Savings),
    Tab("assets", "配置", Icons.Outlined.PieChart),
)

@Composable
fun repo(): FinanceRepository {
    val app = LocalContext.current.applicationContext as LicaiApp
    return app.container.repository
}

@Composable
fun LicaiRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route } || route == null
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route || (route == null && tab.route == "home")
                        NavigationBarItem(
                            selected = selected,
                            onClick = { openTab(nav, tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenLedger = { openTab(nav, "ledger") },
                    onOpenGoals = { openTab(nav, "goals") },
                    onOpenAssets = { openTab(nav, "assets") },
                    onOpenGoal = { id -> nav.navigate("goal/$id") },
                )
            }
            composable("ledger") { LedgerScreen() }
            composable("goals") {
                GoalsScreen(onOpenGoal = { id -> nav.navigate("goal/$id") })
            }
            composable("assets") { AssetsScreen() }
            composable(
                route = "goal/{goalId}",
                arguments = listOf(navArgument("goalId") { type = NavType.LongType }),
            ) { backStack ->
                val id = backStack.arguments?.getLong("goalId") ?: return@composable
                GoalDetailScreen(goalId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun openTab(nav: NavHostController, target: String) {
    nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
