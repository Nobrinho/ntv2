package com.ntv2.app.core.backend.sync

/** Tipo de alteração local na personalização, para o agendador escolher quando sincronizar. */
enum class LocalChange {
    /** Favoritar/desfavoritar, abrir, remover do histórico: sobe logo (com debounce curto). */
    EDIT,

    /** Tick de progresso do player: agrupado, sobe no máximo a cada ~60 s (só o último valor). */
    PROGRESS,

    /** Player pausou ou parou: sobe o último progresso agora (com debounce curto). */
    PLAYBACK_SETTLED
}
