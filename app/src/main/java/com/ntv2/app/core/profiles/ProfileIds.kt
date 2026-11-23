package com.ntv2.app.core.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object ProfileIds {
    /** Perfil inicial "Você". Também é o perfil de tudo que existia antes dos perfis (Room v5, backend). */
    const val MAIN = "p_main"
    const val KIDS = "p_kids"
}

/** Perfil ativo para escopar Minha lista, histórico e progresso. */
interface ActiveProfile {
    val activeId: StateFlow<String>
}

/** Perfil fixo (testes e telas sem perfis). */
class FixedActiveProfile(id: String = ProfileIds.MAIN) : ActiveProfile {
    override val activeId: StateFlow<String> = MutableStateFlow(id)
}

/**
 * Hash do PIN de 4 dígitos (PBKDF2-HMAC-SHA1, disponível desde o Android 6). Com só 10 mil PINs
 * possíveis o hash não impede força bruta; o objetivo é não guardar nem sincronizar o PIN em texto.
 * Formato: `pbkdf2-sha1$<iterações>$<salt hex>$<hash hex>`.
 */
object PinHash {
    private const val PREFIX = "pbkdf2-sha1"
    private const val ITERATIONS = 4_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 160

    fun hash(pin: String, salt: ByteArray = randomSalt(), iterations: Int = ITERATIONS): String =
        "$PREFIX\$$iterations\$${salt.toHex()}\$${derive(pin, salt, iterations).toHex()}"

    fun verify(pin: String, stored: String?): Boolean {
        val parts = stored?.split('$') ?: return false
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: return false
        val salt = parts[2].fromHex() ?: return false
        val expected = parts[3].fromHex() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomSalt() = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return runCatching { ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
