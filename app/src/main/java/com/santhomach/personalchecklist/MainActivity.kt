package com.santhomach.personalchecklist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.santhomach.personalchecklist.ui.checklist.ChecklistScreen
import com.santhomach.personalchecklist.ui.day.DayDetailScreen
import com.santhomach.personalchecklist.ui.history.HistoryScreen
import com.santhomach.personalchecklist.ui.main.MainScreen
import com.santhomach.personalchecklist.ui.theme.ChecklistTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChecklistTheme { AppNavHost() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches up on the daily copy if the 2am job was delayed or the phone was off.
        lifecycleScope.launch { (application as ChecklistApp).repository.ensureToday() }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "main") {
        composable("main") {
            MainScreen(onOpenChecklist = { navController.navigate("checklist/$it") })
        }
        composable(
            "checklist/{checklistId}",
            arguments = listOf(navArgument("checklistId") { type = NavType.LongType }),
        ) {
            ChecklistScreen(
                onBack = { navController.popBackStack() },
                onOpenHistory = { navController.navigate("history/$it") },
            )
        }
        composable(
            "history/{checklistId}",
            arguments = listOf(navArgument("checklistId") { type = NavType.LongType }),
        ) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { navController.navigate("day/$it") },
            )
        }
        composable(
            "day/{dayId}",
            arguments = listOf(navArgument("dayId") { type = NavType.LongType }),
        ) {
            DayDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
