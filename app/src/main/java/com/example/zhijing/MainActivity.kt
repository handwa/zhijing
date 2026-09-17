package com.example.zhijing

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhijing.ui.theme.ZhiJingTheme
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZhiJingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val messages = remember { mutableStateListOf(ChatMessage("你好，我是知境。\n现在可以和我聊点什么？", false)) }
    var input by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }

    LaunchedEffect(Unit) {
        try {
            val model = withContext(Dispatchers.IO) { copyModelToPrivateStorage(context) }
            // Native llama.cpp initialization runs asynchronously when the engine is created.
            // Wait for it before attempting to load the GGUF file.
            engine.state.first { state ->
                state is InferenceEngine.State.Initialized ||
                    state is InferenceEngine.State.Error
            }.let { state ->
                if (state is InferenceEngine.State.Error) throw state.exception
            }
            engine.loadModel(model.absolutePath)
            modelReady = true
        } catch (e: Exception) {
            modelError = e.message ?: e.javaClass.simpleName
        }
    }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Column {
            Text("知境", fontWeight = FontWeight.Bold)
            Text(if (modelReady) "GLM-Edge 本地对话" else "正在加载模型…", style = MaterialTheme.typography.labelSmall)
        } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(messages) { MessageBubble(it) }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("输入消息…") }, maxLines = 4, shape = RoundedCornerShape(22.dp))
                Button(onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(text, true))
                        val assistantIndex = messages.size
                        messages.add(ChatMessage("正在思考…", false))
                        input = ""
                        scope.launch(Dispatchers.Default) {
                            try {
                                val answer = StringBuilder()
                                engine.sendUserPrompt(text).collect { token -> answer.append(token) }
                                withContext(Dispatchers.Main) {
                                    messages[assistantIndex] = ChatMessage(answer.toString(), false)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    messages[assistantIndex] = ChatMessage("模型调用失败：${e.message}", false)
                                }
                            }
                        }
                    }
                }, enabled = input.isNotBlank() && modelReady, modifier = Modifier.height(56.dp), shape = CircleShape) { Text("发送") }
            }
            modelError?.let { Text("模型加载失败：$it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
    }
}

private suspend fun copyModelToPrivateStorage(context: Context): File = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val target = File(dir, "ggml-model-Q4_K_M.gguf")
    if (!target.exists() || target.length() < 900_000_000L) {
        val partial = File(dir, "${target.name}.part")
        partial.delete()
        context.assets.open("model/ggml-model-Q4_K_M.gguf").use { input ->
            FileOutputStream(partial).use { output -> input.copyTo(output) }
        }
        require(partial.length() >= 900_000_000L) { "Model copy incomplete: ${partial.length()} bytes" }
        require(partial.renameTo(target)) { "Could not finalize model file" }
    }
    target
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Card(modifier = Modifier.fillMaxWidth(0.86f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) {
            Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() { ZhiJingTheme { ChatScreen() }
}
