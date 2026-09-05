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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var deviceCode by remember { mutableStateOf<GitHubOAuth.DeviceCode?>(null) }
    var loginError by remember { mutableStateOf("") }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    val logs = remember { mutableStateListOf<LogLine>() }

    fun beginGitHubPolling(device: GitHubOAuth.DeviceCode) {
        pollJob?.cancel()
        loggingIn = true
        loginError = ""
        logs.add(LogLine("Waiting for GitHub authorization…"))
        pollJob = scope.launch {
            try {
                val token = GitHubOAuth(context).waitForToken(ghClientId, device)
                logs.add(LogLine("GitHub authorization received. Checking account…"))
                val login = withContext(Dispatchers.IO) {
                    GitHubApi(token).authenticatedLogin()
                }
                store.put("ghToken", token)
                store.put("owner", login)
                ghToken = token
                owner = login
                githubUser = login
                logs.add(LogLine("Signed in to GitHub as $login.", LogLine.Kind.SUCCESS))
                deviceCode = null
            } catch (e: Exception) {
                val message = e.message ?: "GitHub login failed"
                loginError = message
                logs.add(LogLine(message, LogLine.Kind.ERROR))
            } finally {
                loggingIn = false
                pollJob = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { pollJob?.cancel() }
    }

    if (deviceCode != null) {
        AlertDialog(
            onDismissRequest = { if (!loggingIn) deviceCode = null },
            title = { Text("GitHub verification code") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter this code on GitHub:")
                    Text(deviceCode!!.userCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("GitHub page: ${deviceCode!!.verificationUri}")
                    Text("The code expires after 15 minutes.")
                    if (loggingIn) {
                        Text("Waiting for GitHub to confirm the login…", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (loginError.isNotBlank()) {
                        Text("Login error: $loginError", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !loggingIn,
                    onClick = {
                        val device = deviceCode ?: return@Button
                        beginGitHubPolling(device)
                        GitHubOAuth(context).openVerificationPage(device.verificationUri)
                    }
                ) { Text(if (loggingIn) "Waiting…" else "Open GitHub") }
            },
            dismissButton = {
                TextButton(enabled = !loggingIn, onClick = { deviceCode = null }) { Text("Cancel") }
            }
        )
    }

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
                            loginError = ""
                            logs.add(LogLine("Requesting GitHub verification code…"))
                            scope.launch {
                                try {
                                    val oauth = GitHubOAuth(context)
                                    val device = oauth.start(ghClientId)
                                    deviceCode = device
                                    loggingIn = false
                                } catch (e: Exception) {
                                    loginError = e.message ?: "GitHub login failed"
                                    logs.add(LogLine(loginError, LogLine.Kind.ERROR))
                                    loggingIn = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loggingIn && deviceCode == null && ghClientId.isNotBlank()
                    ) { Text(if (loggingIn) "Requesting code…" else "Sign in with GitHub") }

                    if (githubUser.isNotBlank()) {
                        Text("✓ GitHub connected as $githubUser", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (loggingIn && deviceCode != null) {
                        Text("Waiting for GitHub authorization…", style = MaterialTheme.typography.bodySmall)
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
