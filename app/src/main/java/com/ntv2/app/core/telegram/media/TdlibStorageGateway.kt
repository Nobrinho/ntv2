package com.ntv2.app.core.telegram.media

/** Categorias de arquivo do TDLib para a limpeza de armazenamento. */
enum class StorageFileKind {
    /** Vídeos e documentos (filmes em MKV costumam vir como documento). */
    VIDEO,
    /** Pôsteres, miniaturas e avatares. */
    IMAGE
}

/** Operações de armazenamento do TDLib usadas pela limpeza automática. */
interface TdlibStorageGateway {
    /** Suspende até o TDLib estar autenticado e pronto (as funções de storage exigem isso). */
    suspend fun awaitStorageReady()

    /**
     * Apaga arquivos das categorias [kinds] até o total ficar em [maxTotalBytes], priorizando os
     * acessados há mais tempo. Arquivos criados há menos de [immunitySeconds] são preservados
     * (protege o vídeo que acabou de começar a baixar). Retorna os bytes liberados, ou null se falhou.
     */
    suspend fun optimizeStorage(kinds: Set<StorageFileKind>, maxTotalBytes: Long, immunitySeconds: Int): Long?

    /** Remove a cópia local (parcial ou completa) de um arquivo. */
    suspend fun deleteStoredFile(fileId: Int)
}

class FakeTdlibStorageGateway : TdlibStorageGateway {
    override suspend fun awaitStorageReady() = Unit
    override suspend fun optimizeStorage(kinds: Set<StorageFileKind>, maxTotalBytes: Long, immunitySeconds: Int): Long? = 0L
    override suspend fun deleteStoredFile(fileId: Int) = Unit
}
