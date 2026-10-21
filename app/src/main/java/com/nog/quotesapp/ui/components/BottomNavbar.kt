package com.nog.quotesapp.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nog.quotesapp.ui.navigation.NavItem
import com.nog.quotesapp.ui.navigation.Routes
import com.nog.quotesapp.ui.screens.HomeScreen
import com.nog.quotesapp.ui.screens.QuotesScreen
import com.nog.quotesapp.ui.screens.SettingsScreen
import com.nog.quotesapp.ui.screens.TagsScreen
import com.nog.quotesapp.ui.screens.testQuotes

@Composable
fun BottomNavbar(
    buttons: List<NavItem>,
    onButtonClick: (NavItem) -> Unit
) {

    Surface(
        tonalElevation = 4.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),

            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            buttons.forEach { item ->

                Button(

                    onClick = {
                        onButtonClick(item)
                    },

                    modifier = Modifier
                        .width(120.dp)
                        .height(56.dp)
                ) {

                    Text(item.title)
                }
            }
        }
    }
}

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