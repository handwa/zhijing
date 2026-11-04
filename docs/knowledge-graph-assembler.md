# 图谱合并加载：assets 出厂图谱 + Room 运行时数据

> 对应代码：`app/src/main/java/com/example/zhijing/kg/{GraphAssembler,GraphModels,MiniJson}.kt`
> 上游：`data/.../Kg*Repository`、`assets/kg/graph.json`
> 定位：分层存储（`docs/knowledge-graph-room.md`）的**最后一段**

## 一、为什么要它

分层存储把「出厂命题」留在 assets、「运行时数据」放进 Room。这带来一个问题：

> UI 要渲染一张卡，得同时知道 assets 的卡片内容、Room 里云端生成的增量命题、
> 以及这条命题当前被判成了什么状态 —— 三处数据，UI 不该自己去拼。

`GraphAssembler` 就是那个拼接点。**UI 只认 `MergedGraph`，不认 assets 也不认 Room。**

```
assets/kg/graph.json  ─┐
                       ├─ GraphAssembler ──► MergedGraph ──► UI
Room 状态 + 增量命题  ─┘
```

## 二、合并规则

| 来源 | 处理 | 结果字段 |
|---|---|---|
| 出厂命题（assets） | 原样保留 | `bundled = true`，不可删除 |
| 已确认的云端命题 | 追加到 `cardId` 对应的卡片 | `bundled = false`，可删除 |
| pending / rejected 候选 | **不进入图谱** | — |
| 状态（Room） | 按命题 ID 派生 | `probe` 字段 |
| 已合并的派生 ID | 跳过，不重复挂 | 避免同义命题出现两次 |

**关键点：未经确认的候选永远不出现。** 这是数据层 `observeConfirmed()` 的约定，
加载器只是遵守它。

## 三、输出的数据模型

`MergedGraph` 一次给全广度与深度：

```kotlin
data class MergedGraph(
    val outline: List<GraphOutlineNode>,   // 大纲树（已挂 cardIds）
    val cards: List<GraphCard>,            // 卡片（已挂 claims + badgeStatus）
    val claims: Map<String, GraphClaim>,   // 命题（已挂 probe）
    val edges: Map<String, GraphEdge>      // 边（带条件）
)
```

三个派生属性直接服务 UI：

| 属性 | 用途 |
|---|---|
| `statusSummary` | 深度侧「状态汇总」：四态计数 |
| `claimsByPriority(cardId)` | 下钻排序：易混淆 > 未探测 > 已验证 > 已掌握 |
| `runtimeClaimCount` | 云端生成的可删命题数 |

`GraphCard` 上的两个便利属性：

- `descendable`：只有挂了命题的叶子卡才有下钻入口（`claims.isNotEmpty()`）
- `misconceptionNotes`：把卡内所有命题的误解注记汇总，就是**广度卡片的「易错点注记」**

## 四、卡片角标

一张卡可能挂 1–5 条命题，角标取「最需要用户注意」的那个：

```
易混淆 > 未探测 > 已验证 > 已掌握
```

这样用户扫一眼大纲就知道：哪张卡里还有易错没解除、哪张卡还没碰过。

## 五、两种加载方式

```kotlin
val assembler = GraphAssembler(context, data)

// 一次性加载
val graph = assembler.load(includeRuntimeClaims = true)

// 持续观察：用户判断后自动重算，广度侧角标随之更新
assembler.observe(includeRuntimeClaims = true).collect { graph ->
    renderOutline(graph.outline)
    renderCards(graph.cards)
}
```

**`includeRuntimeClaims = false` 就是离线模式**：此时不查询增量命题表，
结果与只读 assets 完全一致。这就是后续「模式切换」要接的那个开关点。

## 六、MiniJson：为什么手写 JSON 解析

| 方案 | 为什么不选 |
|---|---|
| `org.json` | Android 平台类，纯 JVM 单测里是空桩，无法覆盖 |
| kotlinx-serialization / Gson | 项目刻意零额外依赖；为一份结构固定的 130KB 文件不划算 |

`MiniJson` 支持完整 JSON 语法（对象、数组、字符串转义含 `\uXXXX`、数字、布尔、null），
错误时抛 `GraphFormatException` 并**带上位置与附近文本** —— 排查 130KB 文件里的格式错误必须靠这个。

取值辅助（`asMap()` / `str()` / `list()` 等）缺失字段一律返回默认值，不抛异常：
出厂图谱是构建产物，字段齐全由 `validate_graph.py` 在构建阶段保证；
读侧不该因为一个可选字段缺失就整份白屏。

## 七、测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhijing.kg.*"
```

`GraphAssemblerTest` 用的是**真实的 `assets/kg/graph.json`**，不是手搓的假样本 ——
假样本会让「字段名写错」这类问题漏过去。断言包括：

- 24 大纲 / 22 卡片 / 35 命题 / 16 边全部解析成功
- 状态派生到命题与卡片角标
- 未确认候选不进入图谱
- 已确认云端命题挂到对应卡片、标记为可删
- 离线模式与只读 assets 完全一致
- 已合并的派生命题不重复出现
- 下钻排序易混淆优先

Robolectric 单测默认不挂载 `app/src/main/assets`，因此加载器留了 `seedBundled()`
注入口；断言的是同一个 `ParsedGraph.fromJson` 路径，覆盖度不打折。

全仓 `:app` + `:data` 共 **182 个用例全部通过**。

## 八、尚未做的部分

| 项 | 状态 |
|---|---|
| **模式切换开关** | 未做。`includeRuntimeClaims` 是开关点，但设置页还没有 UI |
| **图谱页面接入** | 未做。现有 UI 仍读静态图，需改为消费 `MergedGraph` |
| **候选确认 UI** | 未做。云端生成后需要界面展示 pending 候选与「确认/否决」 |
| **近义去重调度** | 未做。`kg_claim_merge` 的存储与读取已就绪，缺「取向量→比对→recordMerge」的编排 |
| **`evidence` 逐字校验** | 未做。契约要求命中原文，命不中丢弃 |
