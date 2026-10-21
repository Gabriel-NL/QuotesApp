package com.nog.quotesapp.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.repository.QuoteRepository
import com.nog.quotesapp.ui.screens.QuoteItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nog.quotesapp.ui.screens.CreateQuoteScreen
import com.nog.quotesapp.ui.screens.HomeScreen
import com.nog.quotesapp.ui.screens.QuotesScreen
import com.nog.quotesapp.ui.screens.SettingsScreen
import com.nog.quotesapp.ui.screens.TagsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {

    NavHost(

        navController = navController,

        startDestination = Routes.Home.route,

        modifier = modifier

    ) {

        composable(Routes.Home.route) {
            HomeScreen()
        }

        composable(Routes.Quotes.route) {
            val context = LocalContext.current.applicationContext
            val db = remember(context) { DatabaseProvider.getDatabase(context) }
            var loadError by remember { mutableStateOf(false) }
            val quotesFlow = remember(db) {
                db.quoteDao().observeAll().catch {
                    if (it is CancellationException) throw it
                    loadError = true
                }
            }
            val quotes by quotesFlow.collectAsState(initial = emptyList())
            QuotesScreen(
                quotes = quotes.map { QuoteItem(it.id, it.text) },
                error = if (loadError) "Could not load quotes." else null,
                onCreateClick = {
                    navController.navigate("create_quote")
                }
            )
        }
        composable("create_quote") {
            val context = LocalContext.current.applicationContext
            val repository = remember(context) {
                QuoteRepository(DatabaseProvider.getDatabase(context))
            }
            val scope = rememberCoroutineScope()
            var saving by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            CreateQuoteScreen(
                saving = saving,
                error = error,
                onCreate = { text, tagIds ->
                    if (!saving) {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                repository.createQuote(text, tagIds)
                                navController.popBackStack(Routes.Quotes.route, inclusive = false)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (exception: Exception) {
                                error = "Could not save the quote. A selected tag may have been deleted. Please try again."
                            } finally { saving = false }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Tags.route) {
            TagsScreen()
        }

        composable(Routes.Settings.route) {
            SettingsScreen()
        }
    }
}
