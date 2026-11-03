# kg · 知识图谱构建（队员二交付）

> 依据：`docs/knowledge-graph-design(1).md`（主）、`docs/知境-软件需求规格说明书V2-端侧LLM方案.docx`（辅）
> 对应需求：M6-01 主题树可视化、4.2.2 后台理解、3.3.1 分析结果、6.1.2 认知庭院

一句话：**广度图谱负责「覆盖」，深度图谱负责「穿透」；这里交付的是把两者建起来、校验住、渲染出来的那一套东西。**

## 一分钟跑通

```bash
cd zhijing
python kg/src/build_graph.py        # 语料 → kg/data/out/graph.json
python kg/src/validate_graph.py     # 逐条校验设计文档的 8 条核心约束
python kg/src/export.py --all       # 导出 web 数据 / Android assets / 种子 SQL
```

然后双击 `kg/web/index.html`（不需要服务器、不需要 npm）。

当前产物：**24 个大纲节点 / 22 张卡片 / 35 条命题 / 44 个实体 / 16 条带条件边**。

## 目录

```
kg/
├─ README.md                      ← 你在这里
├─ schema/
│  ├─ graph.schema.json           JSON Schema（节点/边/条件/状态）
│  └─ id-spec.md                  ID 命名规范与稳定性要求
├─ prompts/
│  ├─ extract_entities.md         端侧 LLM 实体抽取提示词
│  ├─ extract_claims.md           命题 + 条件边抽取提示词（含 few-shot）
│  └─ output_contract.md          JSON ↔ 标注块的双向契约与容错链
├─ src/
│  ├─ kg_model.py                 枚举 / ID / 哈希 / YAML 子集解析（唯一实现）
│  ├─ parse_markdown.py           Markdown → 大纲 + 卡片 + 标注块
│  ├─ extract_rules.py            规则抽取：实体、判断题、组件链
│  ├─ build_graph.py              组装：主归属、交叉链接封顶、边解析
│  ├─ validate_graph.py           8 条核心约束 + 结构完整性校验
│  └─ export.py                   导出 web / assets / sql
├─ data/
│  ├─ source/transformer-arch.md  语料（Transformer 架构，含 30 个精标块）
│  └─ out/                        graph.json / graph.sql（产物）
└─ web/
   ├─ index.html                  广度视图 + 结构图 + 深度判断面板
   ├─ app.js  style.css
   ├─ graph-data.js               产物（window.KG=…）
   └─ 展示格式说明.md             三屏展示契约与视觉规范
```

## 管线

```
语料 .md
  │  parse_markdown.py     标题 → 大纲；段落/表格/代码块 → 卡片；kg 块 → 待解析
  │  extract_rules.py      词典/加粗/行内代码 → 实体；判断题表 → 命题；A→B 链 → 组件链
  │  build_graph.py        补 breadth/depth 双视图、主归属、交叉链接、边端点解析、导航树
  │  validate_graph.py     8 条约束 + 结构完整性，失败即非 0 退出
  └  export.py             graph-data.js / assets graph.json / graph.sql
```

每一步都可以单独跑，`--json` 看中间结果：

```bash
python kg/src/parse_markdown.py            # 打印章节树
python kg/src/parse_markdown.py --json     # 看切块明细
python kg/src/extract_rules.py             # 看抽取统计与告警
python kg/src/export.py --stats            # 状态/边/条件的分布
```

## 三条设计硬约束怎么落地的

| 设计文档约束 | 代码位置 | 怎么检查 |
|---|---|---|
| 命题 ID 是唯一主键 | `kg_model.claim_id` | `validate_graph.py` 规则 1（含「哈希必须由 statement 推出」） |
| 一个命题只有一个主归属卡 | `build_graph.build_claim` | 规则 2（反向查 `card.claimIds`） |
| 交叉链接封顶 3–5 条并排序 | `build_crosslinks` | 规则 5 |
| 深度侧条件不在第一屏展开 | `build_claim` 的 `reveal` | 规则 6（breadth 字段白名单 + 正文不得含答案/反例） |
| 回写是摘要而非明细 | `probeState` 四字段 | 规则 7（越界字段报错；广度侧禁止自带状态） |
| 深度是下钻，不是并列 | `build_navigation` | 规则 8（导航里出现 claim 直接失败） |
| 边必须带条件 | `build_edges` | 规则 4（premise + boundary + 五类 kind） |

## 加内容怎么加

1. 在 `data/source/*.md` 里按「标题分层 → 段落讲清 → `kg:claim` 标注判断」的顺序写；
2. 一个 `###` 小节 = 一张卡片；一张卡建议挂 1–3 条命题（超过 5 条说明这一节该拆）；
3. 边写在出边一侧，`to` 可以省略 hash 后缀，脚本会按前缀解析；
4. 跑 `build_graph.py` + `validate_graph.py`，`meta.warnings` 会告诉你「哪条边指向了不存在的命题」「哪条命题被改名了」。

换一个新领域（不限于 Transformer）：

- 在 `kg_model.TERM_LEXICON` 里加该领域的名词与 kind；
- 新增 `data/source/<domain>.md`；
- `build_graph.py` 支持 `--out`，也可以复制一份改成多语料合并（当前是单语料，保持简单）。

## 与 Android 端的关系

| 产出 | 路径 | 用途 |
|---|---|---|
| `graph.json` | `app/src/main/assets/kg/graph.json` | 运行时只读加载，用于渲染庭院/大纲 |
| `graph.sql` | `kg/data/out/graph.sql` | 参考表结构 + 种子数据，供 `:data` 模块在 Migration 中引入 |

**没有改动 `:data` 的 Room 实体与 version**（那是队友在动的代码）。`kg_node` 用 TEXT 主键直接落命题 ID —— 换成自增整数就等于多维护一张映射表，ID 不再是唯一真相源。

## 依赖

Python 3.9+，**只用标准库**（无 PyYAML、无 jsonschema）。装了 `jsonschema` 会自动多跑一遍 Schema 校验，没装则跳过并标注。

## 常见问题

**Q：为什么不用 PyYAML？**
A：端侧/评审机器不一定装。标注块的语法是刻意收窄的子集，40 行解析器就够，且能给出带行号的错误信息。

**Q：为什么规则抽取不直接用 LLM？**
A：规则抽取可复现、可 diff、零幻觉，适合建库与回归；LLM 抽取负责开放词表与运行时扩充。两者写入同一格式，见 `prompts/output_contract.md`。

**Q：为什么演示页要生成 `graph-data.js` 而不是 fetch json？**
A：`file://` 下 fetch 会被 CORS 拦，评审时双击打不开是最常见的翻车点。包成 `window.KG` 就没这个问题。

**Q：命题为什么有的只有 1 条边、有的 3 条？**
A：边表达的是「依赖与影响」，不是画着好看。孤立命题（如位置编码）本来就自成一簇，硬连边会让图谱变成装饰。
