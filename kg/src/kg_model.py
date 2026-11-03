#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
kg_model.py —— 知识图谱的「唯一真相」定义层。

本文件只放**规范**，不放任何抽取逻辑：
    * 节点类型 / 边类型 / 条件类型 / 状态枚举
    * ID 生成与校验（对应 schema/id-spec.md）
    * 文本规范化与稳定哈希
    * kg 标注块（Markdown 内嵌 YAML 子集）的解析器
    * 领域实体词典（规则抽取的判定依据）

对应设计文档：docs/knowledge-graph-design(1).md
    - 节点类型对应「分层大纲 / 卡片 / 叶子卡片 / 命题」
    - 边类型对应「依赖与影响」，条件对应「适用范围」
    - 状态枚举对应「未探测 / 已验证 / 已掌握 / 易混淆」

用法：被 parse_markdown.py / extract_rules.py / build_graph.py / validate_graph.py / export.py 导入。
命令行自检：python kg/src/kg_model.py
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

# --------------------------------------------------------------------------
# 0. 路径常量
# --------------------------------------------------------------------------

KG_ROOT = Path(__file__).resolve().parent.parent          # zhijing/kg
REPO_ROOT = KG_ROOT.parent                                # zhijing
SOURCE_MD = KG_ROOT / "data" / "source" / "transformer-arch.md"
GRAPH_JSON = KG_ROOT / "data" / "out" / "graph.json"
WEB_DATA_JS = KG_ROOT / "web" / "graph-data.js"

SCHEMA_VERSION = "1.0"

# --------------------------------------------------------------------------
# 1. 枚举：节点、边、条件、状态
# --------------------------------------------------------------------------

NODE_TYPES = ("outline", "card", "claim", "entity")

#: 节点 ID 前缀 —— 与 id-spec.md 一一对应
ID_PREFIX = {
    "outline": "out",
    "card": "crd",
    "claim": "clm",
    "entity": "ent",
    "edge": "edg",
}

#: 边类型：设计文档要求「边本质是依赖与影响」，故全部为语义关系而非画线
EDGE_TYPES = {
    "depends_on": "依赖：下游成立需要上游成立",
    "affects": "影响：上游变化会改变下游结果",
    "contains": "组成：整体由部分构成",
    "contrasts": "对比：两者易混，需并置区分",
    "refutes": "反例：存在使该命题不成立的场景",
    "prerequisite": "前置：学习顺序上的先后",
}

#: 边类型短码，用于生成稳定的边 ID
EDGE_TYPE_CODE = {
    "depends_on": "dep",
    "affects": "aff",
    "contains": "con",
    "contrasts": "ctr",
    "refutes": "ref",
    "prerequisite": "pre",
}

#: 条件类型：设计文档「撞错」时要能定位错点落在这五类依赖的哪一类
CONDITION_KINDS = {
    "shape": "形状依赖：张量维度能否接上",
    "range": "值域依赖：输出取值范围是否满足下游假设",
    "statistics": "统计量依赖：归一化沿哪一维、用谁的统计量",
    "gradient": "梯度路径依赖：梯度能否回传、是否被截断",
    "semantic": "语义依赖：形状对得上但含义不对",
}

#: 命题状态：注意「已探测」≠「已掌握」，易混淆必须独立成态
PROBE_STATES = {
    "unprobed": "未探测",
    "verified": "已验证",
    "mastered": "已掌握",
    "confused": "易混淆",
}

#: 实体类型
ENTITY_KINDS = {
    "concept": "概念",
    "method": "方法/层",
    "metric": "指标/损失",
    "tool": "工具/框架",
    "dataset": "数据集",
    "formula": "公式/张量记号",
}

#: 回写字段白名单：宽度侧只接收这 4 个，逐次作答明细留在深度侧
WRITEBACK_FIELDS = ("probe_status", "last_probed_at", "misconceptions", "confidence")

#: 广度卡片上允许出现的字段（用于校验「第一屏不铺开条件」）
BREADTH_FIELDS = ("outlinePath", "cardId", "cardSummary", "leafContent")

#: 交叉链接上限：设计文档要求 3–5 条并排序
CROSSLINK_MIN = 3
CROSSLINK_MAX = 5


# --------------------------------------------------------------------------
# 2. 文本规范化 / 哈希 / slug
# --------------------------------------------------------------------------

_PUNCT = "，。！？；：、（）【】《》「」『』“”‘’…—·,.;:!?()[]{}<>\"'"


def normalize(text: str) -> str:
    """规范化命题文本：去空白、去标点、全半角统一。

    命题 ID 的哈希基于规范化后的文本，因此**只改标点不改 ID**，
    改了实词就会换 ID —— 这正是 id-spec.md 里「ID 当发布物对待」的含义。
    """
    text = (text or "").strip()
    text = text.replace("\u3000", " ")
    text = re.sub(r"\s+", "", text)
    text = "".join(ch for ch in text if ch not in _PUNCT)
    return text.lower()


def short_hash(text: str, length: int = 4) -> str:
    """稳定短哈希：sha1 前 N 位十六进制。同样的输入永远得到同样的输出。"""
    return hashlib.sha1(normalize(text).encode("utf-8")).hexdigest()[:length]


def slugify(text: str, fallback: str = "node") -> str:
    """把中文/英文标题压成可读 slug。

    中文标题无法转拼音（不引第三方库），策略是：保留 ASCII 词，
    非 ASCII 段落整体丢弃，再补哈希兜底 —— 保证 slug 短且稳定。
    """
    ascii_part = re.sub(r"[^a-z0-9.]+", "-", (text or "").lower()).strip("-.")
    ascii_part = re.sub(r"\.{2,}", ".", ascii_part)
    if ascii_part:
        return ascii_part[:40]
    return f"{fallback}-{short_hash(text, 6)}"


# --------------------------------------------------------------------------
# 3. ID 生成与校验
# --------------------------------------------------------------------------

_ID_RE = re.compile(r"^(out|crd|clm|ent|edg)\.[a-z0-9][a-z0-9._-]*$")
_HASHED_ID_RE = re.compile(r"^(clm|ent|edg)\.[a-z0-9][a-z0-9._-]*\.[0-9a-f]{4,8}$")


def outline_id(path_slug: str) -> str:
    """大纲节点 ID：`out.<语义路径>`，只要求路径唯一，不参与命题身份。"""
    return f"out.{path_slug}"


def card_id(path_slug: str, index: int = 1) -> str:
    """卡片 ID：`crd.<语义路径>`，同一路径多卡时追加序号。"""
    return f"crd.{path_slug}" if index <= 1 else f"crd.{path_slug}.{index}"


def claim_id(slug: str, statement: str) -> str:
    """命题 ID：`clm.<slug>.<hash4>`，哈希取自规范化命题文本。"""
    return f"clm.{slugify(slug, 'claim')}.{short_hash(statement)}"


def entity_id(kind: str, name: str) -> str:
    """实体 ID：`ent.<kind>.<slug>.<hash4>`，按词条名去重。"""
    return f"ent.{kind}.{slugify(name, 'term')}.{short_hash(name)}"


def edge_id(from_id: str, to_id: str, edge_type: str) -> str:
    """边 ID：`edg.<类型短码>.<hash6>`，端点或类型变化即换 ID。"""
    code = EDGE_TYPE_CODE.get(edge_type, "rel")
    digest = hashlib.sha1(f"{from_id}|{to_id}|{edge_type}".encode("utf-8")).hexdigest()[:6]
    return f"edg.{code}.{digest}"


def is_valid_id(node_id: str) -> bool:
    """ID 格式校验：前缀合法 + 结构合法 + 需要稳定的类型带哈希后缀。"""
    if not isinstance(node_id, str) or not _ID_RE.match(node_id):
        return False
    if node_id.split(".")[0] in ("clm", "ent", "edg"):
        return bool(_HASHED_ID_RE.match(node_id))
    return True


# --------------------------------------------------------------------------
# 4. 领域实体词典（规则抽取的判定依据）
# --------------------------------------------------------------------------

#: 词条 -> (kind, aliases)。命中即建实体；未命中但被加粗/行内代码包裹的按 concept 处理。
TERM_LEXICON: Dict[str, Dict[str, Any]] = {
    "Linear": {"kind": "method", "aliases": ["线性层", "全连接层", "FC", "Dense"]},
    "ReLU": {"kind": "method", "aliases": ["线性整流", "Rectified Linear Unit"]},
    "GELU": {"kind": "method", "aliases": []},
    "Sigmoid": {"kind": "method", "aliases": []},
    "Softmax": {"kind": "method", "aliases": ["归一化指数函数"]},
    "CrossEntropy": {"kind": "metric", "aliases": ["交叉熵", "交叉熵损失", "CE"]},
    "MSE": {"kind": "metric", "aliases": ["均方误差"]},
    "LayerNorm": {"kind": "method", "aliases": ["层归一化", "LN"]},
    "BatchNorm": {"kind": "method", "aliases": ["批归一化", "BN"]},
    "Dropout": {"kind": "method", "aliases": ["随机失活"]},
    "残差连接": {"kind": "concept", "aliases": ["残差", "捷径连接", "skip connection", "Residual"]},
    "缩放点积注意力": {"kind": "method", "aliases": ["scaled dot-product attention", "点积注意力"]},
    "多头注意力": {"kind": "method", "aliases": ["Multi-Head Attention", "MHA"]},
    "自注意力": {"kind": "method", "aliases": ["Self-Attention"]},
    "因果掩码": {"kind": "concept", "aliases": ["causal mask", "下三角掩码"]},
    "位置编码": {"kind": "concept", "aliases": ["positional encoding", "位置嵌入"]},
    "logits": {"kind": "formula", "aliases": ["未归一化得分"]},
    "batch": {"kind": "formula", "aliases": ["批", "批大小", "batch size"]},
    "张量形状": {"kind": "formula", "aliases": ["shape", "形状"]},
    "学习率": {"kind": "concept", "aliases": ["learning rate", "lr"]},
    "Adam": {"kind": "tool", "aliases": ["AdamW"]},
    "Softmax 饱和": {"kind": "concept", "aliases": ["饱和"]},
    "梯度消失": {"kind": "concept", "aliases": []},
    "Pre-LN": {"kind": "concept", "aliases": ["Pre-LayerNorm"]},
    "Post-LN": {"kind": "concept", "aliases": ["Post-LayerNorm"]},
}

#: 别名 -> 标准名（同一实体多种写法归并到 canonicalName）
ALIAS_TO_CANON: Dict[str, str] = {}
for _canon, _meta in TERM_LEXICON.items():
    ALIAS_TO_CANON[_canon.lower()] = _canon
    for _alias in _meta.get("aliases", []):
        ALIAS_TO_CANON[_alias.lower()] = _canon


# --------------------------------------------------------------------------
# 5. kg 标注块解析器（YAML 子集，零依赖）
# --------------------------------------------------------------------------

class KgBlockError(ValueError):
    """标注块格式错误。错误信息带行号，方便定位语料问题。"""


def _strip_comment(line: str) -> str:
    """去掉行尾注释：只处理不在引号内的 ` #`。"""
    in_single = in_double = False
    for i, ch in enumerate(line):
        if ch == "'" and not in_double:
            in_single = not in_single
        elif ch == '"' and not in_single:
            in_double = not in_double
        elif ch == "#" and not in_single and not in_double:
            if i == 0 or line[i - 1] in " \t":
                return line[:i].rstrip()
    return line.rstrip()


def _unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        return value[1:-1]
    return value


def parse_scalar(value: str) -> Any:
    """标量转换：true/false/int/float/空串。其余保持字符串。"""
    value = _unquote(_strip_comment(value))
    low = value.lower()
    if low in ("true", "yes", "成立", "1"):
        return True
    if low in ("false", "no", "不成立", "0"):
        return False
    if low in ("null", "none", "~"):
        return None
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    if re.fullmatch(r"-?\d+\.\d+", value):
        return float(value)
    return value


def parse_kv_block(text: str) -> Dict[str, Any]:
    """解析 kg 标注块的 YAML 子集。

    支持的结构（够用即可，刻意不引 PyYAML —— 端侧/评审环境不一定装了）：

        key: 值
        list:
          - 标量
          - 标量
        maplist:
          - to: clm.xxx
            type: depends_on
            condition:
              premise: ...
              boundary: ...

    缩进单位固定 2 空格；`- key: value` 表示列表中的映射项。
    实现是「按缩进递归下降」：容器类型（列表/映射）由首个子行的形态决定。
    """
    tokens = _tokenize(text)
    if not tokens:
        return {}
    value, _ = _parse_block(tokens, 0, tokens[0][0])
    if not isinstance(value, dict):
        raise KgBlockError("标注块顶层必须是映射（key: value）")
    return value


def _tokenize(text: str) -> List[tuple]:
    """切行并记录缩进。空行与整行注释被丢弃，行号保留用于报错。"""
    tokens: List[tuple] = []
    for lineno, raw in enumerate(text.splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        line = _strip_comment(raw.rstrip())
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        tokens.append((indent, line.strip(), lineno))
    return tokens


def _parse_block(tokens: List[tuple], i: int, indent: int):
    """解析一个容器（映射或列表），返回 (值, 下一个 token 下标)。"""
    if tokens[i][1].startswith("- "):
        return _parse_list(tokens, i, indent)
    return _parse_map(tokens, i, indent)


def _parse_map(tokens: List[tuple], i: int, indent: int):
    result: Dict[str, Any] = {}
    while i < len(tokens):
        ind, content, lineno = tokens[i]
        if ind < indent:
            break
        if ind > indent:
            raise KgBlockError(f"第 {lineno} 行：缩进不一致：{content!r}")
        if content.startswith("- "):
            break
        if ":" not in content:
            raise KgBlockError(f"第 {lineno} 行：缺少冒号，无法解析：{content!r}")
        key, _, val = content.partition(":")
        key, val = key.strip(), val.strip()
        i += 1
        if val:
            result[key] = parse_scalar(val)
            continue
        # 空值：有更深缩进的子块就递归，否则视为空映射
        if i < len(tokens) and tokens[i][0] > indent:
            result[key], i = _parse_block(tokens, i, tokens[i][0])
        else:
            result[key] = {}
    return result, i


def _parse_list(tokens: List[tuple], i: int, indent: int):
    items: List[Any] = []
    while i < len(tokens):
        ind, content, lineno = tokens[i]
        if ind != indent or not content.startswith("- "):
            break
        body = content[2:].strip()
        i += 1
        if ":" in body and not body.startswith(("'", '"')):
            key, _, val = body.partition(":")
            key, val = key.strip(), val.strip()
            item: Dict[str, Any] = {}
            if val:
                item[key] = parse_scalar(val)
            elif i < len(tokens) and tokens[i][0] > indent:
                item[key], i = _parse_block(tokens, i, tokens[i][0])
            else:
                item[key] = {}
            # 同一列表项的其他键（缩进比 `-` 更深且不是列表项）
            if i < len(tokens) and tokens[i][0] > indent and not tokens[i][1].startswith("- "):
                extra, i = _parse_map(tokens, i, tokens[i][0])
                item.update(extra)
            items.append(item)
        else:
            items.append(parse_scalar(body))
            if i < len(tokens) and tokens[i][0] > indent:
                raise KgBlockError(f"第 {lineno} 行：标量列表项不应有子块：{content!r}")
    return items, i


def parse_list_value(block: Dict[str, Any], key: str) -> List[Any]:
    """取列表字段，容忍单值写法（LLM 常这么犯错）。"""
    value = block.get(key)
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


# --------------------------------------------------------------------------
# 6. 自检
# --------------------------------------------------------------------------

def _selftest() -> int:
    sample = """
statement: Softmax 输出沿类别维求和为 1。
expected: true
status: verified
conditions:
  - 类别维是最后一维
  - 数值稳定实现先减最大值
edges:
  - to: clm.arch.softmax-shift
    type: depends_on
    kind: statistics
    premise: 指数函数对整体平移不敏感
    boundary: 仅在实数域成立
    counterexample: 无
    weight: 0.9
"""
    parsed = parse_kv_block(sample)
    assert parsed["expected"] is True, parsed
    assert len(parsed["conditions"]) == 2, parsed
    assert parsed["edges"][0]["kind"] == "statistics", parsed
    assert claim_id("arch.softmax-sum", parsed["statement"]).startswith("clm.arch.softmax-sum.")
    assert is_valid_id(claim_id("arch.softmax-sum", parsed["statement"]))
    assert not is_valid_id("clm.arch.softmax-sum")
    assert edge_id("clm.a", "clm.b", "depends_on").startswith("edg.dep.")
    print("[kg_model] 自检通过：标注块解析 / ID 生成 / ID 校验")
    return 0


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    if len(sys.argv) > 1 and sys.argv[1] == "--json":
        print(json.dumps({
            "nodeTypes": NODE_TYPES,
            "edgeTypes": EDGE_TYPES,
            "conditionKinds": CONDITION_KINDS,
            "probeStates": PROBE_STATES,
            "entityKinds": ENTITY_KINDS,
        }, ensure_ascii=False, indent=2))
    raise SystemExit(_selftest())
