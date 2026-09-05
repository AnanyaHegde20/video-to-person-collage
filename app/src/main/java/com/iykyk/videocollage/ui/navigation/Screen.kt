package com.iykyk.videocollage.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object VideoGallery : Screen("video_gallery")
    data object Processing : Screen("processing")
    data object Result : Screen("result")
}
