package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.player.PlaybackState

@OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    mediaId: String,
    fileId: Int,
    title: String,
    channelName: String,
    durationSeconds: Int,
    fileName: String?,
    thumbnailPath: String?,
    viewModel: PlayerScreenViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(mediaId, fileId) {
        viewModel.onAction(
            PlayerScreenAction.Prepare(
                mediaId = mediaId,
                fileId = fileId,
                title = title,
                channelName = channelName,
                durationSeconds = durationSeconds,
                fileName = fileName,
                thumbnailPath = thumbnailPath
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAction(PlayerScreenAction.OnAppStop)
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(PlayerScreenAction.OnAppResume)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAction(PlayerScreenAction.Release)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Reprodução", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        if (!state.isPlaceholderMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                },
                update = { view -> view.player = viewModel.player },
                onRelease = { view -> view.player = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            )
        } else if (!state.thumbnailPath.isNullOrBlank()) {
            AsyncImage(
                model = state.thumbnailPath,
                contentDescription = state.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
        Text("Canal: ${state.channelName}", color = Color.White)
        Text("Título: ${state.title}", color = Color.White)
        Text("Duração: ${state.durationSeconds / 60} min", color = Color.White)
        Text("Arquivo: ${state.fileName ?: "-"}", color = Color.White)
        Text("Estado: ${state.statusMessage}", color = Color.White)
        Text("Estado técnico: ${state.snapshot.state}", color = Color.White)

        if (state.snapshot.state is PlaybackState.Error) {
            Button(onClick = { viewModel.onAction(PlayerScreenAction.Retry) }) {
                Text("Tentar novamente")
            }
        }

        if (!state.isPlaceholderMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.onAction(PlayerScreenAction.Play) }) {
                    Text("Play")
                }
                Button(onClick = { viewModel.onAction(PlayerScreenAction.Pause) }) {
                    Text("Pause")
                }
                Button(onClick = { viewModel.onAction(PlayerScreenAction.SeekBack) }) {
                    Text("-5 min")
                }
                Button(onClick = { viewModel.onAction(PlayerScreenAction.SeekForward) }) {
                    Text("+5 min")
                }
            }
        }

        Button(onClick = onBack) {
            Text("Voltar")
        }
    }
}
