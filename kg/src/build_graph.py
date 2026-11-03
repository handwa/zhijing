#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_graph.py —— 组装图谱：把抽取结果拼成一份合法、可校验的 graph.json。

这一步负责「结构纪律」，也就是设计文档里那些容易被写崩的约定：

    1. 命题双视图共享同一条记录（breadth / depth 只是同一实体的两个渲染面）
    2. 每条命题有且只有一个主归属卡（cardId），多入口只做从属引用
    3. 每张卡的交叉链接封顶 CROSSLINK_MAX 条并按 weight 降序（防止退化成节点图）
    4. 深度图谱只能从叶子卡片下钻 —— navigation 里绝不出现 claim
    5. 回写只回写 4 个摘要字段（probeState 里的 attempts/correct 标记为「仅深度侧」）

命令行：
    python kg/src/build_graph.py [--out PATH] [--quiet]
"""

from __future__ import annotations

import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

import extract_rules as X
import kg_model as M
import parse_markdown as P

DEFAULT_QUESTION = "这个说法成立吗？"


# --------------------------------------------------------------------------
# 命题：补齐 breadth / depth 两个渲染面
# --------------------------------------------------------------------------

def _first_paragraph(card: Optional[Dict[str, Any]], limit: int = 120) -> str:
    if not card:
        return ""
    for chunk in card["body"].split("\n\n"):
        chunk = chunk.strip()
        if chunk and not chunk.startswith("```") and not chunk.startswith("|"):
            text = P.summarize(chunk, limit)
            if text:
                return text
    return card["summary"]


def build_claim(claim: Dict[str, Any], cards: Dict[str, Dict[str, Any]], outline: Dict[str, Dict[str, Any]]
                ) -> Dict[str, Any]:
    card = cards.get(claim["_card"])
    section = claim["_section"]
    status = claim["status"] if claim["status"] in M.PROBE_STATES else "unprobed"
    misconceptions: List[str] = []
    if claim.get("misconception"):
        misconceptions.append(claim["misconception"])
    confidence = {"mastered": 0.85, "verified": 0.75, "confused": 0.3, "unprobed": 0.0}[status]

    explanation = claim.get("explain")
    if not explanation:
        head = "这个说法成立。" if claim["expected"] else "这个说法不成立。"
        evidence = str(claim.get("evidence") or "").strip()
        if evidence:
            explanation = head + "原文依据：" + evidence
        else:
            tail = "；".join(claim["conditions"][:2]) if claim["conditions"] else ""
            explanation = head + ("判断依据：" + tail if tail else "")

    return {
        "id": claim["id"],
        "type": "claim",
        "statement": claim["statement"],
        "conditions": claim["conditions"],
        "evidence": claim["evidence"],
        "breadth": {
            "outlinePath": section or "",
            "cardId": claim["_card"] or "",
            "cardSummary": card["summary"] if card else "",
            "leafContent": _first_paragraph(card),
        },
        "depth": {
            "edges": [],
            "judgementPanel": {
                "question": DEFAULT_QUESTION,
                "expected": claim["expected"],
                "reveal": {
                    "explanation": explanation,
                    "errorKinds": claim["error_kinds"],
                    "misconception": claim.get("misconception") or "",
                },
            },
            "probeState": {
                "probe_status": status,
                "last_probed_at": None,
                "misconceptions": misconceptions,
                "confidence": confidence,
                "attempts": 0,
                "correct": 0,
            },
        },
        "_block_edges": claim.get("block_edges", []),
        "_auto": claim.get("_auto", False),
    }


# --------------------------------------------------------------------------
# 边：解析目标、补条件、去重
# --------------------------------------------------------------------------

def resolve_target(ref: Any, claims_by_id: Dict[str, Dict[str, Any]]) -> tuple[Optional[str], List[str]]:
    """标注块里的 `to` 允许省略 hash 后缀，这里做前缀解析。"""
    ref = str(ref or "").strip()
    if not ref:
        return None, ["边缺少 to 字段，已跳过"]
    if ref in claims_by_id:
        return ref, []
    candidates = [cid for cid in claims_by_id if cid == ref or cid.startswith(ref + ".")]
    if len(candidates) == 1:
        return candidates[0], []
    if len(candidates) > 1:
        return sorted(candidates)[0], [f"边目标 `{ref}` 命中多条命题，已取 {sorted(candidates)[0]}"]
    return None, [f"边目标 `{ref}` 不存在，已跳过（可能是命题被改名）"]


def build_edges(claims: List[Dict[str, Any]]) -> tuple[List[Dict[str, Any]], List[str], Dict[str, List[str]]]:
    by_id = {c["id"]: c for c in claims}
    edges: List[Dict[str, Any]] = []
    warnings: List[str] = []
    seen: set = set()
    edges_of: Dict[str, List[str]] = {}

    for claim in claims:
        for raw in claim["_block_edges"]:
            to_id, warns = resolve_target(raw.get("to"), by_id)
            warnings.extend(warns)
            if not to_id:
                continue
            if to_id == claim["id"]:
                warnings.append(f"命题 {claim['id']} 的边指向自己，已跳过")
                continue
            edge_type = str(raw.get("type") or "depends_on").strip()
            if edge_type not in M.EDGE_TYPES:
                warnings.append(f"未知边类型 `{edge_type}`，按 depends_on 处理")
                edge_type = "depends_on"
            premise = str(raw.get("premise") or "").strip()
            boundary = str(raw.get("boundary") or "").strip()
            if not premise or not boundary:
                warnings.append(f"边 {claim['id']} → {to_id} 缺少 premise/boundary，已按空值补全（校验会报错）")
            kind = str(raw.get("kind") or "semantic").strip()
            if kind not in M.CONDITION_KINDS:
                warnings.append(f"未知条件类型 `{kind}`，按 semantic 处理")
                kind = "semantic"
            key = (claim["id"], to_id, edge_type)
            if key in seen:
                continue
            seen.add(key)
            eid = M.edge_id(claim["id"], to_id, edge_type)
            weight = raw.get("weight", 0.5)
            try:
                weight = max(0.0, min(1.0, float(weight)))
            except (TypeError, ValueError):
                weight = 0.5
            edges.append({
                "id": eid,
                "from": claim["id"],
                "to": to_id,
                "type": edge_type,
                "condition": {
                    "kind": kind,
                    "premise": premise,
                    "boundary": boundary,
                    "counterexample": str(raw.get("counterexample") or "").strip(),
                },
                "evidence": str(raw.get("evidence") or claim["evidence"] or "").strip(),
                "weight": round(weight, 2),
            })
            edges_of.setdefault(claim["id"], []).append(eid)
            edges_of.setdefault(to_id, []).append(eid)
    return edges, warnings, edges_of


# --------------------------------------------------------------------------
# 交叉链接：按共享实体计算，封顶 5 条并排序
# --------------------------------------------------------------------------

def build_crosslinks(cards: List[Dict[str, Any]], card_entities: Dict[str, List[str]],
                     entities: Dict[str, Dict[str, Any]]) -> None:
    ids = [c["id"] for c in cards]
    by_id = {c["id"]: c for c in cards}
    sets = {cid: set(card_entities.get(cid, [])) for cid in ids}
    outline = {c["id"]: c["outlinePath"] for c in cards}

    for cid in ids:
        links: List[Dict[str, Any]] = []
        for other in ids:
            if other == cid:
                continue
            shared = sets[cid] & sets[other]
            if not shared:
                continue
            union = len(sets[cid]) + len(sets[other]) - len(shared)
            weight = len(shared) / math.sqrt(max(1, len(sets[cid]) * len(sets[other])))
            if outline[cid].split("-")[0] == outline[other].split("-")[0]:
                weight += 0.03          # 同一章内轻微加权，便于读者就近横跳
            names = [entities[e]["canonicalName"] for e in sorted(shared) if e in entities]
            links.append({
                "target": other,
                "targetKind": "card",
                "label": by_id[other]["title"],
                "weight": round(min(1.0, weight), 2),
                "reason": "共享实体：" + "、".join(names[:4]),
            })
        links.sort(key=lambda x: (-x["weight"], x["label"]))
        by_id[cid]["crossLinks"] = links[: M.CROSSLINK_MAX]


# --------------------------------------------------------------------------
# 导航树：只允许 outline / card，深度必须下钻
# --------------------------------------------------------------------------

def build_navigation(outline: List[Dict[str, Any]], cards: List[Dict[str, Any]],
                     claims: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    card_by_section: Dict[str, Dict[str, Any]] = {c["outlinePath"]: c for c in cards}
    claims_by_section: Dict[str, int] = {}
    for cl in claims:
        path = cl["breadth"]["outlinePath"]
        claims_by_section[path] = claims_by_section.get(path, 0) + 1

    nodes: Dict[str, Dict[str, Any]] = {}
    for o in outline:
        nodes[o["path"]] = {
            "title": o["title"],
            "kind": "outline",
            "id": o["id"],
            "children": [],
            "claimCount": claims_by_section.get(o["path"], 0),
        }
    roots: List[Dict[str, Any]] = []
    for o in sorted(outline, key=lambda x: (x["level"], x["order"])):
        node = nodes[o["path"]]
        parent_path = o["parentId"].split(".", 1)[1] if o["parentId"] else None
        if parent_path and parent_path in nodes:
            nodes[parent_path]["children"].append(node)
        else:
            roots.append(node)

    # 卡片挂在所属小节下，作为该节点的可读内容与下钻入口
    for path, card in card_by_section.items():
        if path not in nodes:
            continue
        nodes[path]["children"].insert(0, {
            "title": card["title"],
            "kind": "card",
            "id": card["id"],
            "claimCount": len(card["claimIds"]),
        })
    return roots


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------

def build(path: Optional[Path] = None) -> Dict[str, Any]:
    extracted = X.extract(path)
    warnings: List[str] = list(extracted["warnings"])

    cards_by_id = {c["id"]: c for c in extracted["cards"]}
    outline_by_path = {o["path"]: o for o in extracted["outline"]}
    entities_by_id = {e["id"]: e for e in extracted["entities"]}

    # 1) 命题（去重：同一句断言只留一条，后出现的边合并进先出现的记录）
    seen_statements: Dict[str, Dict[str, Any]] = {}
    raw_claims: List[Dict[str, Any]] = []
    for raw in extracted["claims"]:
        key = M.normalize(raw["statement"])
        prior = seen_statements.get(key)
        if prior is not None:
            prior["block_edges"] = list(prior.get("block_edges", [])) + list(raw.get("block_edges", []))
            warnings.append(f"命题重复：{raw['id']} 与 {prior['id']} 断言相同，已并入 {prior['id']}")
            continue
        seen_statements[key] = raw
        raw_claims.append(raw)
    claims = [build_claim(raw, cards_by_id, outline_by_path) for raw in raw_claims]

    # 2) 主归属：一张叶子卡上挂多条命题没问题，但一条命题只能属于一张卡
    for cl in claims:
        cid = cl["breadth"]["cardId"]
        if cid in cards_by_id:
            cards_by_id[cid]["claimIds"].append(cl["id"])
        else:
            warnings.append(f"命题 {cl['id']} 的归属卡 {cid or '(空)'} 不存在")

    # 3) 边（此时才把 block 里的边挂到 depth.edges）
    edges, edge_warns, edges_of = build_edges(claims)
    warnings.extend(edge_warns)
    for cl in claims:
        cl["depth"]["edges"] = sorted(set(edges_of.get(cl["id"], [])))

    # 4) 交叉链接
    build_crosslinks(extracted["cards"], extracted["card_entities"], entities_by_id)

    # 5) 导航
    navigation = build_navigation(extracted["outline"], extracted["cards"], claims)

    # 6) 清理内部字段
    for cl in claims:
        cl.pop("_block_edges", None)
        cl.pop("_auto", None)
    for card in extracted["cards"]:
        card.pop("_section", None)

    graph: Dict[str, Any] = {
        "meta": {
            "schemaVersion": M.SCHEMA_VERSION,
            "source": str((path or M.SOURCE_MD)).replace("\\", "/").split("zhijing/")[-1],
            "generator": "kg/src/build_graph.py",
            "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
            "counts": {
                "outline": len(extracted["outline"]),
                "card": len(extracted["cards"]),
                "claim": len(claims),
                "entity": len(extracted["entities"]),
                "edge": len(edges),
            },
            "warnings": warnings,
        },
        "outline": extracted["outline"],
        "cards": extracted["cards"],
        "claims": claims,
        "entities": extracted["entities"],
        "edges": edges,
        "navigation": navigation,
    }

    _diff_against_previous(graph, warnings)
    return graph


def _diff_against_previous(graph: Dict[str, Any], warnings: List[str]) -> None:
    """与上一版 graph.json 比对 ID，把「改名」暴露出来（id-spec 要求 ID 当发布物对待）。"""
    if not M.GRAPH_JSON.exists():
        return
    try:
        old = json.loads(M.GRAPH_JSON.read_text(encoding="utf-8"))
    except Exception:
        return
    old_ids = {c["id"] for c in old.get("claims", [])}
    new_ids = {c["id"] for c in graph["claims"]}
    added, removed = sorted(new_ids - old_ids), sorted(old_ids - new_ids)
    if added:
        graph["meta"]["warnings"].append(f"新增命题 ID {len(added)} 条：{', '.join(added[:5])}"
                                         + ("…" if len(added) > 5 else ""))
    if removed:
        graph["meta"]["warnings"].append(f"消失命题 ID {len(removed)} 条（若不是有意删除，说明有命题被改名）："
                                         + f"{', '.join(removed[:5])}" + ("…" if len(removed) > 5 else ""))


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    out = M.GRAPH_JSON
    if "--out" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--out") + 1])
    graph = build()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(graph, ensure_ascii=False, indent=2), encoding="utf-8")
    counts = graph["meta"]["counts"]
    if "--quiet" not in sys.argv:
        print(f"已生成 {out.relative_to(M.REPO_ROOT)}")
        print(f"  outline {counts['outline']}  card {counts['card']}  claim {counts['claim']}  "
              f"entity {counts['entity']}  edge {counts['edge']}")
        for w in graph["meta"]["warnings"]:
            print("  ! " + w)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
