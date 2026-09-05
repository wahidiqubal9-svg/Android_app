package com.wahid.codeagent

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiClient(private val baseUrl: String, private val apiKey: String, private val model: String) {
    private val client = OkHttpClient()
    private val media = "application/json".toMediaType()
    fun complete(messages: MutableList<JsonObject>, tools: JsonArray): JsonObject {
        val body = buildJsonObject {
            put("model", model); put("messages", JsonArray(messages)); put("temperature", 0.1); put("stream", false)
            put("tools", tools); put("tool_choice", "auto")
        }
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/chat/completions").post(body.toString().toRequestBody(media))
            .header("Authorization", "Bearer $apiKey").header("Content-Type", "application/json").build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty(); if (!r.isSuccessful) error("AI ${r.code}: $text")
            return Json.parseToJsonElement(text).jsonObject
        }
    }
}
