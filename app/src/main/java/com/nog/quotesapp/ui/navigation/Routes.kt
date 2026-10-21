package com.nog.quotesapp.ui.navigation

sealed class Routes(val route: String)
{
    object Home : Routes("home")
    object Quotes : Routes("quotes")
    object Tags : Routes("tags")
    object Settings : Routes("settings")
}

data class NavItem(
    val title: String,
    val route: String
)