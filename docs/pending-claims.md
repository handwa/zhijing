# 候选确认：云端抽取命题的审核闭环

> 对应代码：`MainActivity.PendingClaimsDialog`、`GraphAssembler.pendingCandidates`
> 前置：[图谱模式](graph-mode.md) —— 这是「云端增强」模式的最后一段

## 一、为什么需要这一步

设计文档有一条硬约束：**云端生成的命题未经确认不得进入图谱**。

小模型按 prompt 抽出的命题质量不可控 —— 可能把「描述」当成「可判真假的断言」，
可能边界写得含糊，可能重复已有命题。直接进图谱会污染阅读体验，
而用户信任一旦被破坏，就再也不会打开图谱。

所以云端抽取的产物一律先进 Room，状态为 `pending`，等用户裁决。

## 二、完整闭环

```
导入文档
   │
   ├─ (离线模式) ──────────────────────► 提示「图谱未更新」
   │
   └─ (云端增强) 抽取 → 解析 → 入库 pending
                                    │
                                    ▼
                          侧边栏「待确认命题（N）」
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                      确认                  否决
                         │                     │
                         ▼                     ▼
                  加入图谱（可判断）      保留记录供去重
```

## 三、界面设计

`PendingClaimsDialog` 的三个取舍：

| 取舍 | 理由 |
|---|---|
| **默认全选** | 用户大多会接受，逐条勾选太繁琐；但仍可逐条取消 |
| **答案可见** | 这是**审核界面**，不是判断界面。用户在此是「编辑者」，需要看到答案与条件才能决定收不收。「先判后揭示」不适用于此 |
| **每条完整展开** | 展示命题、答案、条件、原文证据、来源服务商，让用户「扫一眼就能否决掉离谱的」 |

「全部否决」按钮：拒绝垃圾批次比逐条取消快得多。

## 四、数据流

```kotlin
// 侧边栏计数
graphAssembler.observePendingCandidates().collect { pendingCandidates = it }

// 确认 / 否决
data.kgClaims.review(claimId, ClaimReviewStatus.CONFIRMED, now)
data.kgClaims.review(claimId, ClaimReviewStatus.REJECTED, now)
```

`review()` 更新 `review_status` 与 `reviewed_time`：
- `CONFIRMED` → 进入 `observeConfirmed()`，被 `GraphAssembler` 合并进图谱
- `REJECTED` → 保留记录，后续去重时不再重复生成同类内容

## 五、一个容易误判的边界

**已确认的云端命题在离线模式下依然不出现。**

这是刻意的：`GraphAssembler.load(OFFLINE)` 不查询增量命题表。
用户切到离线时，视图里只有出厂图谱 —— 干净、可预期、不夹带任何云端产物。

代价是「同一张卡在两种模式下命题数不同」。这是设计选择，不是 bug。

## 六、测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhijing.kg.GraphAssemblerTest"
```

新增 5 个用例覆盖闭环：

- 待确认队列只包含 `pending`
- **确认后离开队列并进入图谱**（命题数 35 → 36，卡片下出现该命题）
- 否决后离开队列且不进入图谱
- 已确认命题在离线模式下仍不出现
- 卡片标题查询（出厂卡片能查到，云端新建卡返回 null）

全仓 `:app` + `:data` 共 **206 个用例通过**，`:app:assembleDebug` 构建成功。

## 七、现在整条链路的完整度

| 环节 | 状态 | 文档 |
|---|---|---|
| 存储分层 | ✅ | [knowledge-graph-room.md](knowledge-graph-room.md) |
| 云端生成（prompt → 解析 → 入库） | ✅ | [knowledge-graph-cloud.md](knowledge-graph-cloud.md) |
| 合并加载 | ✅ | [knowledge-graph-assembler.md](knowledge-graph-assembler.md) |
| 模式开关 | ✅ | [graph-mode.md](graph-mode.md) |
| **候选确认** | ✅ | 本文 |

## 八、尚未做

| 项 | 影响 |
|---|---|
| **图谱页面消费 `MergedGraph`** | ❗当前 App 内没有图谱浏览页面（`kg/web` 是独立演示页），`MergedGraph` 虽有数据但无界面呈现 |
| **近义去重调度** | `kg_claim_merge` 的存储与读取已就绪，缺「取向量 → 比对 → recordMerge」的编排 |
| **`evidence` 逐字校验** | 契约要求命中原文，命不中丢弃，尚未加 |
| **多段抽取** | 文档超过 900 字只取头部 |
