package com.example.zhijing

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.zhijing.chat.ContextBudget
import com.example.zhijing.chat.ConversationManager
import com.example.zhijing.model.ModelRegistry
import com.example.zhijing.model.ModelSpec
import com.example.zhijing.ui.theme.ZhiJingTheme
import com.zhijing.data.ZhiJingData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val messages = remember {
        mutableStateListOf(ChatMessage("你好，我是知境。\n现在可以和我聊点什么？", false))
    }
    var input by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }

    // 正在流式生成中的文本；null 表示当前没有生成任务
    var streamingText by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }
    // 模型规格在进入界面时确定，换模型只改 ModelRegistry，不碰这里的逻辑
    val spec = remember { ModelRegistry.forDevice(deviceRamGb(context)) }
    val listState = rememberLazyListState()

    // 数据层入口；会话管理在模型加载完成后创建（需要实际上下文窗口）
    val data = remember { ZhiJingData.get(context.applicationContext) }
    var manager by remember { mutableStateOf<ConversationManager?>(null) }

    LaunchedEffect(Unit) {
        try {
            val model = withContext(Dispatchers.IO) { copyModelToPrivateStorage(context, spec) }
            // Native llama.cpp 初始化在 engine 创建时异步进行，先等它就绪再加载模型。
            engine.state.first { state ->
                state is InferenceEngine.State.Initialized ||
                    state is InferenceEngine.State.Error
            }.let { state ->
                if (state is InferenceEngine.State.Error) throw state.exception
            }
            engine.loadModel(model.absolutePath, spec.contextSize)

            // system prompt 由 ConversationManager.open() 内的 rebuildContext 统一灌入。
            // 这里不再调 setSystemPrompt，否则会多一次随后被清空的 prefill。
            // 模型就绪后才能拿到真实上下文窗口，此时再建预算和会话管理
            val budget = ContextBudget.from(
                window = engine.contextWindow(),
                predictLength = spec.predictLength,
                systemPrompt = spec.systemPrompt,
            )
            val created = ConversationManager(
                conversations = data.conversations,
                engine = engine,
                budget = budget,
                predictLength = spec.predictLength,
            )
            created.open(null, spec.systemPrompt)
            manager = created
            modelReady = true
        } catch (e: Exception) {
            modelError = e.message ?: e.javaClass.simpleName
        }
    }

    // 新消息或新 token 到达时，把列表滚到底部
    LaunchedEffect(streamingText, messages.size) {
        val totalCount = messages.size + if (streamingText != null) 1 else 0
        if (totalCount > 0) {
            listState.scrollToItem(totalCount - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("知境", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (modelReady) "${spec.displayName} 本地对话" else "正在加载模型…",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                // 流式气泡不属于 messages，作为最后一项单独渲染
                streamingText?.let { current ->
                    item {
                        MessageBubble(ChatMessage(current, false))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp)
                )
                Button(
                    onClick = {
                        val text = input.trim()
                        val activeManager = manager
                        if (text.isEmpty() || !modelReady || isGenerating || activeManager == null) {
                            return@Button
                        }

                        messages.add(ChatMessage(text, true))
                        input = ""
                        streamingText = ""
                        isGenerating = true

                        scope.launch {
                            val buffer = StringBuilder()
                            try {
                                activeManager.send(text).collect { token ->
                                    buffer.append(token)
                                    streamingText = buffer.toString()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                buffer.append("\n[错误] ").append(e.message ?: e.javaClass.simpleName)
                            } finally {
                                val finalText = buffer.toString()
                                if (finalText.isNotEmpty()) {
                                    messages.add(ChatMessage(finalText, false))
                                }
                                streamingText = null
                                isGenerating = false
                            }
                        }
                    },
                    enabled = input.isNotBlank() && modelReady && !isGenerating,
                    modifier = Modifier.height(56.dp),
                    shape = CircleShape
                ) {
                    Text(if (isGenerating) "生成中" else "发送")
                }
            }

            modelError?.let { error ->
                Text(
                    text = "模型加载失败：$error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private suspend fun copyModelToPrivateStorage(context: Context, spec: ModelSpec): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, spec.fileName)
        // 精确大小 + 只读时间戳足够判断是否已是完整拷贝；比“大于某个魔数”可靠
        if (target.exists() && target.length() == spec.sizeBytes) return@withContext target

        val partial = File(dir, "${spec.fileName}.part")
        partial.delete()
        context.assets.open(spec.assetPath).use { input ->
            FileOutputStream(partial).use { output -> input.copyTo(output) }
        }
        require(partial.length() == spec.sizeBytes) {
            "Model copy incomplete: ${partial.length()} / ${spec.sizeBytes} bytes"
        }
        require(partial.renameTo(target)) { "Could not finalize model file" }
        target
    }

/** 设备物理内存（GB，向下取整），用于 ModelRegistry 选型。 */
private fun deviceRamGb(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val info = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return (info.totalMem / (1024L * 1024L * 1024L)).toInt()
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (message.fromUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ZhiJingTheme {
        ChatScreen()
    }
}
