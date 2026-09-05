package com.wahid.codeagent

data class AgentConfig(
    val providerName: String = "DeepSeek",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-v4-flash",
    val aiApiKey: String = "",
    val githubToken: String = "",
    val owner: String = "",
    val repo: String = "",
    val baseBranch: String = "main"
)

data class LogLine(val text: String, val kind: Kind = Kind.INFO) {
    enum class Kind { INFO, TOOL, ERROR, SUCCESS }
}
