package com.wahid.codeagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class AgentEngine(private val github: GitHubApi, private val ai: AiClient, private val config: AgentConfig, private val log: (LogLine) -> Unit) {
    private val tools = buildJsonArray {
        add(tool("list_files", "List all repository files."))
        add(tool("read_file", "Read a text file. Returns its blob SHA and content.", arrayOf("path")))
        add(tool("create_branch", "Create the working branch from the base branch.", arrayOf("branch")))
        add(tool("write_file", "Create or replace a text file on the working branch. For existing files pass the SHA returned by read_file.", arrayOf("path", "content"), optional = arrayOf("sha")))
        add(tool("delete_file", "Delete an existing file on the working branch. Pass its SHA.", arrayOf("path", "sha")))
        add(tool("create_pull_request", "Create a draft PR from the working branch to the base branch.", arrayOf("title", "body")))
    }
    private fun tool(name: String, desc: String, required: Array<String> = emptyArray(), optional: Array<String> = emptyArray()): JsonObject = buildJsonObject {
        put("type", "function"); putJsonObject("function") {
            put("name", name); put("description", desc); putJsonObject("parameters") {
                put("type", "object"); putJsonObject("properties") {
                    (required + optional).forEach { putJsonObject(it) { put("type", "string") } }
                }
                putJsonArray("required") { required.forEach { add(it) } }
            }
        }
    }
    suspend fun run(task: String) = withContext(Dispatchers.IO) {
        val workingBranch = "agent/${System.currentTimeMillis()}"
        val system = """
            You are Code Agent Mobile, an autonomous GitHub coding agent.
            Repository: ${config.owner}/${config.repo}. Base branch: ${config.baseBranch}.
            Inspect first. Never write to the base branch. Before any write/delete, create the working branch exactly once.
            Read relevant files before editing. Preserve unrelated code. Use complete file contents for write_file.
            After requested work is complete, create a DRAFT pull request. Never claim tests were run unless a tool actually ran them.
        """.trimIndent()
        val messages = mutableListOf(buildJsonObject { put("role", "system"); put("content", system) }, buildJsonObject { put("role", "user"); put("content", task) })
        var branchCreated = false
        var branchName = workingBranch
        repeat(24) {
            val response = ai.complete(messages, tools)
            val message = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: error("AI returned no message")
            messages.add(message)
            val calls = message["tool_calls"]?.jsonArray.orEmpty()
            if (calls.isEmpty()) { log(LogLine(message["content"]?.jsonPrimitive?.content ?: "Agent finished.", LogLine.Kind.SUCCESS)); return@withContext }
            for (tc in calls) {
                val id = tc.jsonObject["id"]!!.jsonPrimitive.content
                val fn = tc.jsonObject["function"]!!.jsonObject
                val name = fn["name"]!!.jsonPrimitive.content
                val args = Json.parseToJsonElement(fn["arguments"]!!.jsonPrimitive.content).jsonObject
                log(LogLine("$name $args", LogLine.Kind.TOOL))
                val result = try { execute(name, args, branchName, branchCreated).also { if (name == "create_branch") { branchCreated = true; branchName = args["branch"]?.jsonPrimitive?.content ?: branchName } } } catch (e: Exception) { "ERROR: ${e.message}" }
                messages.add(buildJsonObject { put("role", "tool"); put("tool_call_id", id); put("content", result) })
            }
        }
        error("Agent reached the maximum tool-call limit.")
    }
    private fun execute(name: String, a: JsonObject, branch: String, branchCreated: Boolean): String = when (name) {
        "list_files" -> github.listFiles(config.owner, config.repo, config.baseBranch).joinToString("\n")
        "read_file" -> { val p=a["path"]!!.jsonPrimitive.content; val o=github.readFile(config.owner,config.repo,p,if(branchCreated) branch else config.baseBranch); "SHA: ${o["sha"]?.jsonPrimitive?.content}\nCONTENT:\n${github.decodeContent(o)}" }
        "create_branch" -> { val b=a["branch"]?.jsonPrimitive?.content?.takeIf{it.isNotBlank()} ?: branch; github.createBranch(config.owner,config.repo,b,config.baseBranch); "Created branch $b" }
        "write_file" -> { if(!branchCreated) error("Create the working branch first."); github.upsertFile(config.owner,config.repo,a["path"]!!.jsonPrimitive.content,a["content"]!!.jsonPrimitive.content,branch,a["sha"]?.jsonPrimitive?.content); "Updated ${a["path"]}" }
        "delete_file" -> { if(!branchCreated) error("Create the working branch first."); github.deleteFile(config.owner,config.repo,a["path"]!!.jsonPrimitive.content,branch,a["sha"]!!.jsonPrimitive.content); "Deleted ${a["path"]}" }
        "create_pull_request" -> { if(!branchCreated) error("No working branch exists."); val p=github.createPr(config.owner,config.repo,branch,config.baseBranch,a["title"]!!.jsonPrimitive.content,a["body"]!!.jsonPrimitive.content); "Draft PR created: ${p["html_url"]?.jsonPrimitive?.content ?: p}" }
        else -> "Unknown tool: $name"
    }
}
