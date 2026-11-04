# 云端生成图谱：prompt → API → 解析 → 入库

> 对应代码：`app/src/main/java/com/example/zhijing/kg/`
> 上游依赖：`cloud/RemoteChatClient`（已有）、`data/.../Kg*Repository`（已有）
> 提示词契约：`kg/prompts/extract_claims.md`、`kg/prompts/output_contract.md`

## 一、这条链路补上了什么

之前的状态：云端 API 已就绪、prompt 已写好，但**中间没有代码**。

```
云端 API ──返回──► ??? ──► 图谱
                  ↑
            缺的就是这一段
```

现在补齐为四段：

```
原文
 │
 ├─ CloudClaimExtractor      组装 prompt → 调 RemoteChatClient → 拿回原始文本
 │
 ├─ ClaimOutputParser        容错解析：JSON → ExtractedClaim
 │
 ├─ ClaimIngestor            算 ID、精确去重 → RuntimeClaim（pending）
 │
 └─ KgClaimRepository → Room kg_runtime_claim    用户确认后才进广度视图
```

## 二、四个组件

### 2.1 `ClaimIds` —— 命题 ID 规则

**这是整条链路里最不能错的一环。** 端上算出的哈希必须与 `kg/src/kg_model.py` 逐位一致，
否则同一条命题在「出厂图谱」和「云端生成」两个来源里会有两个 ID，判断状态立刻分裂。

```
hash = sha1(normalize(statement))[:4]
normalize = 去首尾空白 → 去所有空白 → 去标点 → 转小写
```

去标点的字符集与 Python 侧 `_PUNCT` 逐字相同。`ClaimIdsTest` 里用 Python 实测值做交叉验证：

| statement | 期望哈希 |
|---|---|
| `Linear 层的参数量与 batch 大小无关。` | `2fe7` |
| `ReLU 是逐元素操作，不改变输入张量的形状。` | `6092` |
| `Softmax 的输出沿类别维度求和为 1。` | `a505` |
| `ReLU 之后可以直接接 Softmax 输出类别概率。` | `d07e` |

这些期望值来自 `python kg/src/kg_model.py` 的实测输出，不是照 Kotlin 实现反推的。

### 2.2 `ClaimOutputParser` —— 容错解析

实现 `output_contract.md` 第 5 节的容错链：

| 步骤 | 行为 |
|---|---|
| 剥围栏 | ```json … ``` → 取围栏内部 |
| 定位 JSON | 取第一个 `{` 到最后一个 `}`，容忍前后夹带解释文字 |
| 定位数组 | 找 `"claims"` 键 → 栈式括号匹配取数组体 |
| 切分条目 | 切顶层对象；被截断的尾巴整条丢弃，前面完整的保留 |
| 补全 | 缺 `expected` → `false` + 警告；缺 `conditions` → 「未标注条件」+ 警告；缺 `statement` → 整条丢弃 |

**边与错点的严格规则**（不静默补全，宁可丢弃）：

- `errorKinds.kind` 不在五类内 → 丢弃该项
- 边 `type` 不在六类内 → 丢弃该边
- 边缺 `premise` 或 `boundary` → 丢弃该边（契约：写不出边界的边不要）

### 2.3 `CloudClaimExtractor` —— 调用云端

- system prompt 与 `extract_claims.md` 第 2 节逐字一致（含三问、五类条件、六类边）
- user prompt 按模板组装主题、已知命题 ID、原文
- 单次 `maxTokens = 512`，单段输入建议 ≤900 字（与 prompt 文档一致）
- 调用前先校验 `provider.readyFailureReason()`，配置不全直接抛错而不发请求

### 2.4 `ClaimIngestor` —— 入库

| 检查 | 不通过时 |
|---|---|
| `statement` 非空且 `conditions` 非空 | 拒绝，记入 `rejected` |
| 生成的 ID 符合 `clm.*.<hash4>` | 拒绝 |
| 规范化文本与已有命题重复 | 跳过，记入 `skippedDuplicate` |
| 该 ID 已有合并映射 | 跳过（说明已归到别的命题） |
| 通过 | 以 `source=cloud`、`review_status=pending` 插入 |

## 三、去重分两层

| 层 | 负责 | 机制 |
|---|---|---|
| **精确去重** | `ClaimIngestor` | 规范化文本相同（只差标点/空格）即视为同一条 |
| **近义去重** | `data` 层 + `kg_claim_merge` | 本地 bge 向量余弦相似度超阈值 → 建立 `派生ID → 规范ID` 映射 |

近义去重放在 `data` 层，是因为向量存储与计算归它管（`VectorCodec` 是 internal 的）。
`ClaimIngestor.recordMerge()` 提供写映射的入口。

> **注意**：近义去重的调度逻辑（取向量 → 比对 → 调 `recordMerge`）尚未接进管线，
> 见第五节。

## 四、使用示例

```kotlin
val provider = CloudProvider.DEEPSEEK.copy(apiKey = userKey)
val extractor = CloudClaimExtractor()
val ingestor = ClaimIngestor(ZhiJingData.get(context))

// 1. 抽取
val result = extractor.extract(
    provider = provider,
    content = "ReLU 是逐元素激活，输出形状与输入一致……",
    topicHint = "前馈网络",
    knownClaimIds = listOf("clm.arch.ffn.relu-shape.6092")
)

// 2. 解析器已返回 ExtractedClaim 列表；检查 warnings 决定是否提示用户
result.warnings.forEach { Log.w("kg", it) }

// 3. 入库（pending，不进视图）
val ingested = ingestor.ingest(
    claims = result.claims,
    path = "arch.ffn.relu",
    cardId = "crd.s1-2-relu",
    outlinePath = "arch.ffn.relu",
    providerId = provider.id,
    embeddingOf = { text -> localEngine.embed(text) }
)

// 4. 用户确认后才进入广度视图
ingested.inserted.forEach { claim ->
    data.kgClaims.review(claim.claimId, ClaimReviewStatus.CONFIRMED)
}
```

## 五、尚未做的部分

| 项 | 状态 | 说明 |
|---|---|---|
| **图谱模式开关** | 未做 | 设置页需要新增「离线 / 云端增强」，决定是否调用 `CloudClaimExtractor` |
| **近义去重调度** | 未做 | `kg_claim_merge` 的存储与查询已就绪，取向量→比对→`recordMerge` 的编排待接入 |
| **`GraphAssembler`** | 未做 | 把 `assets` 出厂命题与 Room 增量命题合并供 UI 读取 |
| **确认 UI** | 未做 | 候选需要界面展示与「确认/否决」按钮 |
| **evidence 逐字校验** | 未做 | 契约要求「evidence 逐字命中原文，命不中直接丢弃」，应在 `ClaimIngestor` 里加一道 |

## 六、验证

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhijing.kg.*"
```

新增 30 个用例：

- `ClaimIdsTest`（8）：规范化、ID 稳定性、格式校验、与 Python 交叉验证
- `ClaimOutputParserTest`（18）：容错链各步、补全规则、截断恢复、嵌套结构、类型过滤
- `ClaimOutputParserStructureTest`（4）：栈式括号匹配的定向回归

全仓 `:app` + `:data` 共 161 个用例全部通过。

### 修过的坑（写进测试防回归）

解析器最初用「两个计数器」匹配括号，在 `]}]}` 这类交错收尾上会切错数组体，
表现为**边全部丢失**。现在改为显式栈匹配，并规定「闭括号必须与栈顶同类」。
`findKey` 也从「按层级匹配」改为「内容相等且紧跟冒号」—— 后者天然区分了键与值，
且能处理目标 key 嵌套在数组元素里的情况（`claims[0].edges`）。
