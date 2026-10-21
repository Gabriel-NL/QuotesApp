package com.nog.quotesapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nog.quotesapp.ui.components.BottomNavbar
import com.nog.quotesapp.ui.screens.*

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route
    ) {
        composable(Routes.Home.route) {
            HomeScreen()
        }

        composable(Routes.Quotes.route) {
            QuotesScreen()
        }

        composable(Routes.Tags.route) {
            TagsScreen()
        }

        composable(Routes.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun PreBuiltBottomBar(
    navController: NavController
) {

    BottomNavbar(

        buttons = listOf(

            NavItem(
                title = "Home",
                route = Routes.Home.route
            ),

            NavItem(
                title = "Quotes",
                route = Routes.Quotes.route
            ),

            NavItem(
                title = "Tags",
                route = Routes.Tags.route
            ),

            NavItem(
                title = "Settings",
                route = Routes.Settings.route
            )
        ),

        onButtonClick = { item ->

            navController.navigate(item.route)
        }
    )
}