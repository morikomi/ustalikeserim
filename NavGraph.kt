package com.ersinozdogan.ustalikeserimv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ersinozdogan.ustalikeserimv.ui.camera.CameraScreen
import com.ersinozdogan.ustalikeserimv.ui.camera.CameraViewModel
import com.ersinozdogan.ustalikeserimv.ui.login.LoginScreen

@Composable
fun NavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val cameraViewModel: CameraViewModel = viewModel()

    NavHost(
        navController    = navController,
        startDestination = "login",
        modifier         = modifier
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("camera")
                }
            )
        }
        composable("camera") {
            CameraScreen(
                viewModel = cameraViewModel
            )
        }
    }
}