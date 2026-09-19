package com.ntv2.app.core.navigation

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ntv2.app.feature.auth.domain.model.AuthState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
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
    // Sinal para a tela de baixo restaurar o foco quando o "Fechar o aplicativo?" é cancelado.
    var focusRestoreSignal by remember { mutableStateOf(0) }
    var openChannelPickerRequest by remember { mutableStateOf(0) }
    // Pedidos vindos do rail das Configurações: abrir a busca / atualizar a Biblioteca.
    var openSearchRequest by remember { mutableStateOf(0) }
    var refreshLibraryRequest by remember { mutableStateOf(0) }
    // Cobre a tela com feedback enquanto o logout (recriação do cliente TDLib) acontece.
    var loggingOut by remember { mutableStateOf(false) }
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

    // Sessão do Telegram revogada/deslogada FORA do app: detecção + reação global.
    val rootScope = rememberCoroutineScope()
    val authUi by appContainer.authRepository.authState.collectAsState(initial = AuthState())
    val lifecycleOwner = LocalLifecycleOwner.current
    // Ao voltar ao foreground, sonda a sessão (GetMe leve): se foi revogada, vira sessionExpired.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                rootScope.launch { runCatching { appContainer.authRepository.verifySessionActive() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Quando cai a sessão estando numa tela autenticada, volta ao Login (o aviso é exibido lá).
    // Mantém os canais escolhidos: ao relogar, o usuário retorna direto à biblioteca.
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(authUi.sessionExpired, currentRoute) {
        if (authUi.sessionExpired && currentRoute != null &&
            currentRoute != RoutePath.LOGIN && !loggingOut
        ) {
            // MESMA mecânica do logout manual (overlay "Saindo…" + logout completo): a sessão foi
            // revogada, então canais/mídias/estado anteriores não valem mais. logout() recria o
            // cliente TDLib; limpamos canais + canal ativo para o próximo login ser um "primeiro
            // login" (checa canais → seleção), evitando grade vazia presa carregando dados inacessíveis.
            loggingOut = true
            runCatching { appContainer.authRepository.logout() }
            runCatching { appContainer.channelRepository.clearSelectedChannels() }
            runCatching { appContainer.settingsRepository.updateActiveChannelId(0L) }
            navController.navigate(RoutePath.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            loggingOut = false
        }
    }

  Box(modifier = Modifier.fillMaxSize()) {
   androidx.compose.runtime.CompositionLocalProvider(
       com.ntv2.app.core.ui.LocalFocusRestoreSignal provides focusRestoreSignal
   ) {
    NavHost(
        navController = navController,
        startDestination = RoutePath.LOGIN
    ) {
        composable(RoutePath.LOGIN) {
            // TV inicia no QR Code; celular inicia no telefone.
            val loginAdaptive = rememberAdaptiveLayoutInfo()
            val initialLoginMode = if (loginAdaptive.isTv) LoginMode.QrCode else LoginMode.Phone
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(appContainer.authRepository, initialLoginMode)
            )
            val scope = rememberCoroutineScope()
            LoginScreen(
                viewModel = loginViewModel,
                sessionEndedNotice = authUi.sessionExpired,
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
                // "Voltar" só quando há tela anterior (veio das Configurações); no 1º login é a raiz.
                showBack = navController.previousBackStackEntry != null,
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
                    // Mesmo fluxo das Configurações: overlay "Saindo…" + aguarda o logout (recria o
                    // cliente TDLib) antes de ir ao Login, limpando canais + canal ativo.
                    loggingOut = true
                    channelScope.launch {
                        runCatching { appContainer.authRepository.logout() }
                        runCatching { appContainer.channelRepository.clearSelectedChannels() }
                        runCatching { appContainer.settingsRepository.updateActiveChannelId(0L) }
                        navController.navigate(RoutePath.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        loggingOut = false
                    }
                }
            )
        }

        composable(RoutePath.MEDIA_LIBRARY) {
            // Passo da grade: TV = 5 colunas, celular = 2 (reflete na paginação em múltiplos).
            val mediaGridStep = if (rememberAdaptiveLayoutInfo().isTv) 5 else 2
            val mediaViewModel: MediaLibraryViewModel = viewModel(
                factory = MediaLibraryViewModelFactory(
                    mediaRepository = appContainer.mediaRepository,
                    channelRepository = appContainer.channelRepository,
                    settingsRepository = appContainer.settingsRepository,
                    progressStore = appContainer.playbackProgressStore,
                    mediaDetailsCache = appContainer.mediaDetailsCache,
                    maxCardsLimit = lowRamMaxCards,
                    gridStep = mediaGridStep,
                    searchIndexRepository = appContainer.searchIndexRepository
                )
            )
            MediaLibraryScreen(
                viewModel = mediaViewModel,
                openChannelPickerRequest = openChannelPickerRequest,
                onChannelPickerConsumed = { openChannelPickerRequest = 0 },
                openSearchRequest = openSearchRequest,
                onSearchRequestConsumed = { openSearchRequest = 0 },
                refreshRequest = refreshLibraryRequest,
                onRefreshRequestConsumed = { refreshLibraryRequest = 0 },
                lowRamPlaybackWarnings = lowRamMaxCards != null,
                onOpenSettings = { navController.navigate(RoutePath.SETTINGS) { launchSingleTop = true } },
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
            val nativeBlurGlow by settings.nativeBlurGlow.collectAsState(initial = true)
            val settingsGridStep = if (rememberAdaptiveLayoutInfo().isTv) 5 else 2
            SettingsScreen(
                showCovers = showCovers,
                animationsEnabled = animationsEnabled,
                castPhotos = castPhotos,
                nativeBlurGlow = nativeBlurGlow,
                minDurationMinutes = minDuration,
                maxCards = effectiveMaxCards,
                maxCardsLimit = lowRamMaxCards,
                gridStep = settingsGridStep,
                onToggleCovers = { scope.launch { settings.updateShowCovers(it) } },
                onToggleAnimations = { scope.launch { settings.updateAnimationsEnabled(it) } },
                onToggleCastPhotos = { scope.launch { settings.updateCastPhotos(it) } },
                onToggleNativeBlurGlow = { scope.launch { settings.updateNativeBlurGlow(it) } },
                onChangeMinDuration = { scope.launch { settings.updateMinDurationMinutes(it) } },
                onChangeMaxCards = { value ->
                    scope.launch { settings.updateMaxCards(lowRamMaxCards?.let { value.coerceAtMost(it) } ?: value) }
                },
                onManageChannels = {
                    navController.navigate(RoutePath.CHANNEL_SELECTION) { launchSingleTop = true }
                },
                onOpenListedChannels = {
                    openChannelPickerRequest += 1
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        popUpTo(RoutePath.MEDIA_LIBRARY) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    // Aguarda o logout concluir (recria o cliente TDLib) ANTES de ir ao Login —
                    // senão a tela de QR abre com o cliente ainda não pronto e trava em "gerando".
                    // Limpa canais + canal ativo para que o próximo login seja um "primeiro login"
                    // (checa canais → seleção quando não houver), sem herdar IDs de outra conta.
                    // Mostra o overlay "Saindo…" enquanto a requisição roda (leva alguns segundos).
                    loggingOut = true
                    scope.launch {
                        runCatching { appContainer.authRepository.logout() }
                        runCatching { appContainer.channelRepository.clearSelectedChannels() }
                        runCatching { appContainer.settingsRepository.updateActiveChannelId(0L) }
                        navController.navigate(RoutePath.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                        loggingOut = false
                    }
                },
                onSearch = {
                    openSearchRequest += 1
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        popUpTo(RoutePath.MEDIA_LIBRARY) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onRefresh = {
                    refreshLibraryRequest += 1
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        popUpTo(RoutePath.MEDIA_LIBRARY) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenLibrary = {
                    // Volta para a Biblioteca EXISTENTE (colapsa a pilha) em vez de empilhar outra —
                    // sem isso, alternar Config/Biblioteca acumulava telas e o "voltar" refazia todo
                    // o caminho antes de sair.
                    navController.navigate(RoutePath.MEDIA_LIBRARY) {
                        popUpTo(RoutePath.MEDIA_LIBRARY) { inclusive = false }
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
            val playerAnimations by appContainer.settingsRepository.animationsEnabled.collectAsState(initial = true)
            val playerNativeBlur by appContainer.settingsRepository.nativeBlurGlow.collectAsState(initial = true)
            PlaybackScreen(
                animationsEnabled = playerAnimations,
                nativeBlurGlow = playerNativeBlur,
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
            onDismiss = { showExitDialog = false; focusRestoreSignal++ }
        )
    }

    // Overlay de saída: cobre tudo com feedback enquanto o logout é processado.
    if (loggingOut) {
        LogoutOverlay()
    }
  }
}

@Composable
private fun LogoutOverlay() {
    // Segura o foco e engole todas as teclas: nada da tela de trás pode ser acionado no logout.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20E0E0E))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { true },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFF2BEE34),
                modifier = Modifier.size(52.dp)
            )
            androidx.compose.material3.Text(
                text = "Saindo da conta…",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            androidx.compose.material3.Text(
                text = "Encerrando a sessão do Telegram com segurança",
                color = Color(0xFFB0B0B0),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}
