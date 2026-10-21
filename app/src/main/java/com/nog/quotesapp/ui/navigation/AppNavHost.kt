package com.nog.quotesapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nog.quotesapp.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Routes.Home.route, modifier = modifier) {
        composable(Routes.Home.route) { HomeScreen() }
        composable(Routes.Quotes.route) {
            QuotesScreen(
                onCreateClick = { navController.navigate("create_quote") },
                onEditClick = { navController.navigate("edit_quote/$it") }
            )
        }
        composable("create_quote") {
            QuoteEditorScreen(onBack = { navController.popBackStack() })
        }
        composable("edit_quote/{quoteId}", arguments = listOf(navArgument("quoteId") { type = NavType.IntType })) { entry ->
            QuoteEditorScreen(quoteId = requireNotNull(entry.arguments).getInt("quoteId"),
                onBack = { navController.popBackStack() })
        }
        composable(Routes.Tags.route) { TagsScreen() }
        composable(Routes.Settings.route) { SettingsScreen() }
    }
}
