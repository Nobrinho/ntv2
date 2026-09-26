package com.ntv2.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um título que o usuário abriu para assistir. Alimenta "Continuar assistindo" (não concluídos, com
 * posição na janela útil) e o "Histórico" (tudo, mais recente primeiro). Os metadados são gravados
 * ao abrir o vídeo; a posição e o [completed] são atualizados pelos ticks de progresso do player.
 */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val posterPath: String?,
    val thumbnailPath: String?,
    val durationMs: Long,
    val lastPositionMs: Long,
    val completed: Boolean,
    val genres: String?,
    val updatedAt: Long
)
