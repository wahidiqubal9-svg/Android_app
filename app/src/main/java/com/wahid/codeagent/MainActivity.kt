package com.wahid.codeagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

        setContent {
            CodeAgentApp(SecureStore(this))
        }
    }
}

@Composable
fun CodeAgentApp(store: SecureStore) {

    val scope = rememberCoroutineScope()

    var baseUrl by remember {
        mutableStateOf(
            store.get("baseUrl")
                ?: "https://api.deepseek.com/v1"
        )
    }

    var model by remember {
        mutableStateOf(
            store.get("model")
                ?: "deepseek-v4-flash"
        )
    }

    var aiKey by remember {
        mutableStateOf(
            store.get("aiKey") ?: ""
        )
    }

    var ghToken by remember {
        mutableStateOf(
            store.get("ghToken") ?: ""
        )
    }

    var owner by remember {
        mutableStateOf(
            store.get("owner")
                ?: "wahidiqubal9-svg"
        )
    }

    var repo by remember {
        mutableStateOf(
            store.get("repo")
                ?: "Medicalcoupons.in"
        )
    }

    var branch by remember {
        mutableStateOf(
            store.get("branch")
                ?: "main"
        )
    }

    var task by remember {
        mutableStateOf("")
    }

    var running by remember {
        mutableStateOf(false)
    }

    val logs = remember {
        mutableStateListOf<LogLine>()
    }

    MaterialTheme {

        Scaffold { paddingValues ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ---------------------------------------------------------
                // AI SETTINGS
                // ---------------------------------------------------------

                item {

                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Field(
                        value = baseUrl,
                        label = "OpenAI-compatible base URL",
                        onChange = {
                            baseUrl = it
                        }
                    )

                    Field(
                        value = model,
                        label = "Model",
                        onChange = {
                            model = it
                        }
                    )

                    Secret(
                        value = aiKey,
                        label = "AI API key",
                        onChange = {
                            aiKey = it
                        }
                    )
                }

                // ---------------------------------------------------------
                // GITHUB SETTINGS
                // ---------------------------------------------------------

                item {

                    Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Secret(
                        value = ghToken,
                        label = "GitHub fine-grained token",
                        onChange = {
                            ghToken = it
                        }
                    )

                    Field(
                        value = owner,
                        label = "Owner",
                        onChange = {
                            owner = it
                        }
                    )

                    Field(
                        value = repo,
                        label = "Repository",
                        onChange = {
                            repo = it
                        }
                    )

                    Field(
                        value = branch,
                        label = "Base branch",
                        onChange = {
                            branch = it
                        }
                    )
                }

                // ---------------------------------------------------------
                // SAVE SETTINGS
                // ---------------------------------------------------------

                item {

                    Button(
                        onClick = {

                            store.put(
                                "baseUrl",
                                baseUrl
                            )

                            store.put(
                                "model",
                                model
                            )

                            store.put(
                                "aiKey",
                                aiKey
                            )

                            store.put(
                                "ghToken",
                                ghToken
                            )

                            store.put(
                                "owner",
                                owner
                            )

                            store.put(
                                "repo",
                                repo
                            )

                            store.put(
                                "branch",
                                branch
                            )

                            logs.add(
                                LogLine(
                                    "Settings saved securely.",
                                    LogLine.Kind.SUCCESS
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text("Save settings")
                    }
                }

                // ---------------------------------------------------------
                // TASK
                // ---------------------------------------------------------

                item {

                    Text(
                        text = "Task",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Field(
                        value = task,
                        label = "Tell the agent what to change",
                        minLines = 5,
                        onChange = {
                            task = it
                        }
                    )

                    Button(
                        onClick = {

                            running = true
                            logs.clear()

                            scope.launch {

                                try {

                                    val githubApi = GitHubApi(
                                        ghToken
                                    )

                                    val aiClient = AiClient(
                                        baseUrl,
                                        aiKey,
                                        model
                                    )

                                    val config = AgentConfig(
                                        baseUrl = baseUrl,
                                        model = model,
                                        aiApiKey = aiKey,
                                        githubToken = ghToken,
                                        owner = owner,
                                        repo = repo,
                                        baseBranch = branch
                                    )

                                    val agent = AgentEngine(
                                        githubApi,
                                        aiClient,
                                        config
                                    ) { logLine ->

                                        logs.add(logLine)
                                    }

                                    agent.run(task)

                                } catch (e: Exception) {

                                    logs.add(
                                        LogLine(
                                            e.message
                                                ?: "Unknown error",
                                            LogLine.Kind.ERROR
                                        )
                                    )

                                } finally {

                                    running = false
                                }
                            }

                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running &&
                                task.isNotBlank() &&
                                aiKey.isNotBlank() &&
                                ghToken.isNotBlank()
                    ) {

                        Text(
                            if (running) {
                                "Agent running…"
                            } else {
                                "Run agent"
                            }
                        )
                    }
                }

                // ---------------------------------------------------------
                // ACTIVITY LOG
                // ---------------------------------------------------------

                item {

                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(logs) { log ->

                    Text(
                        text = "• ${log.text}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ---------------------------------------------------------
                // SAFETY INFORMATION
                // ---------------------------------------------------------

                item {

                    Text(
                        text = "Safety: changes are made on a new working " +
                                "branch and the agent creates a draft PR. " +
                                "The base branch is not written directly.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// NORMAL TEXT FIELD
// -------------------------------------------------------------------------

@Composable
private fun Field(
    value: String,
    label: String,
    minLines: Int = 1,
    onChange: (String) -> Unit
) {

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label)
        },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

// -------------------------------------------------------------------------
// PASSWORD / SECRET FIELD
// -------------------------------------------------------------------------

@Composable
private fun Secret(
    value: String,
    label: String,
    onChange: (String) -> Unit
) {

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label)
        },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}