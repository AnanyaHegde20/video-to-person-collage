package com.iykyk.videocollage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.iykyk.videocollage.ui.screens.HomeScreen
import com.iykyk.videocollage.ui.screens.ProcessingScreen
import com.iykyk.videocollage.ui.screens.ResultScreen
import com.iykyk.videocollage.ui.screens.VideoGalleryScreen
import com.iykyk.videocollage.viewmodel.AppViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: AppViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                videoInfo = viewModel.uiState.videoInfo,
                onSelectVideo = {
                    viewModel.loadVideoGallery()
                    navController.navigate(Screen.VideoGallery.route)
                },
                onProcessVideo = {
                    viewModel.onProcessVideo()
                    navController.navigate(Screen.Processing.route)
                },
                completedResults = viewModel.uiState.completedResults
            )
        }

        composable(Screen.VideoGallery.route) {
            VideoGalleryScreen(
                videos = viewModel.uiState.videoGalleryVideos,
                isLoading = viewModel.uiState.isVideoGalleryLoading,
                onVideoSelected = { uri ->
                    viewModel.onVideoPicked(uri)
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Processing.route) {
            val state = viewModel.uiState

            LaunchedEffect(state.showResult) {
                if (state.showResult) {
                    navController.navigate(Screen.Result.route) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                }
            }

            ProcessingScreen(
                uiState = state,
                onCancel = {
                    viewModel.cancelProcessing()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onRetry = {
                    viewModel.retryProcessing()
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                currentResult = viewModel.uiState.currentResult,
                collageBitmap = viewModel.uiState.collageBitmap,
                isSaving = viewModel.uiState.isSaving,
                feedback = viewModel.uiState.feedback,
                onBackToHome = {
                    viewModel.reset()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onSaveCollage = { viewModel.saveCollage() },
                onShareCollage = { viewModel.shareCollage() },
                onClearFeedback = { viewModel.clearFeedback() }
            )
        }
    }
}
