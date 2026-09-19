package com.ntv2.app.core.storage

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Registro PERSISTIDO dos vídeos que têm bytes no disco do TDLib e ainda não foram apagados.
 * O apagamento normal acontece ao sair do player; se o processo morre antes (app fechado à força,
 * falta de memória, queda), o parcial ficava órfão para sempre. Na próxima abertura o
 * [StorageJanitor] apaga tudo que continua registrado aqui.
 */
interface PendingFileDeletions {
    fun markDownloading(fileId: Int)
    fun markDeleted(fileId: Int)
    /** Ids registrados por sessões ANTERIORES do app (candidatos a órfão). */
    fun orphans(): Set<Int>
    /** true se o arquivo foi aberto nesta sessão (não pode ser apagado pela limpeza). */
    fun isActiveInThisSession(fileId: Int): Boolean
}

class SharedPrefsPendingFileDeletions(context: Context) : PendingFileDeletions {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val persisted: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>().apply {
        addAll(prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() })
    }
    private val fromPreviousSessions: Set<Int> = persisted.toSet()
    private val activeThisSession: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    override fun markDownloading(fileId: Int) {
        if (fileId <= 0) return
        activeThisSession.add(fileId)
        // Chamado a cada pedido de faixa: só grava quando o id é novo.
        if (persisted.add(fileId)) save()
    }

    override fun markDeleted(fileId: Int) {
        activeThisSession.remove(fileId)
        if (persisted.remove(fileId)) save()
    }

    override fun orphans(): Set<Int> = fromPreviousSessions.filter { it in persisted }.toSet()

    override fun isActiveInThisSession(fileId: Int): Boolean = fileId in activeThisSession

    @Synchronized
    private fun save() {
        prefs.edit().putStringSet(KEY, persisted.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val PREFS = "ntv2_storage"
        const val KEY = "pending_video_deletions"
    }
}
