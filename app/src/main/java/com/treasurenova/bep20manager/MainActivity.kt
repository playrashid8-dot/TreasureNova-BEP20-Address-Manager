package com.treasurenova.bep20manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treasurenova.bep20manager.ui.ActionBlue
import com.treasurenova.bep20manager.ui.AppViewModel
import com.treasurenova.bep20manager.ui.DetailScreen
import com.treasurenova.bep20manager.ui.Gold
import com.treasurenova.bep20manager.ui.HistoryScreen
import com.treasurenova.bep20manager.ui.HomeScreen
import com.treasurenova.bep20manager.ui.ImportScreen
import com.treasurenova.bep20manager.ui.MultiScreen
import com.treasurenova.bep20manager.ui.Navy
import com.treasurenova.bep20manager.ui.ProcessingScreen
import com.treasurenova.bep20manager.ui.ResultsScreen
import com.treasurenova.bep20manager.ui.SettingsScreen
import com.treasurenova.bep20manager.ui.SingleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Navy,
                    primary = ActionBlue,
                    secondary = Gold,
                )
            ) {
                val nav = rememberNavController()
                val model: AppViewModel = viewModel()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(nav) }
                    composable("single") { SingleScreen(nav, model) }
                    composable("multi") { MultiScreen(nav, model) }
                    composable("import") { ImportScreen(nav, model) }
                    composable("process") { ProcessingScreen(nav, model) }
                    composable("results") { ResultsScreen(nav, model) }
                    composable("detail") { DetailScreen(nav, model) }
                    composable("history") { HistoryScreen(nav, model) }
                    composable("settings") { SettingsScreen(nav, model) }
                }
            }
        }
    }
}
