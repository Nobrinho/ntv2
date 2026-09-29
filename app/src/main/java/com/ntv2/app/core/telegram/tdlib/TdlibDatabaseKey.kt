package com.ntv2.app.core.telegram.tdlib

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * De onde vem a chave tentada no SetTdlibParameters. O banco do TDLib (sessão do Telegram + cache de
 * mensagens) era gravado em texto claro (databaseEncryptionKey vazio); agora é cifrado.
 */
internal enum class TdlibKeySource {
    /** Chave em uso (banco já cifrado com ela). */
    ACTIVE,
    /** Chave de uma migração interrompida: o banco pode ou não ter sido cifrado com ela. */
    PENDING,
    /** Sem chave: banco antigo, em texto claro (será cifrado logo depois de aberto). */
    LEGACY_EMPTY,
    /** Instalação nova (ou banco recriado): gera uma chave e já cria o banco cifrado. */
    NEW
}

/** Ordem das tentativas de chave ao abrir o banco do TDLib (lógica pura, testável na JVM). */
internal object TdlibKeyPlan {
    /**
     * @param activeReadable há chave ativa e ela pôde ser decifrada pelo Keystore.
     * @param pendingReadable idem para a chave pendente.
     * @param databaseExists o diretório do banco do TDLib já tem arquivos.
     */
    fun attempts(activeReadable: Boolean, pendingReadable: Boolean, databaseExists: Boolean): List<TdlibKeySource> =
        when {
            activeReadable -> listOf(TdlibKeySource.ACTIVE)
            // Migração interrompida: ou já cifrou com a pendente, ou o banco ainda está em claro.
            pendingReadable && databaseExists -> listOf(TdlibKeySource.PENDING, TdlibKeySource.LEGACY_EMPTY)
            databaseExists -> listOf(TdlibKeySource.LEGACY_EMPTY)
            else -> listOf(TdlibKeySource.NEW)
        }

    /** Depois de aberto com [source]: precisa cifrar agora com uma chave nova? */
    fun needsEncryption(source: TdlibKeySource): Boolean = source == TdlibKeySource.LEGACY_EMPTY
}

/**
 * Guarda a chave do banco do TDLib cifrada por uma chave do Android Keystore (não sai do aparelho,
 * não vai para backup). Se o Keystore perder a chave (ex.: dados restaurados em outro aparelho), a
 * chave guardada fica ilegível e o banco é recriado — pede login de novo em vez de travar.
 */
internal class TdlibDatabaseKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeKey(): ByteArray? = read(KEY_ACTIVE)
    fun pendingKey(): ByteArray? = read(KEY_PENDING)

    /** Gera e grava uma chave nova como ATIVA (banco novo, criado já cifrado com ela). */
    fun createActiveKey(): ByteArray = randomKey().also { write(KEY_ACTIVE, it); prefs.edit().remove(KEY_PENDING).commit() }

    /** Gera e grava uma chave PENDENTE, antes de pedir ao TDLib para cifrar o banco com ela. */
    fun createPendingKey(): ByteArray = randomKey().also { write(KEY_PENDING, it) }

    /** O TDLib confirmou o banco cifrado com a pendente: ela passa a ser a ativa. */
    fun promotePending() {
        val pending = prefs.getString(KEY_PENDING, null) ?: return
        prefs.edit().putString(KEY_ACTIVE, pending).remove(KEY_PENDING).commit()
    }

    /** Banco recriado do zero: descarta as chaves antigas. */
    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    // commit() (síncrono): a chave precisa estar no disco ANTES de o TDLib cifrar o banco com ela.
    private fun write(name: String, key: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val sealed = cipher.iv + cipher.doFinal(key)
        prefs.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    private fun read(name: String): ByteArray? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingKeystoreKey() ?: error("chave do Keystore ausente"),
                GCMParameterSpec(128, sealed, 0, IV_BYTES)
            )
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }.onFailure { Log.w(TAG, "chave do banco ilegível ($name): ${it.message}") }.getOrNull()
    }

    private fun existingKeystoreKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    }

    private fun keystoreKey(): SecretKey = existingKeystoreKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }
        .generateKey()

    private companion object {
        const val TAG = "TdlibDbKey"
        const val PREFS = "tdlib_db_key"
        const val KEY_ACTIVE = "active"
        const val KEY_PENDING = "pending"
        const val KEYSTORE_ALIAS = "ntv2_tdlib_db_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
