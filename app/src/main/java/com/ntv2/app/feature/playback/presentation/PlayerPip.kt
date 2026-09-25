package com.ntv2.app.feature.playback.presentation

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Picture-in-picture do player (só celular/tablet). O player "arma" o PiP enquanto um vídeo toca;
 * sair do app (Home/gesto) com ele armado encolhe o vídeo para a janelinha em vez de pausar.
 */
object PlayerPip {
    @Volatile
    var armed: Boolean = false

    @Volatile
    private var aspect: Rational = Rational(16, 9)

    fun isSupported(activity: Activity?): Boolean =
        activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Atualiza proporção e auto-entrada (Android 12+ entra sozinho ao sair do app). */
    fun update(activity: Activity?, armed: Boolean, videoWidth: Int, videoHeight: Int) {
        this.armed = armed
        if (videoWidth > 0 && videoHeight > 0) aspect = clampAspect(videoWidth, videoHeight)
        if (!isSupported(activity)) return
        runCatching { activity!!.setPictureInPictureParams(params(autoEnter = armed)) }
    }

    fun enter(activity: Activity?): Boolean {
        if (!isSupported(activity)) return false
        return runCatching { activity!!.enterPictureInPictureMode(params(autoEnter = armed)) }.getOrDefault(false)
    }

    /** Android 8–11 não têm auto-entrada: a Activity chama isto no onUserLeaveHint. */
    fun onUserLeaveHint(activity: Activity) {
        if (armed && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enter(activity)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun params(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

    // O Android recusa proporções fora de ~1:2,39 … 2,39:1.
    private fun clampAspect(w: Int, h: Int): Rational {
        val ratio = w.toFloat() / h
        return when {
            ratio > 2.39f -> Rational(239, 100)
            ratio < 1 / 2.39f -> Rational(100, 239)
            else -> Rational(w, h)
        }
    }
}
