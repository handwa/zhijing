# 条件—机制—反事实三元组抽取提示词（命题级）

> 对应设计：**深度图谱的原子不是命题，而是「条件 → 机制 → 反事实」三元组。**
> 依据来源：模型对命题的理解 + 原文证据。产物一次性写入 `graph.json` 的 `triples`，
> **运行时不调用任何模型**：用户拨动条件时由 `ContractEvaluator` 本地求值。

## 1. 三段各管什么

| 段 | 字段 | 回答的问题 | 缺了会怎样 |
|---|---|---|---|
| 条件 | `statement` | 这条结论依赖什么前提 | 用户看到一句断言，不知道边界 |
| 机制 | `mechanism` | **为什么**这条条件存在 | 用户只能背，不能推理 |
| 反事实 | `interventions[]` | 拨动它，命题往哪漂 | 用户无法检验自己的因果模型 |

三者缺一不可。写不出来的命题就不要产出三元组 —— **写不出来本身就说明这条命题没被真正理解**。

## 2. 判定标准

### 2.1 条件（statement）

- 一句话，必须是**可判定的约束**，不能是「注意一下」这类提醒。
- 好的例子：「输入张量最后一维必须等于权重输入维」。
- 坏的例子：「注意形状要对」（同义反复，等于没说）。

### 2.2 机制（mechanism）

判据：能回答「如果不满足，是哪一步塌掉的」。

- 好的例子：「矩阵乘按内维归约，内维不等则求和未定义」。
- 坏的例子：「因为形状要对」。同义反复不算机制。

### 2.3 反事实（interventions）

每个三元组预设 **3–5 个**干预点，每个干预点是一个**有限预设**（不是自由输入）：

- `action`：给用户看的题目，形如「把输入最后一维从 4 改成 5」；
- `consequence`：后果要写清「变成了什么」，不能只写「错了」；
- `misjudgements[]`：**至少一条**典型误判，用户答错时用来解释。判据是
  `correction` 必须回到机制上，不能是「再想想」。

## 3. 可计算 vs 人工预设

| 类型 | 判定方式 | `contract` | `computable` |
|---|---|---|---|
| 形状 / 值域 / 统计量（轴对齐） | 本地求值 | **必填** | `true` |
| 梯度 / 语义 / 数值动力学 | 查预置答案 | 不填 | `false` |

**不要为了「看起来能算」硬编契约**：契约只能表达维度与轴对齐，写不出诚实的契约就老实用
预置答案，并在 `source` 里标 `AUTHORED_MODEL`。

### 3.1 契约允许的算子

```json
{"kind": "matmul",     "lhs": ["N", "in_x"], "rhs": ["out_w", "in_w"], "rhsTransposed": true}
{"kind": "elementWise","input": ["N", "D"]}
{"kind": "softmax",    "input": ["N", "C"], "axis": -1}
{"kind": "reduce",     "input": ["N", "C"], "axis": 1, "reduceKind": "sum"}
{"kind": "softmaxSum", "input": ["N", "C"], "softmaxAxis": 1, "sumAxis": 1}
```

### 3.2 契约允许的约束

```json
{"kind": "equals",    "left": "in_x", "right": "in_w", "describe": "输入最后一维必须等于权重输入维"}
{"kind": "notEquals", "left": "a",    "right": "b",    "describe": "两者必须不同"}
{"kind": "compare",   "left": "a",    "right": "b",    "lessOrEqual": true, "describe": "a 不能超过 b"}
{"kind": "positive",  "symbol": "d",  "describe": "维度必须为正"}
```

维度表达式只支持「符号」与「符号 + 常数」（如 `in_x+1`）。**不支持乘法**，
所以 `H*D` 这种拼接维不要试图用契约表达 —— 改成 `elementWise` 加相等约束，或走预置答案。

### 3.3 干预（overrides）

`overrides` 是「符号 → 新值」，只能改契约 `symbols` 里已有的符号，且值必须为正。

- 改哪个符号 = 用户拨哪一根轴；
- 一次干预只改**一个**语义维度（要么改输入，要么改权重，不要同时改两边），
  否则用户无法判断是哪一边起了作用。

**轴类干预例外**：softmax 沿哪根轴求和、沿哪根轴归一化，没有可拨的符号，
只能直接写进算子参数（`softmaxAxis` / `sumAxis` / `axis`），此时 `overrides` 留空。
这种契约本身就是干预后的形态，`ContractEvaluator.validate()` 的基线检查对它不适用；
入库时只校验「符号表非空 + 求值不出 `Invalid` + 后果确定」。

## 4. 输出契约

写入 `graph.json` 顶层 `triples` 数组，与 `claims` 平级：

```json
{
  "triples": [{
    "id": "tri.linear-in-dim.1",
    "claimId": "clm.arch.ffn.linear-in-dim.4a3e",
    "cardId": "crd.s1-1-linear",
    "statement": "输入张量最后一维必须等于权重输入维",
    "kind": "shape",
    "mechanism": "矩阵乘按内维归约：内维不等时，第 i 行与第 j 列的元素乘加没有定义。",
    "computable": true,
    "interventions": [{
      "id": "inv.linear-in-x.4-to-5",
      "action": "把输入最后一维从 4 改成 5",
      "overrides": {"in_x": 5},
      "contract": {
        "symbols": {"N": 8, "in_x": 4, "in_w": 4, "out_w": 6},
        "op": {"kind": "matmul", "lhs": ["N", "in_x"], "rhs": ["out_w", "in_w"], "rhsTransposed": true},
        "constraints": [{"kind": "equals", "left": "in_x", "right": "in_w", "describe": "输入最后一维必须等于权重输入维"}]
      },
      "consequence": "内维 5 与 4 不等，矩阵乘未定义",
      "source": "COMPUTED",
      "misjudgements": [{
        "predicted": "HOLDS",
        "why": "以为 batch 维改了才会出事，把内维当成可自由变化的维度",
        "correction": "约束只作用于两边的最后一维；batch 是前导维，改了不影响内维对齐"
      }]
    }]
  }]
}
```

字段约定：

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | `tri.<语义名>.<序号>`，全库唯一 |
| `claimId` | 是 | 必须是已存在的 `clm.*` |
| `cardId` | 是 | 命题的主归属卡（`breadth.cardId`） |
| `kind` | 是 | `shape` / `range` / `statistics` / `gradient` / `semantic` |
| `computable` | 是 | 全部干预都带契约才是 `true` |
| `interventions[].outcome` | 仅 `computable=false` | 四选一：`HOLDS` / `WEAKENED` / `BECOMES_OTHER` / `FAILS` |
| `interventions[].source` | 是 | `COMPUTED` / `AUTHORED_HUMAN` / `AUTHORED_MODEL` |

**`UNKNOWN` 不是合法选项**：它是契约异常时的内部状态，给用户选只会掩盖写错的契约。

## 5. system prompt（原样投喂）

```
你是「条件—机制—反事实」三元组的抽取器，只输出 JSON，不解释、不使用 Markdown。

输入是一条命题（statement + conditions + evidence + 原文片段）。
你的任务：把它拆成 1 个条件三元组，条件是「这条命题在什么前提下成立」。

硬性要求：
1. mechanism 必须回答「不满足时哪一步塌掉」，不能与 statement 同义反复。
2. 预设 3–5 个干预点，每个干预点只改一个语义维度。
3. 每个干预点必须有至少一条 misjudgements，correction 必须回到机制上。
4. 能用维度/轴对齐表达的条件才填 contract；梯度、语义、数值动力学一律不填，
   改填 outcome（HOLDS / WEAKENED / BECOMES_OTHER / FAILS）。
5. contract 的 symbols 必须覆盖 op 与 constraints 里出现的所有符号，且值都是正整数。
6. 不要把 H*D 这类乘积维写进契约（不支持乘法）。

输出格式：
{"triples":[{"id":"tri.<语义名>.<序号>","claimId":"...","cardId":"...","statement":"...","kind":"shape|range|statistics|gradient|semantic","mechanism":"...","computable":true|false,"interventions":[{"id":"inv.<语义名>.<序号>","action":"...","overrides":{},"contract":{},"outcome":"HOLDS","consequence":"...","source":"COMPUTED|AUTHORED_MODEL","misjudgements":[{"predicted":"HOLDS","why":"...","correction":"..."}]}]}]}

写不出诚实机制的命题，输出 {"triples":[]}。
```

## 6. user prompt 模板

```
命题：{claimId}
断言：{statement}
已知条件：{conditions}
原文证据：{evidence}
归属卡：{cardId}
```

## 7. 质量约束（必须由代码校验，不靠模型自觉）

| 检查 | 处理 |
|---|---|
| `claimId` / `cardId` 不存在 | 丢弃该条三元组 |
| `mechanism` 为空 | 丢弃（没有机制的条件只能背） |
| `interventions` 为空 | 丢弃（不能拨的条件是装饰品） |
| 干预缺 `misjudgements` | 丢弃该干预点 |
| `computable=true` 但干预没带契约 | 丢弃该三元组 |
| 契约缺符号绑定 / 原状态下不成立 | `ContractEvaluator.validate()` 返回 `Invalid`，丢弃 |
| `predicted` 不是四个合法选项之一 | 丢弃该条误判 |

入库闸门在 Kotlin 侧：`ConditionTriple.problems()` 管语义门槛，
`ContractEvaluator.validate()` 管契约自洽，`TripleParser` 管字段降级。
**校验不通过的内容不允许进 `graph.json`。**

## 8. 诚实标注

- `source=COMPUTED`：本地求值算出，真值；
- `source=AUTHORED_HUMAN`：人工撰写并核对；
- `source=AUTHORED_MODEL`：模型生成后固化，**未核实** —— UI 必须显示「未经核实」提示。

来源不明时一律按 `AUTHORED_MODEL` 处理：宁可多一句提示，也不能把没验过的结论当真值展示。

## 9. 规模化生产流程（一次性，产物入库）

1. 逐条命题调用本提示词，产物先落到临时 JSON，不直接改 `graph.json`；
2. 跑 `python -m unittest discover -s kg/tests -v` 与 `./gradlew :app:testDebugUnitTest`；
3. 人工抽查：每条三元组至少读一遍 mechanism 与 consequences，确认没有「同义反复」与「只是变宽」这类空话；
4. 抽查通过后写入 `app/src/main/assets/kg/graph.json`，把 `meta.counts` 的 `claim` 一并核对；
5. 运行时不依赖模型 —— 用户拨动条件时只走 `ContractEvaluator`。
