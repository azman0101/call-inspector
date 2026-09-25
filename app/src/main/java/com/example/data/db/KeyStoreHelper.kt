package com.example.data.db

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps a random SQLCipher passphrase with a non-exportable AES-256 Android Keystore key. */
class KeyStoreHelper(context: Context) {
    private val appContext = context.applicationContext
    private val wrappedPassphraseFile = File(appContext.noBackupFilesDir, "arcep-db-key.bin")

    @Synchronized
    fun getDatabasePassphrase(): ByteArray {
        val key = getOrCreateKey()
        if (wrappedPassphraseFile.exists()) {
            val payload = wrappedPassphraseFile.readBytes()
            require(payload.size > GCM_NONCE_BYTES) { "Stored database key material is invalid" }
            val nonce = payload.copyOfRange(0, GCM_NONCE_BYTES)
            val ciphertext = payload.copyOfRange(GCM_NONCE_BYTES, payload.size)
            return Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
                doFinal(ciphertext)
            }
        }
        if (appContext.getDatabasePath("arcep_data_secure.db").exists()) {
            throw IllegalStateException("Encrypted database exists but its wrapped key material is missing")
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(passphrase)
        val temporaryFile = File(wrappedPassphraseFile.parentFile, "${wrappedPassphraseFile.name}.tmp")
        try {
            temporaryFile.writeBytes(payload)
            if (!temporaryFile.renameTo(wrappedPassphraseFile)) {
                throw IOException("Could not persist wrapped database key")
            }
        } finally {
            temporaryFile.delete()
        }
        return passphrase
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val base = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val strongBoxAvailable = appContext.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            if (strongBoxAvailable) {
                try {
                    generator.init(base.setIsStrongBoxBacked(true).build())
                    return generator.generateKey()
                } catch (_: Exception) {
                    // Some API 28+ devices advertise StrongBox inconsistently; fall back to TEE.
                }
            }
        }

        val fallbackSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            base.setIsStrongBoxBacked(false).build()
        } else {
            base.build()
        }
        generator.init(fallbackSpec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "arcep_database_wrapping_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
    }
}
