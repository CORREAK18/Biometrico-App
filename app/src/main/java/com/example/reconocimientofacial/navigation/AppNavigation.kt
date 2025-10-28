package com.example.reconocimientofacial.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reconocimientofacial.ui.screens.HomeScreen
import com.example.reconocimientofacial.ui.screens.RecognizeScreen
import com.example.reconocimientofacial.ui.screens.RegisterScreen
import com.example.reconocimientofacial.ui.screens.ViewAllScreen
import com.example.reconocimientofacial.viewmodel.FaceViewModel

@Composable
fun AppNavigation(viewModel: FaceViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToRecognize = {
                    navController.navigate(Screen.Recognize.route)
                },
                onNavigateToViewAll = {
                    navController.navigate(Screen.ViewAll.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Recognize.route) {
            RecognizeScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ViewAll.route) {
            ViewAllScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
