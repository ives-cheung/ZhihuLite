package com.ives.zhihulite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cookie ciphertext is private to this app, excluded from Android backup. */
fun hasAuthenticationCookie(cookie: String): Boolean = cookie.split(';').any {
    val parts=it.trim().split('=',limit=2)
    parts.size==2 && parts[0]=="z_c0" && parts[1].isNotBlank() && parts[1]!="deleted"
}
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("zhihu_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("zhihu_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun decrypt(field: String): String = runCatching {
        val encoded = prefs.getString(field, null) ?: return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrDefault("")
    private fun encrypt(field: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString(field, Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)).commit()
    }
    fun read(): String = decrypt("value")
    fun capture(): Boolean {
        val cookie = CookieManager.getInstance().getCookie("https://www.zhihu.com/").orEmpty()
        // A guest page must not overwrite an already saved authenticated session.
        if(hasAuthenticationCookie(cookie)) {
            if(cookie!=read()) {
                fun token(value: String)=value.split(';').map{it.trim().split('=',limit=2)}.firstOrNull{it.size==2 && it[0]=="z_c0"}?.get(1)
                if(token(cookie)!=token(read()))prefs.edit().remove("profile").commit()
                encrypt("value",cookie)
            }
        } else if(!loggedIn() && cookie.isNotEmpty()) encrypt("value",cookie)
        return loggedIn()
    }
    fun loggedIn() = hasAuthenticationCookie(read())
    fun profile(): AccountProfile? = runCatching { AccountProfile.from(org.json.JSONObject(decrypt("profile"))) }.getOrNull()
    fun storeProfile(json: org.json.JSONObject) { encrypt("profile", json.toString()) }
    fun clear() { prefs.edit().clear().apply(); CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
}
