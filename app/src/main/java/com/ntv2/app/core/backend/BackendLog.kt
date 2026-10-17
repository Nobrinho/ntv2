package com.ntv2.app.core.backend

/** Log de aviso que não quebra nos testes JVM (android.util.Log não existe fora do aparelho). */
internal fun backendWarn(tag: String, message: String, error: Throwable? = null) {
    runCatching { android.util.Log.w(tag, message, error) }
}
