package com.nog.quotesapp.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nog.quotesapp.ui.navigation.Routes
import com.nog.quotesapp.ui.navigation.NavItem

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
                // Keep buttons above system navigation, including three-button mode.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
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
