# 知识图谱的存储分层：出厂命题在 assets，运行时数据在 Room

> 对应代码：`data/src/main/java/com/zhijing/data/{entity,dao,repository}/Kg*.kt`
> 数据库版本：`ZhijingDatabase` v6（3 张运行时表 + 4 张生成图谱表）

## 一、要解决的问题

图谱要接云端生成，于是有三件事必须持久化：

1. 用户判断后产生的**状态**（否则每次打开都回到「未探测」）；
2. 云端生成的**新命题**（否则重启即丢）；
3. 云端重新生成同一命题时的**同义合并**（否则 ID 漂移、状态挂空）。

问题在于：**这三件事不等于「把整份图谱搬进 Room」。**

## 二、结论：分工存储，不整份搬

```
┌──────────────────────────────────────────┐
│ assets/kg/graph.json      出厂出版物        │  只读，随 APK 更新
│   outline / cards / claims / edges        │  130KB，离线可读已由它保证
└──────────────────────────────────────────┘
                  │ 运行时加载，与下表在内存里 join
                  ▼
┌──────────────────────────────────────────┐
│ Room (zhijing.db)         运行时数据        │  可写，用户与云端产生
│   kg_claim_state      命题探测状态          │
│   kg_runtime_claim    云端/用户增量命题      │
│   kg_claim_merge      同义合并映射          │
└──────────────────────────────────────────┘
```

**为什么不整份搬**：出厂命题是出版物而非用户数据；整份进库会引入「assets 与 Room 两份真相」
和每版 APK 的迁移负担，而离线可读这件事 `assets` 已经做到了。

**为什么不都放 assets**：判断状态、云端增量都要在运行时写，静态资产写不了。

一句话：**assets 管「可看」，Room 管「可变」。**

## 三、三张表

### 3.1 `kg_claim_state` —— 探测状态（回写摘要）

设计文档要求「回写是摘要而非明细」，所以只存收敛后的四字段，外加深度侧自用的计数。

| 列 | 类型 | 说明 |
|---|---|---|
| `claim_id` | TEXT PK | 命题 ID，与 assets 里的 `clm.*` 同一字符串 |
| `probe_status` | TEXT | unprobed / verified / mastered / confused |
| `last_probed_at` | INTEGER | 最近探测时间，毫秒 |
| `misconceptions` | TEXT | 误解注记，JSON 数组，最多 3 条 |
| `confidence` | REAL | 判对率，仅展示 |
| `attempts` / `correct` / `streak` | INTEGER | 深度侧留存，**不回写**广度侧 |

**收敛规则**（`ProbeRules`，与 `kg/web/app.js` 保持一致）：

- 判对 → `streak + 1`；`streak >= 2` 时 `mastered`，否则 `verified`
- 判错 → `streak = 0`，立刻 `confused`，并留下误解注记

这保证了「已探测 ≠ 已掌握」，以及「撞错后仍有『仍易错』这一态」。

### 3.2 `kg_runtime_claim` —— 云端 / 用户增量命题

| 列 | 说明 |
|---|---|
| `claim_id` | 主键，由 statement 推导，与出厂命题同一规范 |
| `statement` / `expected` | 断言与标准答案 |
| `conditions` | 成立条件，JSON 数组 |
| `explanation` / `misconception` / `evidence` | 撞错解释、误解、原文溯源 |
| `card_id` / `outline_path` | 主归属卡与大纲路径 |
| `source` | bundled / cloud / user |
| `review_status` | **pending / confirmed / rejected** |
| `provider_id` | 生成该命题的服务商，如 `deepseek` |
| `embedding` | 命题向量，供同义合并（可为空） |

生命周期：

```
云端生成 ──► pending（不进入任何视图）
               ├─ 确认 ──► confirmed（进入广度视图、可下钻判断）
               └─ 否决 ──► rejected（保留，供后续去重）
```

`ObserveConfirmed()` 只返回 `confirmed`，**「未经确认不出现」是 API 层约定**，不靠调用方自觉。

### 3.3 `kg_claim_merge` —— 同义合并

命题 ID = `sha1(规范化 statement)[:4]`。出厂命题 ID 固定没问题，但云端每次措辞略有不同，ID 就变：

```
第 1 次：Linear 层要求输入最后一维等于权重输入维度  → clm.arch.ffn.linear-in-dim.4a3e
第 2 次：Linear 要求输入的最后一维等于权重输入维度  → clm.arch.ffn.linear-in-dim.9f9f  ← 新 ID！
```

若不处理，库里会出现两条同义记录，而判断状态还挂在旧 ID 上。

| 列 | 说明 |
|---|---|
| `source_claim_id` | PK，派生出来的新 ID |
| `target_claim_id` | 合并到的规范 ID |
| `similarity` | 触发合并时的余弦相似度 |
| `basis` | vector / manual |

主键约束保证**一条源 ID 只指向一个目标**，因此不存在链式映射，`resolve(ids)` 一次解析即可。

### 3.4 `kg_generated_*` —— 用户输入主题后生成的图谱（v6 新增）

与前三张表不同，这一组存的是**完整的可写图谱**：用户在「生成图谱」页输入一个主题，模型产出大纲、
卡片与前置依赖后整份落库。它与出厂图谱并存，渲染时由 `GraphAssembler` 合并。

| 表 | 主键 | 说明 |
|---|---|---|
| `kg_generated_graph` | `topic_id` | 元信息：标题、来源（cloud/local/manual）、块数、`warnings`、`verified` |
| `kg_generated_outline` | `(topic_id, path)` | 大纲节点；`level` / `parentPath` 由 `OutlineBuilder` 本地推算 |
| `kg_generated_card` | `(topic_id, card_id)` | 卡片；`body` 是**原文逐字截取**，不是模型重写 |
| `kg_generated_prerequisite` | `(topic_id, from_card_id, to_card_id)` | 前置依赖；`kind` = required（实线）/ recommended（虚线） |

约定：

- 同一 `topic_id` 重复生成会**整份覆盖**（`GeneratedGraphRepository.save` 在一个事务里替换四张表），
  不保留历史版本；
- 这四张表**无外键**，删除主题走 `deleteGraph(topicId)`；
- `verified = 0` 表示正文由模型生成、未与原文核对，UI 需要区别提示。

## 四、使用方式

```kotlin
val data = ZhiJingData.get(context)

// 判断：记录一次作答，拿回收敛后的状态
val state = data.kgStates.recordJudgement(
    claimId = "clm.arch.ffn.relu-shape.6092",
    correct = false,
    misconception = "以为 ReLU 改了形状"
)

// 广度侧：派生回写，不存储状态
val states = data.kgStates.getStates(card.claimIds)

// 云端候选：先落库，等用户确认
data.kgClaims.addCandidate(claim, embedding = vec)
data.kgClaims.review(claimId, ClaimReviewStatus.CONFIRMED)

// 广度侧只看到已确认的
data.kgClaims.observeConfirmed().collect { ... }

// 读取前把 ID 规范化
val canonical = data.kgClaims.resolveClaimId(derivedId)

// 图谱更新后清理孤儿状态
data.kgStates.pruneOrphans(graph.claims.map { it.id })
```

## 五、几个刻意的取舍

| 取舍 | 理由 |
|---|---|
| 不为 `claim_id` 建外键 | 出厂命题在 assets，不在库里；外键无法跨越资产文件 |
| 误解用 JSON 列而非子表 | 回写只要「最近 3 条」，不需要行级查询，子表纯属开销 |
| 置信度存库而非现算 | 输入是 `correct/attempts`，若每次现算则两列就够；存下来的意义是允许未来换成加权模型而不必回填历史 |
| `pruneOrphans(emptyList())` 不清空 | 空列表多半是加载图谱失败，误删会毁掉用户全部判断记录 |
| 重复 ID 用 `IGNORE` 而非 `REPLACE` | 云端重复生成同一条时，不能把已确认的审核状态打回 pending |

## 六、尚未做的部分

- **去重算法本身**：`kg_claim_merge` 只提供存储与查询，`相似度 > 阈值 → 写入映射` 的调度逻辑待接入（建议放在内容解析管线里，复用本地 bge 向量）。
- **图谱加载器**：从 `assets/kg/graph.json` 读出厂命题并与 Room 增量合并的 `GraphAssembler` 尚未实现，UI 侧目前仍按原方式读静态图。
- **迁移**：`exportSchema = false`、`fallbackToDestructiveMigration()` 保持不变。当前是 v2 → v3 的整体弃库重建，开发期可接受；**上线前必须补 Migration**，否则用户升级会丢失全部判断状态。

## 七、验证

```bash
./gradlew :data:testDebugUnitTest --tests "com.zhijing.data.knowledge.*"
```

新增 21 个用例，覆盖：四态收敛边界、误解去重与截断、孤儿清理（含空列表保护）、
候选不出现在广度视图、确认/否决流转、重复生成不覆盖审核状态、批量 ID 解析。
`:data` 模块全量 60 个用例全部通过。
