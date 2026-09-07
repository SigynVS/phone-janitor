package com.sigynvs.phonejanitor.email

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gmail address + app password, encrypted at rest.
 *
 * File name "email_credentials" is excluded from backup / device-transfer in res/xml rules.
 * The value stored is an app password (Google Account → Security → App passwords), not the
 * account password — spaces are stripped on save.
 */
class EmailCredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isConfigured: Boolean
        get() = !address().isNullOrBlank() && !appPassword().isNullOrBlank()

    fun address(): String? = prefs.getString(KEY_ADDRESS, null)

    fun appPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun save(address: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_ADDRESS, address.trim())
            .putString(KEY_PASSWORD, appPassword.replace(" ", ""))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "email_credentials"
        const val KEY_ADDRESS = "address"
        const val KEY_PASSWORD = "app_password"
    }
}
