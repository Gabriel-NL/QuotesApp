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
import com.nog.quotesapp.ui.components.AppNavigation
import com.nog.quotesapp.ui.navigation.PreBuiltBottomBar
import com.nog.quotesapp.ui.theme.MyApplicationTheme

var debugText by mutableStateOf("Waiting...")
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            MyApplicationTheme {

                val navController = rememberNavController()

                Scaffold(

                    bottomBar = {
                        PreBuiltBottomBar(navController)
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