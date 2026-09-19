package com.ntv2.app.di

import android.content.Context
import androidx.room.Room
import com.ntv2.app.core.database.AppDatabase
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.cache.PlaybackCacheManager
import com.ntv2.app.core.player.controller.DefaultPlaybackController
import com.ntv2.app.core.player.controller.PlaybackController
import com.ntv2.app.core.player.config.PlaybackTuning
import com.ntv2.app.core.player.download.ProgressiveDownloadPlanner
import com.ntv2.app.core.player.exoplayer.DefaultExoPlayerProvider
import com.ntv2.app.core.player.exoplayer.ExoPlayerProvider
import com.ntv2.app.core.player.io.DiskWindowPolicy
import com.ntv2.app.core.player.io.GrowingFileDataSourceFactory
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.core.player.progress.RoomPlaybackProgressStore
import com.ntv2.app.core.player.session.DefaultPlaybackCoordinator
import com.ntv2.app.core.player.session.PlaybackResourceManager
import com.ntv2.app.core.player.source.DefaultPlaybackSourceResolver
import com.ntv2.app.core.player.source.PlaybackSourceResolver
import com.ntv2.app.core.player.telegram.TelegramPlaybackDataSource
import com.ntv2.app.core.player.telegram.TdlibTelegramPlaybackDataSource
import com.ntv2.app.core.preferences.UserPreferencesDataStore
import com.ntv2.app.core.telegram.auth.FakeTdlibAuthGateway
import com.ntv2.app.core.telegram.auth.TdlibAuthGateway
import com.ntv2.app.core.telegram.channels.FakeTdlibChannelsGateway
import com.ntv2.app.core.telegram.channels.TdlibChannelsGateway
import com.ntv2.app.core.telegram.media.FakeTdlibMediaGateway
import com.ntv2.app.core.telegram.media.FakeTdlibPlaybackGateway
import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import com.ntv2.app.core.telegram.tdlib.RealTdlibGateway
import com.ntv2.app.core.telegram.tdlib.TdlibRuntimeConfig
import com.ntv2.app.feature.auth.data.datasource.TelegramAuthDataSource
import com.ntv2.app.feature.auth.data.datasource.TdlibTelegramAuthDataSource
import com.ntv2.app.feature.auth.data.repository.TelegramAuthRepository
import com.ntv2.app.feature.auth.data.session.AuthSessionStore
import com.ntv2.app.feature.auth.domain.AuthRepository
import com.ntv2.app.feature.channels.data.datasource.TelegramChannelsDataSource
import com.ntv2.app.feature.channels.data.datasource.TdlibTelegramChannelsDataSource
import com.ntv2.app.feature.channels.data.repository.DefaultChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.media.data.datasource.TelegramMediaDataSource
import com.ntv2.app.feature.media.data.datasource.TdlibTelegramMediaDataSource
import com.ntv2.app.feature.media.data.index.SearchIndexRepository
import com.ntv2.app.feature.media.data.repository.DefaultMediaRepository
import com.ntv2.app.feature.media.domain.MediaRepository
import com.ntv2.app.feature.media.domain.MediaDetailsCache
import com.ntv2.app.feature.settings.domain.FakeSettingsRepository
import com.ntv2.app.feature.settings.domain.SettingsRepository
import com.ntv2.app.core.storage.PendingFileDeletions
import com.ntv2.app.core.storage.SharedPrefsPendingFileDeletions
import com.ntv2.app.core.storage.StatFsDeviceStorage
import com.ntv2.app.core.storage.StorageJanitor
import com.ntv2.app.core.storage.TrackingPlaybackGateway
import com.ntv2.app.core.telegram.media.FakeTdlibStorageGateway
import android.content.pm.ApplicationInfo
import coil.imageLoader
import java.io.File

interface AppContainer {
    val tdlibAuthGateway: TdlibAuthGateway
    val tdlibChannelsGateway: TdlibChannelsGateway
    val tdlibMediaGateway: TdlibMediaGateway
    val tdlibPlaybackGateway: TdlibPlaybackGateway

    val telegramAuthDataSource: TelegramAuthDataSource
    val telegramChannelsDataSource: TelegramChannelsDataSource
    val telegramMediaDataSource: TelegramMediaDataSource
    val telegramPlaybackDataSource: TelegramPlaybackDataSource

    val playbackCoordinator: PlaybackCoordinator
    val playbackSourceResolver: PlaybackSourceResolver
    val playbackController: PlaybackController
    val playbackProgressStore: PlaybackProgressStore
    val videoPrefetcher: com.ntv2.app.core.player.prefetch.VideoPrefetcher
    val storageJanitor: StorageJanitor

    val userPreferencesDataStore: UserPreferencesDataStore
    val authSessionStore: AuthSessionStore
    val appDatabase: AppDatabase

    val authRepository: AuthRepository
    val channelRepository: ChannelRepository
    val mediaRepository: MediaRepository
    val settingsRepository: SettingsRepository
    val mediaDetailsCache: MediaDetailsCache
    val searchIndexRepository: SearchIndexRepository
}

class DefaultAppContainer(
    context: Context
) : AppContainer {
    private val appContext = context.applicationContext
    private val playbackTuning = PlaybackTuning()
    private val tdlibConfig = TdlibRuntimeConfig(
        enabled = com.ntv2.app.BuildConfig.TELEGRAM_REAL_ENABLED,
        apiId = com.ntv2.app.BuildConfig.TELEGRAM_API_ID,
        apiHash = com.ntv2.app.BuildConfig.TELEGRAM_API_HASH
    )

    private val realTdlibGateway: RealTdlibGateway? by lazy {
        if (!tdlibConfig.enabled) return@lazy null
        RealTdlibGateway(
            context = appContext,
            config = tdlibConfig
        )
    }

    override val tdlibAuthGateway: TdlibAuthGateway by lazy {
        realTdlibGateway ?: FakeTdlibAuthGateway()
    }

    override val authSessionStore: AuthSessionStore by lazy {
        AuthSessionStore(appContext)
    }

    override val tdlibChannelsGateway: TdlibChannelsGateway by lazy {
        realTdlibGateway ?: FakeTdlibChannelsGateway()
    }

    override val tdlibMediaGateway: TdlibMediaGateway by lazy {
        realTdlibGateway ?: FakeTdlibMediaGateway()
    }

    private val pendingFileDeletions: PendingFileDeletions by lazy {
        SharedPrefsPendingFileDeletions(appContext)
    }

    // Todo vídeo que ganha bytes no disco fica registrado até ser apagado (limpeza de órfãos).
    override val tdlibPlaybackGateway: TdlibPlaybackGateway by lazy {
        TrackingPlaybackGateway(
            delegate = realTdlibGateway ?: FakeTdlibPlaybackGateway(appContext),
            pending = pendingFileDeletions
        )
    }

    override val storageJanitor: StorageJanitor by lazy {
        StorageJanitor(
            storageGateway = realTdlibGateway ?: FakeTdlibStorageGateway(),
            pending = pendingFileDeletions,
            deviceStorage = StatFsDeviceStorage(appContext.filesDir),
            clearImageDiskCache = { appContext.imageLoader.diskCache?.clear() },
            tdlibFilesDir = File(appContext.filesDir, "tdlib/files"),
            verboseLogging = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        )
    }

    override val telegramAuthDataSource: TelegramAuthDataSource by lazy {
        TdlibTelegramAuthDataSource(
            tdlibGateway = tdlibAuthGateway,
            sessionStore = authSessionStore
        )
    }

    override val telegramChannelsDataSource: TelegramChannelsDataSource by lazy {
        TdlibTelegramChannelsDataSource(tdlibChannelsGateway)
    }

    override val telegramMediaDataSource: TelegramMediaDataSource by lazy {
        TdlibTelegramMediaDataSource(tdlibMediaGateway)
    }

    override val telegramPlaybackDataSource: TelegramPlaybackDataSource by lazy {
        TdlibTelegramPlaybackDataSource(tdlibPlaybackGateway)
    }

    private val playbackCacheManager: PlaybackCacheManager by lazy {
        PlaybackCacheManager(
            cacheDir = File(appContext.cacheDir, "telegram-playback"),
            maxBytes = playbackTuning.cacheMaxBytes,
            trimTargetBytes = playbackTuning.cacheTrimTargetBytes,
            maxFiles = playbackTuning.maxCachedFiles
        )
    }

    private val exoPlayerProvider: ExoPlayerProvider by lazy {
        DefaultExoPlayerProvider(appContext)
    }

    private val playbackResourceManager: PlaybackResourceManager by lazy {
        PlaybackResourceManager(exoPlayerProvider)
    }

    private val progressivePlanner: ProgressiveDownloadPlanner by lazy {
        ProgressiveDownloadPlanner(playbackTuning)
    }

    private val growingFileDataSourceFactory: GrowingFileDataSourceFactory by lazy {
        GrowingFileDataSourceFactory(
            partialFileAccessor = telegramPlaybackDataSource,
            stallTimeoutMs = playbackTuning.ioStallTimeoutMs,
            readAheadBytes = playbackTuning.aheadWindowBytes,
            onLowStorage = { storageJanitor.onLowStorageDuringPlayback() },
            // Libera do disco o trecho já assistido: um filme ocupa ~250 MB em vez do tamanho todo.
            diskWindow = DiskWindowPolicy(
                headPinBytes = playbackTuning.diskHeadPinBytes,
                tailPinBytes = playbackTuning.diskTailPinBytes,
                keepBehindBytes = playbackTuning.diskKeepBehindBytes,
                minEvictBytes = playbackTuning.diskMinEvictBytes
            )
        )
    }

    override val playbackProgressStore: PlaybackProgressStore by lazy {
        RoomPlaybackProgressStore(appDatabase.playbackProgressDao())
    }

    override val playbackCoordinator: PlaybackCoordinator by lazy {
        DefaultPlaybackCoordinator(
            playbackDataSource = telegramPlaybackDataSource,
            resourceManager = playbackResourceManager,
            cacheManager = playbackCacheManager,
            planner = progressivePlanner,
            dataSourceFactory = growingFileDataSourceFactory,
            progressStore = playbackProgressStore
        )
    }

    override val playbackSourceResolver: PlaybackSourceResolver by lazy {
        DefaultPlaybackSourceResolver(telegramPlaybackDataSource)
    }

    override val videoPrefetcher: com.ntv2.app.core.player.prefetch.VideoPrefetcher by lazy {
        com.ntv2.app.core.player.prefetch.TdlibVideoPrefetcher(tdlibPlaybackGateway)
    }

    override val playbackController: PlaybackController by lazy {
        DefaultPlaybackController(
            coordinator = playbackCoordinator,
            sourceResolver = playbackSourceResolver,
            progressStore = playbackProgressStore,
            storageGuard = storageJanitor
        )
    }

    override val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(appContext)
    }

    override val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "ntv2.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    override val authRepository: AuthRepository by lazy {
        TelegramAuthRepository(telegramAuthDataSource)
    }

    override val channelRepository: ChannelRepository by lazy {
        DefaultChannelRepository(
            telegramChannelsDataSource = telegramChannelsDataSource,
            selectedChannelDao = appDatabase.selectedChannelDao()
        )
    }

    override val mediaRepository: MediaRepository by lazy {
        DefaultMediaRepository(telegramMediaDataSource)
    }

    override val settingsRepository: SettingsRepository by lazy {
        FakeSettingsRepository(userPreferencesDataStore)
    }

    override val mediaDetailsCache: MediaDetailsCache by lazy { MediaDetailsCache() }

    override val searchIndexRepository: SearchIndexRepository by lazy {
        SearchIndexRepository(indexUrl = SEARCH_INDEX_URL)
    }

    private companion object {
        // Índice de busca publicado pelo bot (GitHub Pages). Trocar aqui se mudar de host.
        const val SEARCH_INDEX_URL = "https://nobrinho.github.io/nbrplay-privacy/docs/index.json"
    }
}
