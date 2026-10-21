package com.nog.quotesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nog.quotesapp.ui.navigation.AppNavigation
import com.nog.quotesapp.ui.components.PreBuiltBottomBar
import com.nog.quotesapp.ui.theme.MyApplicationTheme


var debugText by mutableStateOf("Waiting...")
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            MyApplicationTheme {

                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val entryScreen = currentEntry?.destination?.route == "create_quote"

                Scaffold(

                    bottomBar = {
                        if (!entryScreen) PreBuiltBottomBar(navController)
                    }

                ) { paddingValues ->

                    AppNavigation(

                        navController = navController,

                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}
