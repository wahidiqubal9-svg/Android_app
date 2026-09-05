package com.wahid.codeagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** GitHub OAuth Device Flow. No personal access token is typed into the app. */
class GitHubOAuth(private val context: Context) {
    private val client = OkHttpClient()
    private val deviceUrl = "https://github.com/login/device/code"
    private val tokenUrl = "https://github.com/login/oauth/access_token"

    data class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val intervalSeconds: Long
    )

    suspend fun start(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "GitHub OAuth Client ID is required." }
        val body = FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("scope", "repo")
            .build()
        val request = Request.Builder()
            .url(deviceUrl)
            .post(body)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub device authorization failed: HTTP ${response.code}")
            val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
            if (json["error"] != null) error(json["error_description"]?.jsonPrimitive?.content ?: "GitHub authorization failed")
            DeviceCode(
                userCode = json["user_code"]!!.jsonPrimitive.content,
                verificationUri = json["verification_uri"]!!.jsonPrimitive.content,
                deviceCode = json["device_code"]!!.jsonPrimitive.content,
                intervalSeconds = json["interval"]?.jsonPrimitive?.content?.toLongOrNull() ?: 5L
            )
        }
    }

    fun openVerificationPage(uri: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    suspend fun waitForToken(clientId: String, device: DeviceCode): String = withContext(Dispatchers.IO) {
        var waitSeconds = device.intervalSeconds.coerceAtLeast(5L)
        repeat(120) {
            delay(waitSeconds * 1000L)
            val body = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("device_code", device.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val request = Request.Builder()
                .url(tokenUrl)
                .post(body)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
                val token = json["access_token"]?.jsonPrimitive?.content
                if (!token.isNullOrBlank()) return@withContext token
                when (json["error"]?.jsonPrimitive?.content) {
                    "authorization_pending" -> Unit
                    "slow_down" -> waitSeconds += 5L
                    "expired_token" -> error("GitHub login expired. Please start again.")
                    "access_denied" -> error("GitHub login was denied.")
                    else -> if (json["error"] != null) error(json["error_description"]?.jsonPrimitive?.content ?: "GitHub login failed")
                }
            }
        }
        error("GitHub login timed out. Please try again.")
    }

    suspend fun login(clientId: String): String {
        val device = start(clientId)
        openVerificationPage(device.verificationUri)
        return waitForToken(clientId, device)
    }
}
