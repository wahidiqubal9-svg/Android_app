package com.wahid.codeagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CodeAgentApp(SecureStore(this)) } }
}

@Composable
fun CodeAgentApp(store: SecureStore) {
    val scope=rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(store.get("baseUrl") ?: "https://api.deepseek.com/v1") }
    var model by remember { mutableStateOf(store.get("model") ?: "deepseek-v4-flash") }
    var aiKey by remember { mutableStateOf(store.get("aiKey") ?: "") }
    var ghToken by remember { mutableStateOf(store.get("ghToken") ?: "") }
    var owner by remember { mutableStateOf(store.get("owner") ?: "wahidiqubal9-svg") }
    var repo by remember { mutableStateOf(store.get("repo") ?: "Medicalcoupons.in") }
    var branch by remember { mutableStateOf(store.get("branch") ?: "main") }
    var task by remember { mutableStateOf("") }; var running by remember { mutableStateOf(false) }
    val logs=remember { mutableStateListOf<LogLine>() }
    MaterialTheme { Scaffold(topBar={ TopAppBar(title={Text("Code Agent Mobile")}) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("AI",style=MaterialTheme.typography.titleMedium); Field(baseUrl,"OpenAI-compatible base URL"){baseUrl=it}; Field(model,"Model"){model=it}; Secret(aiKey,"AI API key"){aiKey=it} }
            item { Text("GitHub",style=MaterialTheme.typography.titleMedium); Secret(ghToken,"GitHub fine-grained token"){ghToken=it}; Field(owner,"Owner"){owner=it}; Field(repo,"Repository"){repo=it}; Field(branch,"Base branch"){branch=it} }
            item { Button(onClick={store.put("baseUrl",baseUrl);store.put("model",model);store.put("aiKey",aiKey);store.put("ghToken",ghToken);store.put("owner",owner);store.put("repo",repo);store.put("branch",branch);logs.add(LogLine("Settings saved securely.",LogLine.Kind.SUCCESS))},Modifier.fillMaxWidth()){Text("Save settings")} }
            item { Text("Task",style=MaterialTheme.typography.titleMedium); Field(task,"Tell the agent what to change",5){task=it}; Button(enabled=!running&&task.isNotBlank()&&aiKey.isNotBlank()&&ghToken.isNotBlank(),onClick={running=true;logs.clear();scope.launch{try{AgentEngine(GitHubApi(ghToken),AiClient(baseUrl,aiKey,model),AgentConfig(baseUrl=baseUrl,model=model,aiApiKey=aiKey,githubToken=ghToken,owner=owner,repo=repo,baseBranch=branch)){logs.add(it)}.run(task)}catch(e:Exception){logs.add(LogLine(e.message?:"Unknown error",LogLine.Kind.ERROR))}finally{running=false}}},Modifier.fillMaxWidth()){Text(if(running)"Agent running…" else "Run agent")} }
            item { Text("Activity",style=MaterialTheme.typography.titleMedium) }; items(logs){Text("• ${it.text}",style=MaterialTheme.typography.bodySmall)}
            item { Text("Safety: changes are made on a new working branch and the agent creates a draft PR. Base branch is not written directly.",style=MaterialTheme.typography.bodySmall) }
        }
    }}
}

@Composable private fun Field(value:String,label:String,minLines:Int=1,onChange:(String)->Unit)=OutlinedTextField(value,onChange,label={Text(label)},minLines=minLines,modifier=Modifier.fillMaxWidth())
@Composable private fun Secret(value:String,label:String,onChange:(String)->Unit)=OutlinedTextField(value,onChange,label={Text(label)},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
