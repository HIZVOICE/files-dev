package com.hyperfiles.manager

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the secure-folder lock: type (pin/pattern), salted SHA-256 hash, biometric flag. */
object SecurityPrefs {

    private fun p(c: Context) = c.getSharedPreferences("secure", Context.MODE_PRIVATE)

    fun isSet(c: Context): Boolean = p(c).getString("hash", null) != null
    fun lockType(c: Context): String = p(c).getString("type", "pin") ?: "pin"
    fun useBiometric(c: Context): Boolean = p(c).getBoolean("biometric", false)
    fun setBiometric(c: Context, v: Boolean) = p(c).edit().putBoolean("biometric", v).apply()

    fun setLock(c: Context, type: String, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        p(c).edit()
            .putString("type", type)
            .putString("salt", saltHex)
            .putString("hash", hash(saltHex, secret))
            .apply()
    }

    fun verify(c: Context, secret: String): Boolean {
        val salt = p(c).getString("salt", null) ?: return false
        val stored = p(c).getString("hash", null) ?: return false
        return hash(salt, secret) == stored
    }

    fun clear(c: Context) {
        p(c).edit().remove("type").remove("salt").remove("hash").apply()
    }

    private fun hash(salt: String, secret: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$salt|$secret".toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
