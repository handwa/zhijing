# 记忆策略：端侧与云端分流

> 对应代码：`app/src/main/java/com/example/zhijing/chat/{MemoryStrategy,ConversationManager}.kt`
> 前置：`docs/knowledge-graph-*` 三篇是图谱侧；本文是**对话侧**的记忆。

## 一、问题

`ConversationManager` 的编排逻辑（选路、上下文重建、RAG、落库）端云共用一份，这是当初抽出
`ChatEngine` 接口的目的 —— 不该分叉。

但**记忆的约束两端差异很大**：

| | 端侧 | 云端 |
|---|---|---|
| 上下文窗口 | 1–4K token，很紧 | 8K 以上，宽松 |
| 压缩时机 | 阈值低，频繁压缩 | 阈值高，少压缩 |
| 摘要成本 | 本地推理慢，占显存 | 按 token 计费，质量高 |
| 提要长度 | 必须很短 | 可以到几千字 |

所以：**分的是策略，不是复制 300 行编排代码。**

## 二、结构

```
ConversationManager（不变：选路 / 重建 / RAG / 落库）
        │
        └─ MemoryStrategy  ← 新增
              ├─ LocalMemoryStrategy   端侧：紧预算 + 小模型摘要
              └─ CloudMemoryStrategy   云端：宽预算 + 结构化摘要
```

`ConversationManager.compressIfNeeded(engine)` 按引擎选策略：

```kotlin
private fun memoryFor(engine: ChatEngine): MemoryStrategy =
    if (engine === cloud) cloudMemory else localMemory
```

## 三、两种策略的差异

| 项 | 端侧 | 云端 |
|---|---|---|
| `name` | `local` | `cloud` |
| `historyBudget` | 默认取构造时的 `budget` | 同左（由调用方给更大值） |
| `digestMaxChars` | **400** | **2000** |
| 摘要 prompt | 极简：「不超过 80 字，只留目标/结论/未决」 | 结构化：「分四类：目标偏好、事实结论、待办、未决」 |
| 每轮输入上限 | **200 字**（控制端侧推理耗时） | 不限 |
| 输出上限 | 96 token | 320 token |

端侧提要 400 字 ≈ 600 token（按 `estimateTokens` 1.5 token/字），
在 2K 窗口下是能接受的上限，再大就挤占历史空间了。

## 四、降级：摘要可以降，记忆不能丢

这是 `MemoryStrategy.summarize` 返回 `String?` 而不是抛异常的原因：

```kotlin
val facts = summarizeFor(memory, dropped, engine)        // 模型摘要，失败返回 null
    ?: compressor.extractFacts(dropped).takeIf { it.isNotEmpty() }  // 降级：规则抽取
```

端侧小模型可能超时、可能吐空；云端可能网络失败。**用户正在等回答，不该因为摘要失败就整轮失败。**

## 五、修掉的一个真问题：digest 无限增长

旧实现：

```kotlin
digest = if (digest.isEmpty()) facts else "$digest；$facts"   // 只增不减
```

压缩很多次后 digest 会重新撑爆窗口 —— 压缩本身反而制造了超限。

新实现每次并入后都过一遍上限：

```kotlin
digest = compactIfNeeded(memory, merged, engine)
// 超限 → compactDigest 调模型压缩；模型也失败 → 截断保留末尾（新信息更可能有用）
```

另外补了 `compactOversizedDigest`：**从云端切回端侧时**，云端生成的 2000 字提要
会超出端侧 400 字上限，此时主动压一次。

## 六、提要与策略绑定

`digestBy` 记录当前提要由哪条路径生成（`local` / `cloud` / `unknown`）。

为什么需要：两种策略的粒度不同，混用会让提要被反复重写。从数据库恢复的提要
无法区分来源，标为 `unknown`，由后续压缩按当前策略校正。

> **局限**：目前只记录了来源，**还没有做「切换策略时主动重压」**。
> `compactOversizedDigest` 只在长度超限时触发，不检查 `digestBy` 是否与当前策略一致。
> 如果云端生成的短提要被端侧使用，不会重新压缩（但这本身无害，短提要不会撑爆窗口）。

## 七、测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhijing.chat.*"
```

`MemoryStrategyTest` 10 个用例：

- 两种策略的 `digestMaxChars` 不同（400 vs 2000）
- 摘要成功返回模型输出
- **摘要失败返回 null 而不抛异常**
- 摘要为空串也视为失败
- 空轮次不调用模型
- 云端 prompt 含四类结构化要求
- 端侧限制每轮输入长度
- 未超限时 compact 不调模型
- 超限时 compact 调用模型
- `incoming` 为空时不凭空多出分隔符

全仓 `:app` + `:data` 共 **192 个用例通过**。

## 八、尚未做

| 项 | 状态 |
|---|---|
| **摘要改造成模型调用** | ✅ 已做（`summarize` 走模型，失败降级规则抽取） |
| **digest 长度上限** | ✅ 已做 |
| **跨会话用户记忆** | ❌ 未做。当前只有「会话级记忆」，没有「用户级记忆」（如「用户是后端工程师」） |
| **记忆唤醒** | ❌ 只有 `wake_plan_table` 表，未接 |
| **提示词可配置** | ❌ 摘要 prompt 硬编码在策略里，未开放给用户调 |
