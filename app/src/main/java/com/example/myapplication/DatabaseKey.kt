package com.example.myapplication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object DatabaseKey {
    const val DATABASE_NAME = "attendance.db"

    private const val PREFERENCES = "database_key"
    private const val ENCRYPTED_KEY = "encrypted_key"
    private const val IV = "iv"
    private const val ALIAS = "attendance_database_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    fun get(context: Context): ByteArray {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val encryptedKey = preferences.getString(ENCRYPTED_KEY, null)
        val iv = preferences.getString(IV, null)

        if (encryptedKey != null && iv != null) {
            return Cipher.getInstance(TRANSFORMATION).run {
                init(
                    Cipher.DECRYPT_MODE,
                    keystoreKey(),
                    GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
                )
                doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
            }
        }

        check(!context.getDatabasePath(DATABASE_NAME).exists()) {
            "Database key is missing; refusing to replace it."
        }

        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keystoreKey())
        }

        check(
            preferences.edit()
                .putString(ENCRYPTED_KEY, Base64.encodeToString(cipher.doFinal(key), Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        )

        return key
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (keyStore.containsAlias(ALIAS)) {
            return (keyStore.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }
}