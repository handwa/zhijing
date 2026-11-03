# 命题与条件边抽取提示词（端侧 LLM）

> 这是「深度探索图谱」唯一的自动内容来源。设计文档要求：命题必须是一句话可判真假的断言，**边必须带条件**（成立前提、边界、反例）。
> 落点：用户收藏内容解析完成后按需触发（不是每条都跑），或离线批量补充语料。

## 1. 任务定义

输入一段中文文本，输出**至多两条**命题；每条命题带：

- `statement` 一句话断言（必须能判对错，不是描述、不是建议）
- `expected` 该断言在标准语境下是否成立
- `conditions` 成立条件 / 边界（1–3 条）
- `evidence` 原文里支撑或反驳它的片段（逐字摘，不改写）
- `errorKinds` 使用者最容易踩的错点类型（五选一）
- `edges` 与已知命题的关系（可选，至多两条），每条边必须带 premise / boundary / counterexample
- `misconception` 最常见的误解说法（可选）

## 2. system prompt（原样投喂）

```
你是知识图谱的命题抽取器，只输出 JSON，不解释、不使用 Markdown。
任务：把文本里的「可判真假的断言」抽成命题，并给出判断它所需的边界。

判断命题是否合格的三问：
1. 能不能判对错？描述、建议、步骤都不合格。
2. 是不是一句话？多个论点必须拆开，一次只留一条。
3. 边界能不能说清？说不清就说明它还不是一条命题。

conditions 写「在什么前提下成立」与「什么情况下不再适用」，不要复述 statement。
evidence 必须逐字摘自原文，找不到原文依据就整条不要。
errorKinds.kind 只能取：shape（形状依赖）/ range（值域依赖）/ statistics（统计量依赖）/ gradient（梯度路径依赖）/ semantic（语义依赖）。

edges 只允许以下类型：
depends_on 依赖 / affects 影响 / contains 组成 / contrasts 对比 / refutes 反例 / prerequisite 前置。
每条边必须写 premise（前提）与 boundary（边界），写不出边界的边不要输出。

输出格式：
{"claims":[{"statement":"...","expected":true,"conditions":["..."],
 "evidence":"...","errorKinds":[{"kind":"semantic","why":"..."}],
 "misconception":"...","edges":[{"to":"<命题ID或空>","type":"depends_on",
 "kind":"shape","premise":"...","boundary":"...","counterexample":"..."}]}]}

没有任何合格命题时输出 {"claims":[]}。
```

## 3. user prompt 模板

```
主题：{outlineTitle}
已知命题（可用于 edges.to，没有就留空）：{claimIdList}
文本：
"""
{content}
"""
```

`claimIdList` 只放**同一主题下**已有的 3–5 条命题 ID：小模型面对长清单会乱指，宁少勿多。

## 4. few-shot（必带，这决定了输出质量）

```
文本：
"""
ReLU 是逐元素激活，输出形状与输入一致，但它输出全为非负数。Softmax 期望的输入是
未归一化的 logits（可正可负）。把 ReLU 直接接在 Softmax 前面，形状上完全能跑通，
只是负半轴的信息已经丢失，C=1 时输出恒为 1。
"""

输出：
{"claims":[
 {"statement":"ReLU 之后可以直接接 Softmax 输出类别概率。",
  "expected":false,
  "conditions":[
    "形状依赖满足：ReLU 不改变形状，Softmax 接受同形输入",
    "语义依赖不满足：Softmax 期望未归一化 logits，ReLU 输出全非负",
    "边界：C=1 时 Softmax 恒输出 1，没有区分度"],
  "evidence":"把 ReLU 直接接在 Softmax 前面，形状上完全能跑通，只是负半轴的信息已经丢失。",
  "errorKinds":[{"kind":"semantic","why":"形状对得上但含义不对，是最隐蔽的一类错"}],
  "misconception":"看到形状能跑通、loss 也在降，就认定这条连接成立。",
  "edges":[]},
 {"statement":"Softmax 的输入必须是未归一化的 logits。",
  "expected":true,
  "conditions":["输入可正可负","前一层不要再做一次 Softmax"],
  "evidence":"Softmax 期望的输入是未归一化的 logits（可正可负）。",
  "errorKinds":[{"kind":"range","why":"把概率当输入，等于多做一次归一化"}],
  "misconception":"以为 Softmax 接 Softmax 只是多此一举，不会影响结果。",
  "edges":[{"to":"","type":"contrasts","kind":"semantic",
    "premise":"两条命题都在谈 Softmax 的输入要求",
    "boundary":"仅在分类头场景成立",
    "counterexample":"输入已是概率分布时，结论翻转"}]}
]}
```

示例刻意展示三件事：`expected` 可以为 false；`conditions` 里出现「边界：」；边的 `to` 允许为空（由脚本事后按主题解析）。

## 5. 调用参数

| 参数 | 值 | 说明 |
|---|---|---|
| temperature | 0.1 | 命题必须稳定，重跑不该换个说法 |
| max_generate_tokens | 512 | 两条命题的上限 |
| 每次输入文本 | ≤900 字 | 再长就切段，一条内容分段跑 |

## 6. 为什么不让模型直接产出 ID

命题 ID 由 `sha1(规范化 statement)[:4]` 生成（见 `schema/id-spec.md`）。让模型写 ID 有两个后果：

1. 它会瞎编后缀，ID 立刻不合法；
2. 它每次生成的 ID 不同，同一命题无法收敛。

所以模型只负责 `statement`，ID 一律由 `build_graph.py` 计算。模型输出的 `edges.to` 允许是已有 ID，也允许留空。

## 7. 质量红线（写进验收）

| 指标 | 门槛 | 检查方式 |
|---|---|---|
| JSON 可解析 | ≥95% | `output_contract.md` 的解析器 + 本地日志统计 |
| 命题可判真假 | 人工抽检 20 条，≥18 条合格 | 研究记录 |
| evidence 逐字命中原文 | 100% | 脚本回查，命中不了直接丢弃 |
| 边带边界 | 100% | `validate_graph.py` 规则 4 |

达不到就缩范围：先只做一个垂直领域（例如「神经网络基础」）的一小簇命题，跑通闭环再扩 —— 这也是设计文档「风险」一节给的缓解办法。
