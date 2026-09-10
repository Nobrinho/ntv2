package com.ntv2.app.core.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ntv2.app.di.AppContainer
import com.ntv2.app.feature.auth.presentation.LoginScreen
import com.ntv2.app.feature.auth.presentation.viewmodel.LoginViewModel
import com.ntv2.app.feature.auth.presentation.viewmodel.LoginViewModelFactory
import com.ntv2.app.feature.channels.presentation.ChannelSelectionScreen
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionViewModel
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionViewModelFactory
import com.ntv2.app.feature.media.presentation.MediaLibraryScreen
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModel
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModelFactory
import com.ntv2.app.feature.playback.presentation.PlaybackScreen
import com.ntv2.app.feature.playback.presentation.PlayerScreenViewModel
import com.ntv2.app.feature.playback.presentation.PlayerScreenViewModelFactory
import com.ntv2.app.feature.settings.presentation.SettingsScreenPlaceholder

@Composable
fun AppNavHost(
    appContainer: AppContainer
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RoutePath.LOGIN
    ) {
        composable(RoutePath.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(appContainer.authRepository)
            )
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(RoutePath.CHANNEL_SELECTION) {
                        popUpTo(RoutePath.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(RoutePath.CHANNEL_SELECTION) {
            val channelViewModel: ChannelSelectionViewModel = viewModel(
                factory = ChannelSelectionViewModelFactory(
                    channelRepository = appContainer.channelRepository,
                    authRepository = appContainer.authRepository
                )
            )
            ChannelSelectionScreen(
                viewModel = channelViewModel,
                onOpenLibrary = { navController.navigate(RoutePath.MEDIA_LIBRARY) },
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(RoutePath.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }

        composable(RoutePath.MEDIA_LIBRARY) {
            val mediaViewModel: MediaLibraryViewModel = viewModel(
                factory = MediaLibraryViewModelFactory(
                    mediaRepository = appContainer.mediaRepository,
                    channelRepository = appContainer.channelRepository,
                    settingsRepository = appContainer.settingsRepository
                )
            )
            MediaLibraryScreen(
                viewModel = mediaViewModel,
                onOpenSettings = { navController.navigate(RoutePath.SETTINGS) },
                onOpenPlaybackPlaceholder = { mediaId, fileId, title, channelName, durationSeconds, fileName, thumbnailPath ->
                    navController.navigate(
                        RoutePath.playbackPlaceholder(
                            mediaId = Uri.encode(mediaId),
                            fileId = fileId,
                            title = Uri.encode(title),
                            channel = Uri.encode(channelName),
                            duration = durationSeconds,
                            fileName = Uri.encode(fileName ?: ""),
                            thumbnail = Uri.encode(thumbnailPath ?: "")
                        )
                    )
                }
            )
        }

        composable(RoutePath.SETTINGS) {
            SettingsScreenPlaceholder(
                onBackToLibrary = { navController.popBackStack() }
            )
        }

        composable(
            route = RoutePath.PLAYBACK_PLACEHOLDER,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("fileId") { type = NavType.IntType },
                navArgument("title") { type = NavType.StringType },
                navArgument("channel") { type = NavType.StringType },
                navArgument("duration") { type = NavType.IntType },
                navArgument("fileName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("thumbnail") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val playerViewModel: PlayerScreenViewModel = viewModel(
                factory = PlayerScreenViewModelFactory(appContainer.playbackController)
            )
            PlaybackScreen(
                mediaId = Uri.decode(backStackEntry.arguments?.getString("mediaId").orEmpty()),
                fileId = backStackEntry.arguments?.getInt("fileId") ?: 0,
                title = Uri.decode(backStackEntry.arguments?.getString("title").orEmpty()),
                channelName = Uri.decode(backStackEntry.arguments?.getString("channel").orEmpty()),
                durationSeconds = backStackEntry.arguments?.getInt("duration") ?: 0,
                fileName = Uri.decode(backStackEntry.arguments?.getString("fileName").orEmpty()),
                thumbnailPath = Uri.decode(backStackEntry.arguments?.getString("thumbnail").orEmpty()),
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
