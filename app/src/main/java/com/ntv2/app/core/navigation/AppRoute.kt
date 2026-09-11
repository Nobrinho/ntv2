package com.ntv2.app.core.navigation

sealed interface AppRoute {
    data object Login : AppRoute
    data object ChannelSelection : AppRoute
    data object MediaLibrary : AppRoute
    data object Settings : AppRoute
    data object PlaybackPlaceholder : AppRoute
}

object RoutePath {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val CHANNEL_SELECTION = "channels"
    const val MEDIA_LIBRARY = "media"
    const val SETTINGS = "settings"
    const val PLAYBACK_PLACEHOLDER =
        "playback-placeholder/{mediaId}?fileId={fileId}&title={title}&channel={channel}&duration={duration}&fileName={fileName}&thumbnail={thumbnail}"

    fun playbackPlaceholder(
        mediaId: String,
        fileId: Int,
        title: String,
        channel: String,
        duration: Int,
        fileName: String,
        thumbnail: String
    ): String {
        return "playback-placeholder/$mediaId?fileId=$fileId&title=$title&channel=$channel&duration=$duration&fileName=$fileName&thumbnail=$thumbnail"
    }
}
