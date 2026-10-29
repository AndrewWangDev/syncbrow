package com.syncbrow.tool.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val KEY_ALIAS = "syncbrow_pm_aes_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    const val PREFIX = "ENC:"
    const val PREFIX_V2 = "ENC:V2:"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    @Volatile
    private var sessionKey: SecretKey? = null

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    fun setSessionPassword(password: String, salt: ByteArray) {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        sessionKey = SecretKeySpec(keyBytes, "AES")
    }

    fun clearSessionKey() {
        sessionKey = null
    }

    fun hasSessionKey(): Boolean = sessionKey != null

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun isEncrypted(text: String): Boolean = text.startsWith(PREFIX)

    @Throws(CryptoException::class)
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        try {
            val currentKey = sessionKey ?: getOrCreateKey()
            val isV2 = sessionKey != null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, currentKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            return if (isV2) PREFIX_V2 + base64 else PREFIX + base64
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw CryptoException("Failed to encrypt data", e)
        }
    }

    @Throws(CryptoException::class)
    fun decrypt(encryptedText: String): String {
        if (!encryptedText.startsWith(PREFIX)) {
            // Unencrypted legacy fallback
            return encryptedText
        }
        val isV2 = encryptedText.startsWith(PREFIX_V2)
        val prefix = if (isV2) PREFIX_V2 else PREFIX
        val key = if (isV2) {
            sessionKey ?: throw CryptoException("Master password required to decrypt V2 data")
        } else {
            // Legacy V1 data fallback to AndroidKeyStore
            getOrCreateKey()
        }

        try {
            val raw = Base64.decode(encryptedText.removePrefix(prefix), Base64.NO_WRAP)
            if (raw.size <= GCM_IV_LENGTH) {
                throw CryptoException("Encrypted payload too short")
            }
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(raw, 0, iv, 0, GCM_IV_LENGTH)
            val cipherBytes = ByteArray(raw.size - GCM_IV_LENGTH)
            System.arraycopy(raw, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw CryptoException("Failed to decrypt data", e)
        }
    }
}
