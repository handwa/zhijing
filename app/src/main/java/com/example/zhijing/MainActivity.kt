package com.example.zhijing

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.arm.aichat.cancelGenerationAndAwaitReady
import com.example.zhijing.chat.ContextBudget
import com.example.zhijing.chat.ConversationManager
import com.example.zhijing.chat.DocumentParsers
import com.example.zhijing.chat.RagIndexer
import com.example.zhijing.chat.RetrievalConfig
import com.zhijing.data.repository.ConversationEntitySnapshot
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

    // 多会话
    var conversations by remember { mutableStateOf<List<ConversationEntitySnapshot>>(emptyList()) }
    var showConversations by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }   // 切换/新建会话中，屏蔽其他操作
    var busyName by remember { mutableStateOf<String?>(null) }  // 副标题显示的当前会话名
    // 侧边栏
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showSettings by remember { mutableStateOf(false) }

    // 文档导入：进度非 null 表示正在导入
    var importProgress by remember { mutableStateOf<ImportProgress?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    /**
     * 把系统文件选择器返回的 uri 拷贝到缓存目录再解析。
     *
     * 直接用 uri 读也可以，但文档可能来自云盘或需要临时授权，
     * 先落地成本地文件更稳妥。
     */
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importMessage = null
                importProgress = ImportProgress("正在读取文件…", 0, 0)
                busy = true
                try {
                    val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                    importProgress = ImportProgress("正在解析并建立索引…", 0, 0)
                    val indexer = RagIndexer(
                        engine = engine,
                        contents = data.contents,
                        embeddingModelId = ModelRegistry.embedding().id,
                    )
                    val contentId = indexer.indexDocument(file) { done, total ->
                        importProgress = ImportProgress("正在建立索引…", done, total)
                    }
                    withContext(Dispatchers.IO) { file.delete() }
                    importMessage = "已导入并建立索引（内容 id=$contentId）"
                    android.util.Log.i("Zhijing", "document imported, content=$contentId")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    importMessage = "导入失败：${e.message ?: e.javaClass.simpleName}"
                    android.util.Log.e("Zhijing", "document import failed", e)
                } finally {
                    importProgress = null
                    busy = false
                }
            }
        }
    }

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
                // 本地收藏检索；嵌入模型加载失败时 buildAugmentedPrompt 会自动降级
                contents = data.contents,
                retrieval = RetrievalConfig.DEFAULT,
            )
            // 有历史会话就恢复最近那个，否则新建
            val existing = created.observeConversations().first()
            created.open(existing.firstOrNull()?.id, spec.systemPrompt)
            manager = created
            rebuildMessageList(created, messages)

            // 嵌入模型（RAG）。失败不影响对话，只影响检索。
            try {
                val embSpec = ModelRegistry.embedding()
                val embFile = withContext(Dispatchers.IO) {
                    copyAssetToPrivateStorage(context, embSpec.assetPath, embSpec.fileName, embSpec.sizeBytes)
                }
                engine.loadEmbeddingModel(embFile.absolutePath)
                android.util.Log.i("Zhijing", "embedding ready, dim=${engine.embeddingDim()}")
            } catch (e: Exception) {
                android.util.Log.e("Zhijing", "embedding model unavailable, RAG disabled", e)
            }

            modelReady = true
        } catch (e: Exception) {
            modelError = e.message ?: e.javaClass.simpleName
        }
    }

    // 监听会话列表变化
    LaunchedEffect(manager) {
        val m = manager ?: return@LaunchedEffect
        m.observeConversations().collect { conversations = it }
    }

    // 新消息或新 token 到达时，把列表滚到底部
    LaunchedEffect(streamingText, messages.size) {
        val totalCount = messages.size + if (streamingText != null) 1 else 0
        if (totalCount > 0) {
            listState.scrollToItem(totalCount - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                conversations = conversations,
                currentId = manager?.currentId() ?: 0,
                enabled = modelReady && !busy && !isGenerating,
                onNewChat = {
                    scope.launch {
                        drawerState.close()
                        val m = manager ?: return@launch
                        busy = true
                        try {
                            engine.cancelGenerationAndAwaitReady()
                            m.newConversation()
                            rebuildMessageList(m, messages)
                        } finally {
                            busy = false
                        }
                    }
                },
                onSelect = { conv ->
                    scope.launch {
                        drawerState.close()
                        val m = manager ?: return@launch
                        busy = true
                        try {
                            engine.cancelGenerationAndAwaitReady()
                            m.open(conv.id, spec.systemPrompt)
                            rebuildMessageList(m, messages)
                        } catch (e: Exception) {
                            android.util.Log.e("Zhijing", "switch failed", e)
                        } finally {
                            busy = false
                        }
                    }
                },
                onImport = {
                    scope.launch {
                        drawerState.close()
                    }
                    importLauncher.launch(DocumentParsers.supportedMimeTypes)
                },
                onSettings = {
                    scope.launch {
                        drawerState.close()
                        showSettings = true
                    }
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                busyName ?: "知境",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (modelReady) {
                                    "${spec.displayName} · ${conversations.size} 个会话"
                                } else "正在加载模型…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "会话列表")
                        }
                    },
                    actions = {
                        // 新建对话
                        IconButton(
                            onClick = {
                                val m = manager ?: return@IconButton
                                scope.launch {
                                    busy = true
                                    try {
                                        engine.cancelGenerationAndAwaitReady()
                                        m.newConversation()
                                        rebuildMessageList(m, messages)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = modelReady && !busy && !isGenerating,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新建对话")
                        }
                    },
                )
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
                    enabled = input.isNotBlank() && modelReady && !isGenerating && !busy,
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

    // 导入进度
    importProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { /* 导入中不可取消，避免留下半截数据 */ },
            title = { Text("导入文档") },
            text = {
                Column {
                    Text(progress.label, style = MaterialTheme.typography.bodyMedium)
                    if (progress.total > 0) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${progress.done} / ${progress.total} 段",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }

    // 导入结果提示
    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            title = { Text(if (message.startsWith("导入失败")) "导入失败" else "导入完成") },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { importMessage = null }) { Text("好") }
            },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("设置") },
            text = {
                Column {
                    Text("模型：${spec.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("上下文：${spec.contextSize} tokens", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("嵌入：${ModelRegistry.embedding().id}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支持导入：" + DocumentParsers.supportedExtensions.sorted().joinToString(" / "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "更多设置项将在后续版本加入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * DeepSeek 风格侧边栏：顶部新建入口，中间会话列表，底部功能设置。
 */
@Composable
private fun ChatDrawer(
    conversations: List<ConversationEntitySnapshot>,
    currentId: Long,
    enabled: Boolean,
    onNewChat: () -> Unit,
    onSelect: (ConversationEntitySnapshot) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(20.dp))
        Text(
            "知境",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))

        // 新建对话
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onNewChat)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text("新建对话", style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 会话列表（可滚动，底部固定在设置之上）
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            items(conversations, key = { it.id }) { conv ->
                val selected = conv.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onSelect(conv) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (selected) Icons.Filled.Email else Icons.Outlined.Email,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = conv.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (conversations.isEmpty()) {
                item {
                    Text(
                        "还没有会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }

        HorizontalDivider()
        // 底部功能区
        DrawerBottomItem(Icons.Default.AddCircle, "导入文档", onImport, enabled = enabled)
        DrawerBottomItem(Icons.Default.Settings, "设置", onSettings)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerBottomItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}

/** 用管理器的当前会话内容重填 UI 消息列表（不含 system）。 */
private fun rebuildMessageList(
    manager: ConversationManager,
    messages: MutableList<ChatMessage>,
) {
    messages.clear()
    manager.currentMessages().forEach { turn ->
        messages.add(ChatMessage(turn.content, turn.role == com.example.zhijing.chat.Role.USER))
    }
    if (messages.isEmpty()) {
        messages.add(ChatMessage("你好，我是知境。\n现在可以和我聊点什么？", false))
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

/** 文档导入的进度状态。total 为 0 表示尚未进入分段阶段。 */
private data class ImportProgress(val label: String, val done: Int, val total: Int)

/**
 * 把文件选择器返回的 uri 拷贝到缓存目录。
 *
 * 返回的文件名保留原扩展名，解析器靠它判断格式。
 */
private fun copyUriToCache(context: Context, uri: android.net.Uri): File {
    val name = queryDisplayName(context, uri) ?: "import.doc"
    // 用时间戳避免同名覆盖
    val target = File(context.cacheDir, "${System.currentTimeMillis()}_$name")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法打开所选文件" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

/** 从 uri 里查出显示名（含扩展名）。 */
private fun queryDisplayName(context: Context, uri: android.net.Uri): String? {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

/** 向量已 L2 归一化，点积即余弦相似度。 */
@Suppress("unused")
private fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    val n = minOf(a.size, b.size)
    for (i in 0 until n) s += a[i] * b[i]
    return s
}
/**
 * 把 assets 里的一个文件复制到私有目录，已存在且大小一致时直接复用。
 *
 * 精确大小比对足够判断是否已是完整拷贝，比“大于某个魔数”可靠。
 */
private suspend fun copyAssetToPrivateStorage(
    context: Context,
    assetPath: String,
    fileName: String,
    expectedBytes: Long,
): File = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val target = File(dir, fileName)
    if (target.exists() && target.length() == expectedBytes) return@withContext target

    val partial = File(dir, "$fileName.part")
    partial.delete()
    context.assets.open(assetPath).use { input ->
        FileOutputStream(partial).use { output -> input.copyTo(output) }
    }
    require(partial.length() == expectedBytes) {
        "Asset copy incomplete: ${partial.length()} / $expectedBytes bytes ($fileName)"
    }
    require(partial.renameTo(target)) { "Could not finalize $fileName" }
    target
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
