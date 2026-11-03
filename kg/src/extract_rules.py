#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
extract_rules.py —— 规则抽取：从 Markdown 结构里抽出实体与关系。

抽取信号与产物的对应（对应设计文档「实体与关系」一节）：

    # / ## / ### 标题            → outline 分类节点（只组织，不判断）
    段落 / 列表 / 表格 / 代码块   → card 卡片（可独立阅读的单元）
    加粗词 / 行内代码 / 领域词表   → entity 实体 + 出现位置
    判断题表（判断句｜是否成立｜边界） → claim 命题（自动抽取，带 expected）
    代码块里的 A → B → C 链      → card.chain（组成关系，供结构图示意）
    ```yaml kg:claim``` 标注块    → claim + 带条件的 edge（精标通道）

设计取舍：**规则只做结构可判定的部分**，语义判断（这条命题是否成立、边界在哪）
交给 `kg:claim` 标注块承载 —— 人工精标和 LLM 回填写进同一格式，脚本一视同仁。

命令行：
    python kg/src/extract_rules.py [--json]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kg_model as M
import parse_markdown as P

#: 表格表头里出现这些词，才认为它是「判断题表」
VERDICT_HEADERS = ("判断句", "说法", "命题")
TRUTH_HEADERS = ("是否成立", "成立吗", "对错")

BOLD_RE = re.compile(r"\*\*(.+?)\*\*")
CODE_SPAN_RE = re.compile(r"`([^`\n]{1,24})`")
ARROW_RE = re.compile(r"[ \t]*(?:→|->|=>)[ \t]*")


# --------------------------------------------------------------------------
# 1. 大纲与卡片
# --------------------------------------------------------------------------

def build_outline(doc: Dict[str, Any]) -> List[Dict[str, Any]]:
    """每个标题 → 一个 outline 节点。分类节点不参与判断，也不能下钻。"""
    nodes: List[Dict[str, Any]] = []
    for s in doc["sections"]:
        nodes.append({
            "id": s["id"],
            "type": "outline",
            "title": s["title"],
            "level": min(s["level"], 3),
            "parentId": M.outline_id(s["parentPath"]) if s["parentPath"] else None,
            "path": s["path"],
            "order": s["order"],
            "descendable": False,
        })
    return nodes


def card_summary(section: Dict[str, Any]) -> str:
    """卡片摘要：首段首句；没有段落时退回表格首行。"""
    blocks = P.prose_blocks(section)
    for b in blocks:
        if b["kind"] == "para" and len(b["text"]) >= 30:
            return P.summarize(b["text"])
    for b in blocks:
        if b["kind"] == "table" and b["rows"]:
            return P.summarize("；".join(b["rows"][0]))
    for b in blocks:
        if b["kind"] == "para":
            return P.summarize(b["text"])
    return f"{section['title']}（本卡以标注块为主）"


def build_cards(doc: Dict[str, Any]) -> tuple[List[Dict[str, Any]], Dict[str, str]]:
    """有可读内容的 section → 一张卡片。返回 (cards, sectionPath → cardId)。"""
    cards: List[Dict[str, Any]] = []
    by_section: Dict[str, str] = {}
    for s in doc["sections"]:
        blocks = P.prose_blocks(s)
        if not blocks:
            continue
        body_parts: List[str] = []
        for b in blocks:
            if b["kind"] == "para":
                body_parts.append(b["text"])
            elif b["kind"] == "list":
                body_parts.extend(f"- {item}" for item in b["items"])
            elif b["kind"] == "table":
                body_parts.append(b["text"])
            elif b["kind"] == "code":
                body_parts.append(f"```{b.get('lang', '')}\n{b['text']}\n```")
        cid = M.card_id(s["path"])
        by_section[s["path"]] = cid
        cards.append({
            "id": cid,
            "type": "card",
            "title": s["title"],
            "outlinePath": s["path"],
            "summary": card_summary(s),
            "body": "\n\n".join(body_parts).strip(),
            "chain": [],
            "crossLinks": [],
            "claimIds": [],
            "_section": s["path"],
        })
    return cards, by_section


# --------------------------------------------------------------------------
# 2. 实体抽取
# --------------------------------------------------------------------------

def _term_pattern(term: str) -> re.Pattern:
    """ASCII 词条要求词边界，避免 ReLU 命中 LeakyReLU 或 GELU 里的子串。"""
    escaped = re.escape(term)
    if re.match(r"^[\x00-\x7f]+$", term):
        return re.compile(rf"(?<![A-Za-z0-9_]){escaped}(?![A-Za-z0-9_])", re.IGNORECASE)
    return re.compile(escaped)


_PATTERNS = {name: _term_pattern(name) for name in M.TERM_LEXICON}
_PATTERNS.update({alias: _term_pattern(alias) for alias in M.ALIAS_TO_CANON if alias not in _PATTERNS})


def canonical_of(surface: str) -> Optional[str]:
    return M.ALIAS_TO_CANON.get(surface.strip().lower())


def build_entities(cards: List[Dict[str, Any]]) -> tuple[List[Dict[str, Any]], Dict[str, List[str]]]:
    """从卡片正文抽实体。返回 (entities, cardId → [entityId])。

    三类信号：领域词表命中 > 加粗 > 行内代码。未在词表里的按 concept 处理。
    """
    found: Dict[str, Dict[str, Any]] = {}
    per_card: Dict[str, List[str]] = {}

    def add(name: str, surface: str, card: Dict[str, Any], kind: Optional[str] = None) -> None:
        canon = canonical_of(name) or name.strip()
        if not canon or len(canon) > 24:
            return
        meta = M.TERM_LEXICON.get(canon, {})
        real_kind = meta.get("kind") or kind or "concept"
        eid = M.entity_id(real_kind, canon)
        node = found.setdefault(eid, {
            "id": eid,
            "type": "entity",
            "kind": real_kind,
            "canonicalName": canon,
            "aliases": list(meta.get("aliases", [])),
            "mentions": [],
        })
        node["aliases"] = sorted(set(node["aliases"]))
        if not any(m["cardId"] == card["id"] and m["surface"] == surface for m in node["mentions"]):
            node["mentions"].append({"cardId": card["id"], "surface": surface})
        per_card.setdefault(card["id"], [])
        if eid not in per_card[card["id"]]:
            per_card[card["id"]].append(eid)

    for card in cards:
        text = card["body"]
        # 1) 领域词表（含别名）
        for surface in sorted(_PATTERNS, key=len, reverse=True):
            for m in _PATTERNS[surface].finditer(text):
                add(m.group(0), m.group(0), card)
        # 2) 加粗与行内代码：图谱里最像"术语"的两种写法
        for m in BOLD_RE.finditer(text):
            add(m.group(1), m.group(1), card)
        for m in CODE_SPAN_RE.finditer(text):
            span = m.group(1).strip()
            if re.search(r"[A-Za-z\u4e00-\u9fa5]", span) and " " not in span:
                add(span, span, card, kind="formula" if re.search(r"[()\[\]=]", span) else None)

    entities = sorted(found.values(), key=lambda e: (-len(e["mentions"]), e["canonicalName"]))
    return entities, per_card


# --------------------------------------------------------------------------
# 3. 组件链（A → B → C）
# --------------------------------------------------------------------------

def extract_chains(cards: List[Dict[str, Any]], per_card_entities: Dict[str, List[str]]) -> List[str]:
    """代码块里的箭头链 → 卡片上的 chain 字段（组成关系，用于结构图示意）。

    只认领域词表里有的组件；认不出来的记 warning，不硬造实体。
    """
    warnings: List[str] = []
    by_id = {c["id"]: c for c in cards}
    for card in cards:
        for chunk in re.findall(r"```[a-z]*\n(.*?)```", card["body"], re.S):
            if "→" not in chunk:
                continue
            parts = [p.strip() for p in ARROW_RE.split(chunk.strip()) if p.strip()]
            if len(parts) < 2:
                continue
            ids: List[str] = []
            for part in parts:
                canon = canonical_of(part)
                if not canon:
                    warnings.append(f"组件链中的 `{part}` 未在领域词表中，未生成实体（{card['id']}）")
                    continue
                meta = M.TERM_LEXICON.get(canon, {})
                ids.append(M.entity_id(meta.get("kind", "concept"), canon))
            if len(ids) >= 2:
                by_id[card["id"]]["chain"] = ids
    return warnings


# --------------------------------------------------------------------------
# 4. 判断题表 → 命题
# --------------------------------------------------------------------------

def _truth_of(cell: str) -> Optional[bool]:
    cell = cell.strip()
    if cell.startswith("不成立") or cell.startswith("错") or cell.startswith("否"):
        return False
    if cell.startswith("成立") or cell.startswith("对") or cell.startswith("是"):
        return True
    return None


def extract_table_claims(doc: Dict[str, Any], cards_by_section: Dict[str, str],
                         cards: List[Dict[str, Any]]) -> tuple[List[Dict[str, Any]], List[str]]:
    """判断题表 → claim。表头三列：判断句 / 是否成立 / 边界。"""
    claims: List[Dict[str, Any]] = []
    warnings: List[str] = []
    seq = 0
    for s in doc["sections"]:
        for block in s["blocks"]:
            if block["kind"] != "table":
                continue
            header = block["header"]
            if not any(h in "".join(header) for h in VERDICT_HEADERS):
                continue
            if not any(h in "".join(header) for h in TRUTH_HEADERS):
                continue
            idx_stmt = next(i for i, h in enumerate(header) if any(k in h for k in VERDICT_HEADERS))
            idx_truth = next(i for i, h in enumerate(header) if any(k in h for k in TRUTH_HEADERS))
            idx_bound = next((i for i, h in enumerate(header) if "边界" in h or "条件" in h), None)
            card_id = cards_by_section.get(s["path"])
            if not card_id:
                continue
            for row in block["rows"]:
                if idx_stmt >= len(row) or idx_truth >= len(row):
                    continue
                statement = row[idx_stmt].strip()
                expected = _truth_of(row[idx_truth])
                if not statement or expected is None:
                    warnings.append(f"判断题表跳过一行（无法判定成立与否）：{row}")
                    continue
                seq += 1
                boundary = row[idx_bound].strip() if idx_bound is not None and idx_bound < len(row) else ""
                claims.append({
                    "id": f"clm.arch.qa.q{seq}.{M.short_hash(statement)}",
                    "type": "claim",
                    "statement": statement,
                    "conditions": [f"边界：{boundary}"] if boundary else ["边界：未在表格中标注"],
                    "evidence": f"判断题表：{statement} —— {row[idx_truth].strip()}",
                    "expected": expected,
                    "status": "unprobed",
                    "misconception": None,
                    "error_kinds": [{
                        "kind": "semantic",
                        "why": "表格判断题未标注错点类型，按语义依赖处理",
                    }],
                    "block_edges": [],
                    "_section": s["path"],
                    "_card": card_id,
                    "_auto": True,
                })
    return claims, warnings


# --------------------------------------------------------------------------
# 5. 标注块 → 命题 + 边
# --------------------------------------------------------------------------

def extract_explicit(doc: Dict[str, Any], cards_by_section: Dict[str, str]) -> tuple[List[Dict[str, Any]], List[str]]:
    claims: List[Dict[str, Any]] = []
    warnings: List[str] = []
    for kb in doc["kg_blocks"]:
        if kb["kind"] != "claim":
            warnings.append(f"标注块类型 `{kb['kind']}` 尚未支持，已跳过")
            continue
        data = kb["data"]
        statement = str(data.get("statement") or "").strip()
        if not statement:
            warnings.append("标注块缺少 statement，已跳过")
            continue
        raw_id = str(data.get("id") or "").strip()
        base_id = raw_id if raw_id.startswith("clm.") else f"clm.{M.slugify(raw_id or statement, 'claim')}"
        cid = base_id if re.match(r"^clm\..+\.[0-9a-f]{4,8}$", base_id) else f"{base_id}.{M.short_hash(statement)}"
        section = kb["sectionPath"]
        card_id = cards_by_section.get(section)
        if not card_id:
            warnings.append(f"命题 {cid} 所在章节没有卡片，已挂到根卡")
            card_id = next(iter(cards_by_section.values()), None)
        expected = data.get("expected")
        if not isinstance(expected, bool):
            warnings.append(f"命题 {cid} 缺少 expected（true/false），默认按不成立处理")
            expected = False
        conditions = [str(c) for c in M.parse_list_value(data, "conditions")]
        error_kinds = []
        for item in M.parse_list_value(data, "error_kinds"):
            if isinstance(item, dict) and item.get("kind"):
                error_kinds.append({"kind": item["kind"], "why": str(item.get("why") or "")})
        if not error_kinds and data.get("error_kind"):
            error_kinds.append({
                "kind": str(data["error_kind"]),
                "why": str(data.get("error_why") or "标注块未给出错点说明"),
            })
        if not error_kinds:
            error_kinds = [{"kind": "semantic", "why": "未标注错点类型，按语义依赖处理"}]
        claims.append({
            "id": cid,
            "type": "claim",
            "statement": statement,
            "conditions": conditions or ["未标注条件"],
            "evidence": str(data.get("evidence") or "").strip(),
            "expected": expected,
            "status": str(data.get("status") or "unprobed").strip(),
            "misconception": (str(data["misconception"]).strip() if data.get("misconception") else None),
            "error_kinds": error_kinds,
            "explain": (str(data["explain"]).strip() if data.get("explain") else None),
            "block_edges": [e for e in M.parse_list_value(data, "edges") if isinstance(e, dict)],
            "_section": section,
            "_card": card_id,
            "_auto": False,
        })
    return claims, warnings


# --------------------------------------------------------------------------
# 入口
# --------------------------------------------------------------------------

def extract(path: Optional[Path] = None) -> Dict[str, Any]:
    doc = P.parse_file(path or M.SOURCE_MD)
    outline = build_outline(doc)
    cards, cards_by_section = build_cards(doc)
    entities, per_card = build_entities(cards)
    warnings: List[str] = []
    warnings += extract_chains(cards, per_card)

    table_claims, w1 = extract_table_claims(doc, cards_by_section, cards)
    explicit_claims, w2 = extract_explicit(doc, cards_by_section)
    warnings += w1 + w2

    return {
        "doc": doc,
        "outline": outline,
        "cards": cards,
        "entities": entities,
        "claims": explicit_claims + table_claims,
        "card_entities": per_card,
        "warnings": warnings,
    }


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    result = extract()
    if "--json" in sys.argv:
        slim = {k: v for k, v in result.items() if k != "doc"}
        print(json.dumps(slim, ensure_ascii=False, indent=2))
        return 0
    print(f"outline {len(result['outline'])} / card {len(result['cards'])} / "
          f"entity {len(result['entities'])} / claim {len(result['claims'])}")
    print("-" * 60)
    for c in result["claims"][:8]:
        print(f"  {'✔' if c['expected'] else '✘'} {c['id']}")
    print(f"  …… 共 {len(result['claims'])} 条")
    for w in result["warnings"]:
        print("  ! " + w)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
