package com.nog.quotesapp

import android.os.Bundle
import android.os.Build
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
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
    override fun onResume() {
        super.onResume()
        com.nog.quotesapp.background.DailyQuoteScheduler.enqueue(this)
    }
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { allowed -> if(allowed) com.nog.quotesapp.background.DailyQuoteScheduler.enqueue(this) }
    private val container get() = (application as QuotesApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val permissionPrefs=getSharedPreferences("notification_permission",MODE_PRIVATE)
            if(!permissionPrefs.getBoolean("asked",false)) {
                permissionPrefs.edit().putBoolean("asked",true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Draw the Compose theme behind system bars instead of the window's light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {

            MyApplicationTheme {

                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val entryScreen = currentEntry?.destination?.route.let {
                    it == "create_quote" || it == "edit_quote/{quoteId}"
                }

                Scaffold(

                    bottomBar = {
                        if (!entryScreen) PreBuiltBottomBar(navController)
                    }

                ) { paddingValues ->

                    AppNavigation(

                        navController = navController,
                        container = container,

                        modifier = Modifier
                            .padding(paddingValues)
                            .consumeWindowInsets(paddingValues)
                    )
                }
            }
        }
    }
}
