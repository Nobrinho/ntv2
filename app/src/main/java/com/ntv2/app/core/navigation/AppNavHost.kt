package com.ntv2.app.core.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import com.ntv2.app.feature.splash.presentation.SplashScreen

@Composable
fun AppNavHost(
    appContainer: AppContainer
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    // O app não deve fechar direto no "Voltar" quando está na raiz (sem tela anterior).
    // Nesse caso, pedimos confirmação. Fora da raiz, o Voltar navega normalmente (callback desabilitado).
    val currentEntry by navController.currentBackStackEntryAsState()
    val atRoot = currentEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = atRoot) { showExitDialog = true }

  Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = RoutePath.SPLASH
    ) {
        composable(RoutePath.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(RoutePath.LOGIN) {
                        popUpTo(RoutePath.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(RoutePath.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(appContainer.authRepository)
            )
            val scope = rememberCoroutineScope()
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    // Já logado: se já há canais escolhidos, pula a seleção e vai direto para a
                    // biblioteca; só mostra a seleção de canais na primeira vez (nenhum selecionado).
                    scope.launch {
                        val hasChannels = appContainer.channelRepository
                            .observeSelectedChannelIds().first().isNotEmpty()
                        val destination = if (hasChannels) {
                            RoutePath.MEDIA_LIBRARY
                        } else {
                            RoutePath.CHANNEL_SELECTION
                        }
                        navController.navigate(destination) {
                            popUpTo(RoutePath.LOGIN) { inclusive = true }
                        }
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
                onOpenLibrary = {
                    // Vai para a biblioteca sem empilhar a seleção de canais (evita duplicatas
                    // ao abrir "Canais" pela própria biblioteca).
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        popUpTo(RoutePath.CHANNEL_SELECTION) { inclusive = true }
                        launchSingleTop = true
                    }
                },
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
                    settingsRepository = appContainer.settingsRepository,
                    progressStore = appContainer.playbackProgressStore
                )
            )
            MediaLibraryScreen(
                viewModel = mediaViewModel,
                onOpenSettings = { navController.navigate(RoutePath.SETTINGS) },
                onOpenChannels = { navController.navigate(RoutePath.CHANNEL_SELECTION) },
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

    if (showExitDialog) {
        ExitConfirmDialog(
            onConfirm = { (context as? Activity)?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }
  }
}

@Composable
private fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { dismissFocus.requestFocus() } }
    // Voltar dentro do diálogo = cancelar (não fecha o app).
    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC0000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Deseja fechar o aplicativo?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.focusRequester(dismissFocus),
                    onClick = onDismiss
                ) {
                    Text("Não")
                }
                Button(onClick = onConfirm) {
                    Text("Sim, sair")
                }
            }
        }
    }
}
