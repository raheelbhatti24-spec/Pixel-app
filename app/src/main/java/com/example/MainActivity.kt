package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.repository.PhotoRepository
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.ReviewScreen
import com.example.ui.theme.PixelShotTheme
import com.example.ui.viewmodel.CameraViewModel
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.ReviewViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = PhotoRepository(this, database.photoDao())

        setContent {
            PixelShotTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "camera",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("camera") {
                        val cameraViewModel: CameraViewModel = viewModel(
                            factory = CameraViewModel.Factory(repository)
                        )
                        CameraCaptureScreen(
                            viewModel = cameraViewModel,
                            onPhotoCaptured = { photoId ->
                                navController.navigate("review/$photoId")
                            },
                            onOpenGallery = {
                                navController.navigate("gallery")
                            }
                        )
                    }

                    composable(
                        route = "review/{photoId}",
                        arguments = listOf(navArgument("photoId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                        val reviewViewModel: ReviewViewModel = viewModel(
                            factory = ReviewViewModel.Factory(repository, photoId)
                        )
                        ReviewScreen(
                            viewModel = reviewViewModel,
                            onBackToCamera = {
                                navController.popBackStack("camera", inclusive = false)
                            }
                        )
                    }

                    composable("gallery") {
                        val galleryViewModel: GalleryViewModel = viewModel(
                            factory = GalleryViewModel.Factory(repository)
                        )
                        GalleryScreen(
                            viewModel = galleryViewModel,
                            onBackToCamera = {
                                navController.popBackStack()
                            },
                            onSelectPhoto = { photoId ->
                                navController.navigate("review/$photoId")
                            }
                        )
                    }
                }
            }
        }
    }
}
