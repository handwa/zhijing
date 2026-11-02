package com.example.zhijing.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 云服务商配置的持久化。
 *
 * **安全设计**：API Key 绝不落明文。用 Android Keystore 里的 AES-256-GCM 密钥
 * 加密后再存 SharedPreferences；密钥本身由系统安全区（TEE/StrongBox）保管，
 * 应用卸载即销毁，也无法被备份带走。其余字段（地址、模型名）不敏感，明文存储。
 *
 * 为什么不全用 EncryptedSharedPreferences：那需要额外依赖 androidx.security，
 * 而这里只有一个字段要加密，手写不到 60 行，还能把密钥绑到 SecureRandom 生成的
 * 密钥上，避免依赖库版本问题。
 *
 * 备份策略：加密后的密文即使被系统云备份带走，在别的设备上也无法解密
 * （Keystore 密钥不能跨设备迁移），所以文件本身是安全的。
 */
class CloudConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取完整配置；从未配置过时返回 null。 */
    fun load(): CloudProvider? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return CloudProvider(
            id = id,
            displayName = prefs.getString(KEY_DISPLAY_NAME, id) ?: id,
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            apiKey = decrypt(prefs.getString(KEY_API_KEY_ENC, null)),
            stream = prefs.getBoolean(KEY_STREAM, true),
        )
    }

    /** 保存配置。密钥加密失败时抛异常，不会退化成明文落盘。 */
    fun save(provider: CloudProvider) {
        prefs.edit()
            .putString(KEY_ID, provider.id)
            .putString(KEY_DISPLAY_NAME, provider.displayName)
            .putString(KEY_BASE_URL, provider.baseUrl)
            .putString(KEY_MODEL, provider.model)
            .putString(KEY_API_KEY_ENC, encrypt(provider.apiKey))
            .putBoolean(KEY_STREAM, provider.stream)
            .apply()
    }

    /** 清除云端配置，恢复为纯端侧。 */
    fun clear() {
        prefs.edit().clear().apply()
    }

    // --- 加解密 ---------------------------------------------------------------

    private fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            // 把 IV 与密文一起存，用 ":" 分隔后 Base64
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "encrypt failed", e)
            throw IllegalStateException("无法加密 API Key：${e.message}", e)
        }
    }

    private fun decrypt(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            // 密钥被系统清除、或密文损坏：当作未配置，让用户重新填
            Log.w(TAG, "decrypt failed, treating as unconfigured", e)
            ""
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证：配置页在后台也要能读，且这不是高敏金融场景
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "CloudConfigStore"
        private const val PREFS_NAME = "cloud_provider"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "zhijing_cloud_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val KEY_ID = "id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY_ENC = "api_key_enc"
        private const val KEY_STREAM = "stream"
    }
}
