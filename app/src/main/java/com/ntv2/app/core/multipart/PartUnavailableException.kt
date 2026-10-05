package com.ntv2.app.core.multipart

import java.io.IOException

/**
 * Uma parte do filme dividido não está disponível: acabou antes do tamanho registrado (parte truncada ou ainda em
 * upload) ou não abriu no TDLib (mensagem apagada). É um [IOException] para a recuperação de I/O do player
 * continuar tentando; se não adiantar, a tela mostra QUAL parte falhou em vez de um erro técnico da emenda.
 *
 * [part] começa em 1 (como no nome `.partNNofMM`).
 */
class PartUnavailableException(
    val part: Int,
    val total: Int,
    detail: String,
    cause: Throwable? = null
) : IOException("Parte $part de $total indisponível: $detail", cause)
