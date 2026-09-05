package com.wahid.codeagent

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodeAgentApp(SecureStore(this), this) }
    }
}

@Composable
fun CodeAgentApp(store: SecureStore, context: Context) {
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(store.get("baseUrl") ?: "https://api.deepseek.com/v1") }
    var model by remember { mutableStateOf(store.get("model") ?: "deepseek-v4-flash") }
    var aiKey by remember { mutableStateOf(store.get("aiKey") ?: "") }
    var ghToken by remember { mutableStateOf(store.get("ghToken") ?: "") }
    var ghClientId by remember { mutableStateOf(store.get("ghClientId") ?: "") }
    var owner by remember { mutableStateOf(store.get("owner") ?: "") }
    var repo by remember { mutableStateOf(store.get("repo") ?: "Medicalcoupons.in") }
    var branch by remember { mutableStateOf(store.get("branch") ?: "main") }
    var task by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var loggingIn by remember { mutableStateOf(false) }
    var githubUser by remember { mutableStateOf(if (ghToken.isNotBlank()) owner else "") }
    val logs = remember { mutableStateListOf<LogLine>() }

    MaterialTheme {
        Scaffold { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("AI", style = MaterialTheme.typography.titleMedium)
                    Field(baseUrl, "OpenAI-compatible base URL") { baseUrl = it }
                    Field(model, "Model") { model = it }
                    Secret(aiKey, "AI API key") { aiKey = it }
                }

                item {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sign in with GitHub so you don't need to create or paste a personal access token.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Field(ghClientId, "GitHub OAuth Client ID") { ghClientId = it }
                    Button(
                        onClick = {
                            store.put("ghClientId", ghClientId)
                            loggingIn = true
                            logs.add(LogLine("Starting GitHub sign-in…"))
                            scope.launch {
                                try {
                                    val oauth = GitHubOAuth(context)
                                    val token = oauth.login(ghClientId)
                                    store.put("ghToken", token)
                                    ghToken = token
                                    val login = GitHubApi(token).authenticatedLogin()
                                    owner = login
                                    githubUser = login
                                    store.put("owner", login)
                                    logs.add(LogLine("Signed in to GitHub as $login.", LogLine.Kind.SUCCESS))
                                } catch (e: Exception) {
                                    logs.add(LogLine(e.message ?: "GitHub login failed", LogLine.Kind.ERROR))
                                } finally {
                                    loggingIn = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loggingIn && ghClientId.isNotBlank()
                    ) { Text(if (loggingIn) "Waiting for GitHub…" else "Sign in with GitHub") }

                    if (githubUser.isNotBlank()) {
                        Text("✓ GitHub connected as $githubUser", style = MaterialTheme.typography.bodyMedium)
                    }

                    Field(repo, "Repository") { repo = it }
                    Field(branch, "Base branch") { branch = it }
                }

                item {
                    Button(
                        onClick = {
                            store.put("baseUrl", baseUrl)
                            store.put("model", model)
                            store.put("aiKey", aiKey)
                            store.put("ghToken", ghToken)
                            store.put("ghClientId", ghClientId)
                            store.put("owner", owner)
                            store.put("repo", repo)
                            store.put("branch", branch)
                            logs.add(LogLine("Settings saved securely.", LogLine.Kind.SUCCESS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save settings") }
                }

                item {
                    Text("Task", style = MaterialTheme.typography.titleMedium)
                    Field(task, "Tell the agent what to change", minLines = 5) { task = it }
                    Button(
                        onClick = {
                            running = true
                            logs.clear()
                            scope.launch {
                                try {
                                    val githubApi = GitHubApi(ghToken)
                                    val aiClient = AiClient(baseUrl, aiKey, model)
                                    val config = AgentConfig(
                                        baseUrl = baseUrl,
                                        model = model,
                                        aiApiKey = aiKey,
                                        githubToken = ghToken,
                                        owner = owner,
                                        repo = repo,
                                        baseBranch = branch
                                    )
                                    val agent = AgentEngine(githubApi, aiClient, config) { logLine -> logs.add(logLine) }
                                    agent.run(task)
                                } catch (e: Exception) {
                                    logs.add(LogLine(e.message ?: "Unknown error", LogLine.Kind.ERROR))
                                } finally { running = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running && task.isNotBlank() && aiKey.isNotBlank() && ghToken.isNotBlank() && owner.isNotBlank()
                    ) { Text(if (running) "Agent running…" else "Run agent") }
                }

                item { Text("Activity", style = MaterialTheme.typography.titleMedium) }
                items(logs) { log -> Text("• ${log.text}", style = MaterialTheme.typography.bodySmall) }
                item {
                    Text(
                        "Safety: changes are made on a new working branch and the agent creates a draft PR. The base branch is not written directly.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, label: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, minLines = minLines, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Secret(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
}
