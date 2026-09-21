package com.duylt.demo.axiom.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duylt.demo.axiom.ui.lab.LabScreen
import com.duylt.demo.axiom.ui.products.ProductsScreen
import com.duylt.demo.axiom.ui.users.UsersScreen

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Products("products", "Products", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Users("users", "Users", Icons.Outlined.People, Icons.Filled.People),
    Lab("lab", "Lab", Icons.Outlined.Science, Icons.Filled.Science),
}

/**
 * Three tabs, one Axiom feature group each: Products = `data()`/`state()`/transform+publish;
 * Users = Paging 3 over the store; Lab = every task's state, WorkManager, the probes, Koin, logs.
 */
@Composable
fun DemoAppScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Products.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Products.route) { ProductsScreen() }
            composable(Destination.Users.route) { UsersScreen() }
            composable(Destination.Lab.route) { LabScreen() }
        }
    }
}
