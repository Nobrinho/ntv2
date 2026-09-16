package com.ntv2.app.core.navigation

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
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
import com.ntv2.app.core.ui.ConfirmDialog
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
import com.ntv2.app.feature.settings.presentation.SettingsScreen
import com.ntv2.app.feature.splash.presentation.SplashScreen

@Composable
fun AppNavHost(
    appContainer: AppContainer
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lowRamMaxCards = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.isLowRamDevice == true) 60 else null
    }
    var showExitDialog by remember { mutableStateOf(false) }
    var openChannelPickerRequest by remember { mutableStateOf(0) }
    // Splash como OVERLAY: o app real (Login → Biblioteca) monta e carrega POR TRÁS enquanto a
    // intro cobre a tela; ao terminar, ela some (fade) e revela a Biblioteca já pronta.
    // Respeita o toggle "Animações" das Configurações (off → pula a intro).
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (!appContainer.settingsRepository.animationsEnabled.first()) showSplash = false
    }

    // O app não deve fechar direto no "Voltar" quando está na raiz (sem tela anterior).
    // Nesse caso, pedimos confirmação. Fora da raiz, o Voltar navega normalmente (callback desabilitado).
    val currentEntry by navController.currentBackStackEntryAsState()
    val atRoot = currentEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = atRoot) { showExitDialog = true }

  Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = RoutePath.LOGIN
    ) {
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
            val channelScope = rememberCoroutineScope()
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
                onOpenSettings = {
                    navController.navigate(RoutePath.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                onLogout = {
                    // A VM já chamou o logout; limpa canais + canal ativo (próximo login = seleção).
                    channelScope.launch {
                        runCatching { appContainer.channelRepository.clearSelectedChannels() }
                        runCatching { appContainer.settingsRepository.updateActiveChannelId(0L) }
                    }
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
                    progressStore = appContainer.playbackProgressStore,
                    mediaDetailsCache = appContainer.mediaDetailsCache,
                    maxCardsLimit = lowRamMaxCards
                )
            )
            MediaLibraryScreen(
                viewModel = mediaViewModel,
                openChannelPickerRequest = openChannelPickerRequest,
                lowRamPlaybackWarnings = lowRamMaxCards != null,
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
            val scope = rememberCoroutineScope()
            val settings = appContainer.settingsRepository
            val showCovers by settings.showCovers.collectAsState(initial = true)
            val animationsEnabled by settings.animationsEnabled.collectAsState(initial = true)
            val minDuration by settings.minDurationMinutes.collectAsState(initial = 15)
            val savedMaxCards by settings.maxCards.collectAsState(initial = 150)
            val effectiveMaxCards = lowRamMaxCards?.let { savedMaxCards.coerceAtMost(it) } ?: savedMaxCards
            val castPhotos by settings.castPhotos.collectAsState(initial = true)
            SettingsScreen(
                showCovers = showCovers,
                animationsEnabled = animationsEnabled,
                castPhotos = castPhotos,
                minDurationMinutes = minDuration,
                maxCards = effectiveMaxCards,
                maxCardsLimit = lowRamMaxCards,
                onToggleCovers = { scope.launch { settings.updateShowCovers(it) } },
                onToggleAnimations = { scope.launch { settings.updateAnimationsEnabled(it) } },
                onToggleCastPhotos = { scope.launch { settings.updateCastPhotos(it) } },
                onChangeMinDuration = { scope.launch { settings.updateMinDurationMinutes(it) } },
                onChangeMaxCards = { value ->
                    scope.launch { settings.updateMaxCards(lowRamMaxCards?.let { value.coerceAtMost(it) } ?: value) }
                },
                onManageChannels = { navController.navigate(RoutePath.CHANNEL_SELECTION) },
                onOpenListedChannels = {
                    openChannelPickerRequest += 1
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    // Aguarda o logout concluir (recria o cliente TDLib) ANTES de ir ao Login —
                    // senão a tela de QR abre com o cliente ainda não pronto e trava em "gerando".
                    // Limpa canais + canal ativo para que o próximo login seja um "primeiro login"
                    // (checa canais → seleção quando não houver), sem herdar IDs de outra conta.
                    scope.launch {
                        runCatching { appContainer.authRepository.logout() }
                        runCatching { appContainer.channelRepository.clearSelectedChannels() }
                        runCatching { appContainer.settingsRepository.updateActiveChannelId(0L) }
                        navController.navigate(RoutePath.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
                onOpenLibrary = {
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        launchSingleTop = true
                    }
                }
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
                factory = PlayerScreenViewModelFactory(
                    appContainer.playbackController,
                    appContainer.mediaDetailsCache
                )
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

    // Intro de marca por cima de tudo, enquanto o app carrega por trás.
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = "Deseja fechar o aplicativo?",
            message = null,
            icon = Icons.Filled.ExitToApp,
            confirmLabel = "Sim, sair",
            confirmIcon = Icons.Filled.ExitToApp,
            cancelLabel = "Não",
            cancelIcon = Icons.Filled.Close,
            destructive = true,
            onConfirm = { (context as? Activity)?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }
  }
}
