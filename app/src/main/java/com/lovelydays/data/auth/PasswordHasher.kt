package com.lovelydays.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stretches the password with PBKDF2. The database stores the salt and the hash, never the password.
 */
object PasswordHasher {
    private const val ITERATIONS = 20_000
    private const val BITS = 256

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: String, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = hash(password, salt)
        return MessageDigest.isEqual(actual, expected)
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
