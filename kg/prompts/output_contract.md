# 输出契约：JSON 与标注块的双向对应

> 本文件解决一个具体问题：**同一份抽取结果，人写的时候是 Markdown 标注块，机器读的时候是 JSON。**
> 两者必须能无损互转，否则「人工精标」和「模型回填」就会变成两套数据。

## 1. 两种形态，同一份语义

| 形态 | 谁写 | 存在哪 | 用途 |
|---|---|---|---|
| `yaml kg:claim` 标注块 | 人（精标）、模型（回填后落地） | 语料 `.md` 里 | 可读、可 review、可 diff |
| `graph.json` 里的 `claim` 对象 | `build_graph.py` | `kg/data/out/` | 可校验、可渲染、可导入 |

**标注块是源，JSON 是产物。** 不允许反过来手改 JSON —— 下次构建会被覆盖，且 ID 会漂。

## 2. 字段对应表

| 标注块键 | JSON 路径 | 必填 | 说明 |
|---|---|---|---|
| `id` | `claim.id` | 否 | 只写到 `clm.xxx` 即可，缺的 hash4 由脚本补 |
| `statement` | `claim.statement` | 是 | 一句话断言 |
| `expected` | `claim.depth.judgementPanel.expected` | 是 | `true` / `false` |
| `status` | `claim.depth.probeState.probe_status` | 否 | 缺省 `unprobed` |
| `conditions[]` | `claim.conditions[]` | 是 | 至少一条 |
| `evidence` | `claim.evidence` | 是 | 原文片段 |
| `misconception` | `...reveal.misconception` + `probeState.misconceptions[0]` | 否 | 撞错时回写的注记 |
| `explain` | `...reveal.explanation` | 否 | 不给则由「结论 + evidence」自动拼 |
| `error_kind` / `error_why` | `...reveal.errorKinds[0]` | 否 | 单条错点类型 |
| `error_kinds[]` | `...reveal.errorKinds[]` | 否 | 多条错点类型（优先于上面两个） |
| `edges[].to` | `edge.to` | 是 | 允许省略 hash 后缀，脚本前缀解析 |
| `edges[].type` | `edge.type` | 是 | 六类之一 |
| `edges[].kind` | `condition.kind` | 是 | 五类之一 |
| `edges[].premise` | `condition.premise` | 是 | 空则校验失败 |
| `edges[].boundary` | `condition.boundary` | 是 | 空则校验失败 |
| `edges[].counterexample` | `condition.counterexample` | 否 | 建议填 |
| `edges[].weight` | `edge.weight` | 否 | 缺省 0.5 |

标注块里的 `edges` 是**出边**（从当前命题指向别人）；JSON 里会同时写进 `edges` 表与 `depth.edges`（无向索引，便于两侧都展示）。

## 3. 最小可用标注块

```yaml
kg:claim
statement: ReLU 之后可以直接接 Softmax 输出类别概率。
expected: false
conditions:
  - 形状依赖满足，语义依赖不满足
evidence: 把 ReLU 直接接在 Softmax 前面，形状上完全能跑通。
```

其余字段全部有缺省值，能建出一条「可判断但信息偏薄」的命题 —— 校验能过，但 `reveal` 会显得空。**语料生产的实际标准是：`error_kind` 与至少一条边要有**，否则撞错时定位不到错点。

## 4. 解析器约定（脚本侧）

`kg_model.parse_kv_block()` 解析的是 **YAML 子集**，刻意没引 PyYAML：

- 缩进 2 空格；
- `key: value` 标量；`key:` + 子块 → 映射或列表；
- 列表项 `- 标量` 或 `- key: value`；
- `true/false`、整数、浮点自动转换，其余为字符串；
- 行尾 ` #` 起为注释（引号内不算）。

**不支持**多行字符串 `|`、锚点、流式写法 —— 用不上，而且小模型也写不出来。

## 5. 模型输出的容错链

```
原始文本
  ├─ 1. 直接 json.loads
  ├─ 2. 取第一个 { 到最后一个 } 再解析
  ├─ 3. 去掉 ```json 围栏后重试
  ├─ 4. 用正则救出 claims 数组，逐条尝试补全必需字段
  └─ 5. 放弃该条，记本地日志（时间、原因、模型版本），不影响其他字段
```

第 4 步的补全规则：缺 `expected` 一律按 `false` 并打警告（宁可判错也不静默当成立）；缺 `conditions` 补「未标注条件」——**这类条目校验会失败，必须人工补**，这正是把质量问题挡在入库前的手段。

## 6. 版本与兼容

`meta.schemaVersion` 目前为 `1.0`。字段只增不改：

- 新增可选字段：小版本号 +0.1，旧数据仍合法；
- 改语义或删字段：大版本号 +1，同时写迁移说明。

`graph.schema.json` 的 `additionalProperties: false` 是刻意的 —— 字段写错名字会直接报错，而不是被静默忽略。

## 7. 与 Android 侧的对接

`export.py --target assets` 产出的 `app/src/main/assets/kg/graph.json` 与本契约完全一致；
`--target sql` 产出的 `kg_node` / `kg_edge` 表把整条 JSON 存进 `payload` 列，读侧反序列化即可，不需要逐字段建列。
这样契约升级时，Room 侧不用跟着改表结构。
