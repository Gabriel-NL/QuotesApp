package com.nog.quotesapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.nog.quotesapp.AppContainer
import com.nog.quotesapp.viewmodel.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nog.quotesapp.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController, container: AppContainer, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Routes.Home.route, modifier = modifier) {
        composable(Routes.Home.route) { entry ->
            val model = remember(entry) { ViewModelProvider(entry, container.factory())[HomeViewModel::class.java] }
            HomeRoute(model, onEdit = { navController.navigate("edit_quote/$it") })
        }
        composable(Routes.Quotes.route) { entry ->
            val model = remember(entry) { ViewModelProvider(entry, container.factory())[QuoteViewModel::class.java] }
            QuotesRoute(
                model = model,
                onCreateClick = { navController.navigate("create_quote") },
                onEditClick = { navController.navigate("edit_quote/$it") }
            )
        }
        composable("create_quote") { entry ->
            val model = remember(entry) { ViewModelProvider(entry, container.factory())[QuoteEditorViewModel::class.java] }
            QuoteEditorScreen(model = model, onBack = { navController.popBackStack() })
        }
        composable("edit_quote/{quoteId}", arguments = listOf(navArgument("quoteId") { type = NavType.IntType })) { entry ->
            val model = remember(entry) {
                ViewModelProvider(entry, container.factory(requireNotNull(entry.arguments).getInt("quoteId")))[QuoteEditorViewModel::class.java]
            }
            QuoteEditorScreen(model = model, onBack = { navController.popBackStack() })
        }
        composable(Routes.Tags.route) { entry ->
            val model = remember(entry) { ViewModelProvider(entry, container.factory())[TagsViewModel::class.java] }
            TagsRoute(model)
        }
        composable(Routes.Settings.route) { entry ->
            val model = remember(entry) { ViewModelProvider(entry, container.factory())[SettingsViewModel::class.java] }
            SettingsRoute(model)
        }
    }
}
