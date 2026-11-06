package com.u30pro.panel.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The admin password is required to re-login whenever the single device
 * session expires, so it has to be kept. Encrypt with a Keystore-backed AES key
 * instead of writing it to plain SharedPreferences.
 */
class CredentialsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("u30_credentials", Context.MODE_PRIVATE)

    fun hasSaved(): Boolean = prefs.contains(KEY_PAYLOAD)

    fun save(password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PAYLOAD, payload).apply()
    }

    fun load(): String? {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            if (bytes.size <= GCM_IV_BYTES) return null
            val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "u30pro-admin-password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_PAYLOAD = "password_payload"
    }
}