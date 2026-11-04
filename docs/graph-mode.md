# 图谱模式：离线 / 云端增强

> 对应代码：`app/src/main/java/com/example/zhijing/kg/{GraphModeStore,GraphUpdateCoordinator}.kt`
> 前置：[存储分层](knowledge-graph-room.md) → [云端生成](knowledge-graph-cloud.md) → [合并加载](knowledge-graph-assembler.md)

## 一、模式定义

| 模式 | 行为 | 网络 |
|---|---|---|
| **离线** `OFFLINE` | 只读 `assets/kg/graph.json` 的出厂图谱。阅读、判断、状态回写全部可用 | **零请求** |
| **云端增强** `CLOUD_ENHANCED` | 出厂图谱 + 云端抽取的增量命题（先进 Room 待确认） | 调用户配置的服务商 |

默认是**离线**：不联网是最安全的默认值。

## 二、与聊天路由是两回事

| | 聊天路由 `RoutePolicy.RouteMode` | 图谱模式 `GraphMode` |
|---|---|---|
| 决定 | **谁来回答** | **图谱从哪来** |
| 档位 | `LOCAL_ONLY` / `CLOUD_FIRST` / `CLOUD_ONLY` | `OFFLINE` / `CLOUD_ENHANCED` |
| 发送给服务商 | 提问 + 检索到的片段 | **收藏的原文** |

**刻意是两个独立开关**：用户可能想用云端聊天但图谱保持离线（不把收藏原文发出去）。
图谱模式单列一份 SharedPreferences（`graph_mode`），不塞进 `CloudConfigStore` ——
后者被「清除云端配置」整体 `clear()`，而图谱模式属于用户偏好，不该跟着被重置。

## 三、三个执行约束

`GraphUpdateCoordinator` 是模式的**唯一执行点**：

```kotlin
when {
    mode == OFFLINE                          -> Skipped   // 绝不联网
    provider == null || 配置不完整            -> Skipped   // 同样不联网
    content.isBlank()                        -> Skipped
    else                                     -> 抽取 → 解析 → 入库(pending)
}
```

1. **离线绝不联网。** 这是用户选离线时唯一的预期。
2. **失败不阻塞内容保存。** 内容已经落库并建好索引；抽命题失败只意味着「这次没有新命题」，
   返回 `Outcome.Failed(reason)` 供 UI 提示，不抛异常。
3. **串行化。** 用 `Mutex` 保证同一次导入的多段抽取不并发挤爆额度、不乱序写库。

## 四、自动降级

用户在「云端增强」状态下清除了 API Key，会留下矛盾状态。两处兜底：

| 位置 | 行为 |
|---|---|
| `GraphModeStore.effective(hasCloud)` | 云端增强但无配置 → 实际按离线处理 |
| 清除云端配置的 UI 回调 | 主动把模式切回离线，并提示用户 |

## 五、接入点

目前接在**文档导入**流程（`MainActivity` 的 `importLauncher`）：

```
选择文件 → 解析 → 建索引 → [图谱更新]
                              ├─ OFFLINE → 提示「图谱未更新」
                              └─ CLOUD   → 抽取 → 入库(pending) → 提示「N 条候选需确认」
```

`indexDocument` 现在一并返回原文（`IndexedDocument.text`），避免图谱抽取再解析一遍文件。

## 六、加载侧

`GraphAssembler` 加了按模式的入口：

```kotlin
assembler.load(mode)      // OFFLINE → includeRuntimeClaims = false
assembler.observe(mode)   // 同上
```

离线模式**不查询增量命题表**，因此连本地已确认过的云端命题都不会混进来 ——
用户切到离线时视图是纯净的。

## 七、测试

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhijing.kg.*"
```

新增 9 个用例：

- `GraphModeTest`（4）：wire 值解析、未知值回落离线、空标签防护
- `GraphUpdateCoordinatorTest`（5）：**离线模式跳过**、无服务商跳过、配置不完整跳过、
  空内容跳过、两模式不可合并

全仓 `:app` + `:data` 共 **201 个用例通过**，`:app:assembleDebug` 构建成功。

## 八、尚未做

| 项 | 说明 |
|---|---|
| **候选确认 UI** | ❗最关键。抽取出的命题是 `pending`，**目前没有任何界面让用户确认**，所以它们永远进不了图谱 |
| **图谱页面接入** | 现有 UI 仍读静态图，未改为消费 `MergedGraph` |
| **导入之外的内容源** | 目前只有文档导入触发抽取；分享进来的文字、手动录入都还没接 |
| **多段抽取** | 超过 900 字的文档只取头部，未做切段循环 |
| **成本提示** | 每次导入都会调用模型，UI 尚未显示预估费用 |

> **当前状态提示**：模式开关、执行逻辑、降级、加载侧都已就绪，
> 但「云端抽取 → 入库 → 用户确认 → 进图谱」这一步的**确认界面缺失**，
> 所以云端增强模式下抽取出的命题暂时只能停在 Room 里。
