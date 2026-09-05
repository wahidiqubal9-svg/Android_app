package com.wahid.codeagent

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.net.URLEncoder

class GitHubApi(private val token: String) {
    private val client = OkHttpClient()
    private val media = "application/json".toMediaType()
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun req(url: String, method: String = "GET", body: String? = null): Request {
        val b = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) b.method(method, body.toRequestBody(media)) else if (method != "GET") b.method(method, "".toRequestBody(media))
        return b.build()
    }
    private fun call(request: Request): JsonElement {
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("GitHub ${r.code}: $text")
            return if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
        }
    }
    fun authenticatedLogin(): String = call(req("https://api.github.com/user")).jsonObject["login"]!!.jsonPrimitive.content

    fun listRepositories(): List<String> {
        val result = mutableListOf<String>()
        var page = 1
        while (page <= 20) {
            val json = call(req("https://api.github.com/user/repos?per_page=100&page=$page&sort=full_name")).jsonArray
            if (json.isEmpty()) break
            result += json.mapNotNull { it.jsonObject["full_name"]?.jsonPrimitive?.content }
            if (json.size < 100) break
            page++
        }
        return result.distinct()
    }

    fun listBranches(owner: String, repo: String): List<String> {
        val result = mutableListOf<String>()
        var page = 1
        while (page <= 20) {
            val json = call(req("https://api.github.com/repos/${enc(owner)}/${enc(repo)}/branches?per_page=100&page=$page")).jsonArray
            if (json.isEmpty()) break
            result += json.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            if (json.size < 100) break
            page++
        }
        return result.distinct()
    }

    fun branchSha(owner: String, repo: String, branch: String): String = call(req("https://api.github.com/repos/$owner/$repo/git/ref/heads/${enc(branch)}")).jsonObject["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
    fun listFiles(owner: String, repo: String, branch: String): List<String> {
        val sha = branchSha(owner, repo, branch)
        return call(req("https://api.github.com/repos/$owner/$repo/git/trees/$sha?recursive=1")).jsonObject["tree"]!!.jsonArray.mapNotNull { if (it.jsonObject["type"]?.jsonPrimitive?.content == "blob") it.jsonObject["path"]?.jsonPrimitive?.content else null }
    }
    fun readFile(owner: String, repo: String, path: String, branch: String): JsonObject = call(req("https://api.github.com/repos/$owner/$repo/contents/${path.trimStart('/')}?ref=${enc(branch)}")).jsonObject
    fun createBranch(owner: String, repo: String, newBranch: String, baseBranch: String) {
        val sha = branchSha(owner, repo, baseBranch)
        val body = buildJsonObject { put("ref", "refs/heads/$newBranch"); put("sha", sha) }
        call(req("https://api.github.com/repos/$owner/$repo/git/refs", "POST", body.toString()))
    }
    fun upsertFile(owner: String, repo: String, path: String, content: String, branch: String, sha: String?) {
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val body = buildJsonObject { put("message", "Code Agent: update $path"); put("content", encoded); put("branch", branch); if (sha != null) put("sha", sha) }
        call(req("https://api.github.com/repos/$owner/$repo/contents/${path.trimStart('/')}", "PUT", body.toString()))
    }
    fun deleteFile(owner: String, repo: String, path: String, branch: String, sha: String) {
        val body = buildJsonObject { put("message", "Code Agent: delete $path"); put("sha", sha); put("branch", branch) }
        call(req("https://api.github.com/repos/$owner/$repo/contents/${path.trimStart('/')}", "DELETE", body.toString()))
    }
    fun createPr(owner: String, repo: String, head: String, base: String, title: String, body: String): JsonObject {
        val obj = buildJsonObject { put("title", title); put("body", body); put("head", head); put("base", base); put("draft", true) }
        return call(req("https://api.github.com/repos/$owner/$repo/pulls", "POST", obj.toString())).jsonObject
    }
    fun decodeContent(obj: JsonObject): String = String(Base64.getDecoder().decode(obj["content"]!!.jsonPrimitive.content.replace("\n", "")), Charsets.UTF_8)
}
