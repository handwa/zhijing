#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
validate_graph.py —— 把设计文档的「核心约束」变成机器可查的 8 条校验。

逐条对应 docs/knowledge-graph-design(1).md 第五节「核心约束」：

    1. 命题 ID 是唯一主键（单一真相源）        → 规则 1
    2. 深度图谱是下钻，不是并列结构            → 规则 8
    3. 一个命题只有一个主归属卡                → 规则 2
    4. 回写是摘要而非明细                      → 规则 7（状态四态 + 回写字段白名单）
    5. 回写走派生（广度侧不存状态）            → 规则 7（广度节点不得自带状态字段）
    6. 区分「撞错后纠正」与「仍易错」          → 规则 7（confused 必须带误解说明）
    7. 广度侧交叉链接封顶 3–5 条并排序         → 规则 5
    8. 深度侧条件不在第一屏展开                → 规则 6

另外三条结构完整性检查（边端点、边必须带条件、大纲树闭合）也一并纳入。

零依赖：不引 jsonschema，检查逻辑就是本文件；若环境里恰好装了 jsonschema，
会额外跑一遍 schema，双重保险。

命令行：
    python kg/src/validate_graph.py [--graph PATH] [--json]
退出码：0 全部通过；1 存在失败项（可直接用于 CI / 提交前检查）
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kg_model as M

CLAIM_RE = re.compile(r"^clm\.[a-z0-9][a-z0-9._-]*\.[0-9a-f]{4,8}$")


class Report:
    """收集校验结果：每条规则一行，失败项带具体对象 ID，便于直接定位语料。"""

    def __init__(self) -> None:
        self.checks: List[Dict[str, Any]] = []

    def add(self, no: str, title: str, failures: List[str], note: str = "") -> None:
        self.checks.append({
            "no": no,
            "title": title,
            "passed": not failures,
            "failures": failures,
            "note": note,
        })

    @property
    def ok(self) -> bool:
        return all(c["passed"] for c in self.checks)


def load(path: Optional[Path]) -> Dict[str, Any]:
    return json.loads(Path(path or M.GRAPH_JSON).read_text(encoding="utf-8"))


# --------------------------------------------------------------------------
# 规则 1：ID 唯一且格式合法
# --------------------------------------------------------------------------

def check_ids(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    seen: Dict[str, str] = {}
    for bucket in ("outline", "cards", "claims", "entities", "edges"):
        for node in g.get(bucket, []):
            nid = node.get("id", "")
            if not M.is_valid_id(nid):
                failures.append(f"[{bucket}] ID 格式非法：{nid!r}（规范见 schema/id-spec.md）")
            if nid in seen:
                failures.append(f"ID 重复：{nid} 同时出现在 {seen[nid]} 与 {bucket}")
            seen[nid] = bucket
    # 命题 ID 的哈希必须由 statement 推出，防止手改 ID 造成语义漂移
    for cl in g.get("claims", []):
        expect_suffix = M.short_hash(cl.get("statement", ""))
        if not str(cl.get("id", "")).endswith(expect_suffix):
            failures.append(f"命题 {cl.get('id')} 的哈希与 statement 不匹配（应为 …{expect_suffix}）")
    r.add("1", "命题 ID 全库唯一、格式合法、哈希与正文一致", failures)


# --------------------------------------------------------------------------
# 规则 2：一个命题只有一个主归属卡
# --------------------------------------------------------------------------

def check_primary_card(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    cards = {c["id"]: c for c in g.get("cards", [])}
    owner: Dict[str, List[str]] = {}
    for card in g.get("cards", []):
        for cid in card.get("claimIds", []):
            owner.setdefault(cid, []).append(card["id"])

    for cl in g.get("claims", []):
        cid = cl.get("breadth", {}).get("cardId")
        if cid not in cards:
            failures.append(f"命题 {cl['id']} 的主归属卡 {cid!r} 不存在")
            continue
        if cl["id"] not in cards[cid].get("claimIds", []):
            failures.append(f"命题 {cl['id']} 未登记在归属卡 {cid} 的 claimIds 中")
        if len(owner.get(cl["id"], [])) != 1:
            failures.append(f"命题 {cl['id']} 归属不唯一：出现在 {owner.get(cl['id'], [])}")
        path = cl.get("breadth", {}).get("outlinePath")
        if path != cards[cid].get("outlinePath"):
            failures.append(f"命题 {cl['id']} 的 outlinePath 与归属卡不一致（{path} ≠ {cards[cid].get('outlinePath')}）")
    r.add("2", "每个命题有且只有一个主归属卡", failures)


# --------------------------------------------------------------------------
# 规则 3 / 4：边端点存在、边必须带条件
# --------------------------------------------------------------------------

def check_edges(g: Dict[str, Any], r: Report) -> None:
    claim_ids = {c["id"] for c in g.get("claims", [])}
    dangling: List[str] = []
    for e in g.get("edges", []):
        for side in ("from", "to"):
            if e.get(side) not in claim_ids:
                dangling.append(f"边 {e.get('id')} 的 {side}={e.get(side)!r} 指向不存在的命题")
        if e.get("from") == e.get("to"):
            dangling.append(f"边 {e.get('id')} 是自环")
    r.add("3", "所有边的端点都存在，无悬空边/自环", dangling)

    bad: List[str] = []
    for e in g.get("edges", []):
        cond = e.get("condition") or {}
        if not str(cond.get("premise") or "").strip():
            bad.append(f"边 {e.get('id')} 缺少 premise（成立前提）")
        if not str(cond.get("boundary") or "").strip():
            bad.append(f"边 {e.get('id')} 缺少 boundary（边界），等于没有条件")
        if cond.get("kind") not in M.CONDITION_KINDS:
            bad.append(f"边 {e.get('id')} 的 kind={cond.get('kind')!r} 不在五类条件内")
    # depth.edges 必须与 edges 表对得上
    edge_ids = {e["id"] for e in g.get("edges", [])}
    for cl in g.get("claims", []):
        for eid in cl.get("depth", {}).get("edges", []):
            if eid not in edge_ids:
                bad.append(f"命题 {cl['id']} 引用了不存在的边 {eid}")
    r.add("4", "每条边都带条件（premise + boundary + 五类 kind）", bad)


# --------------------------------------------------------------------------
# 规则 5：交叉链接封顶且有序
# --------------------------------------------------------------------------

def check_crosslinks(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    card_ids = {c["id"] for c in g.get("cards", [])}
    for card in g.get("cards", []):
        links = card.get("crossLinks", [])
        if len(links) > M.CROSSLINK_MAX:
            failures.append(f"卡片 {card['id']} 交叉链接 {len(links)} 条，超过上限 {M.CROSSLINK_MAX}（会退化成节点图）")
        weights = [l.get("weight", 0) for l in links]
        if weights != sorted(weights, reverse=True):
            failures.append(f"卡片 {card['id']} 的交叉链接未按 weight 降序排列")
        for link in links:
            if link.get("target") not in card_ids:
                failures.append(f"卡片 {card['id']} 的交叉链接指向不存在的卡片 {link.get('target')}")
            if link.get("target") == card["id"]:
                failures.append(f"卡片 {card['id']} 的交叉链接指向自己")
    r.add("5", f"交叉链接每卡 ≤{M.CROSSLINK_MAX} 条且按权重降序", failures)


# --------------------------------------------------------------------------
# 规则 6：第一屏不铺开条件
# --------------------------------------------------------------------------

def check_first_screen(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    cards = {c["id"]: c for c in g.get("cards", [])}
    for cl in g.get("claims", []):
        breadth = cl.get("breadth", {})
        extra = set(breadth) - set(M.BREADTH_FIELDS)
        if extra:
            failures.append(f"命题 {cl['id']} 的 breadth 出现白名单之外的字段：{sorted(extra)}")
        panel = cl.get("depth", {}).get("judgementPanel", {})
        reveal = panel.get("reveal", {})
        card = cards.get(breadth.get("cardId"))
        if not card:
            continue
        body = card.get("body", "")
        explain = str(reveal.get("explanation") or "").strip()
        if len(explain) >= 8 and explain in body:
            failures.append(f"命题 {cl['id']} 的判定解释出现在卡片 {card['id']} 正文里，第一屏就泄露答案")
        for e in g.get("edges", []):
            if cl["id"] not in (e.get("from"), e.get("to")):
                continue
            ce = str((e.get("condition") or {}).get("counterexample") or "").strip()
            if len(ce) >= 6 and ce in body:
                failures.append(f"边 {e['id']} 的反例出现在卡片正文里，等于提前给出边界")
        if not panel.get("question"):
            failures.append(f"命题 {cl['id']} 缺判断面板问题（第一屏唯一文案）")
        if not isinstance(panel.get("expected"), bool):
            failures.append(f"命题 {cl['id']} 的判断面板缺少 expected 标准答案")
    r.add("6", "第一屏只有「命题 + 两个按钮」，条件/答案不出现在广度正文", failures)


# --------------------------------------------------------------------------
# 规则 7：状态四态 + 回写字段
# --------------------------------------------------------------------------

def check_states(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    for cl in g.get("claims", []):
        probe = cl.get("depth", {}).get("probeState", {})
        status = probe.get("probe_status")
        if status not in M.PROBE_STATES:
            failures.append(f"命题 {cl['id']} 的 probe_status={status!r} 不在四态内")
        if not isinstance(probe.get("misconceptions"), list):
            failures.append(f"命题 {cl['id']} 的 misconceptions 不是列表")
        if status == "confused" and not probe.get("misconceptions"):
            failures.append(f"命题 {cl['id']} 标记为易混淆却没有留下误解说明（无法回写易错点注记）")
        if status in ("mastered", "verified") and not probe.get("confidence"):
            failures.append(f"命题 {cl['id']} 状态为 {status} 但 confidence 为 0")
        extra = set(probe) - set(M.WRITEBACK_FIELDS) - {"attempts", "correct"}
        if extra:
            failures.append(f"命题 {cl['id']} 的 probeState 出现非约定字段：{sorted(extra)}")
    # 广度侧（outline / card）不得自带判断状态 —— 回写必须走派生
    for node in g.get("outline", []) + g.get("cards", []):
        leaked = set(node) & set(M.WRITEBACK_FIELDS)
        if leaked:
            failures.append(f"{node.get('id')} 自带状态字段 {sorted(leaked)}，违背「广度侧只派生不存储」")
    r.add("7", "状态落在四态内，广度侧不存储状态（回写走派生）", failures)


# --------------------------------------------------------------------------
# 规则 8：导航里没有深度入口
# --------------------------------------------------------------------------

def check_navigation(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    claim_ids = {c["id"] for c in g.get("claims", [])}
    card_ids = {c["id"] for c in g.get("cards", [])}
    outline_ids = {c["id"] for c in g.get("outline", [])}

    def walk(items: List[Dict[str, Any]], depth: int, root_level: bool = True) -> None:
        for item in items:
            if item.get("kind") not in ("outline", "card"):
                failures.append(f"导航项 {item.get('title')} 的 kind={item.get('kind')!r}，深度图谱不允许并列入口")
            if item.get("id") in claim_ids:
                failures.append(f"导航项 {item.get('title')} 直接指向命题，深度必须从叶子卡片下钻")
            if item.get("kind") == "card" and item.get("id") not in card_ids:
                failures.append(f"导航里的卡片 {item.get('id')} 不存在")
            if item.get("kind") == "outline" and item.get("id") not in outline_ids:
                failures.append(f"导航里的大纲节点 {item.get('id')} 不存在")
            if item.get("kind") == "card" and item.get("children"):
                failures.append(f"卡片 {item.get('id')} 不应再有子节点")
            walk(item.get("children", []), depth + 1, False)

    walk(g.get("navigation", []), 0)

    for node in g.get("outline", []):
        if node.get("descendable") is not False:
            failures.append(f"大纲节点 {node['id']} 的 descendable 不是 false（分类节点不可下钻）")
        if node.get("level", 1) < 1 or node.get("level", 1) > 3:
            failures.append(f"大纲节点 {node['id']} 的 level={node.get('level')}，只允许 1–3")
        parent = node.get("parentId")
        if parent and parent not in outline_ids:
            failures.append(f"大纲节点 {node['id']} 的父节点 {parent} 不存在")

    # 大纲树必须闭合：每个 outline 都要能在导航里被走到
    reachable: set = set()

    def collect(items: List[Dict[str, Any]]) -> None:
        for item in items:
            if item.get("kind") == "outline":
                reachable.add(item.get("id"))
            collect(item.get("children", []))

    collect(g.get("navigation", []))
    missing = sorted(outline_ids - reachable)
    if missing:
        failures.append(f"有 {len(missing)} 个大纲节点不在导航树里：{missing[:5]}")
    r.add("8", "导航只有广度结构，深度图谱只能下钻", failures)


# --------------------------------------------------------------------------
# 结构完整性（大纲闭合 / 卡片归属 / 字段齐备）
# --------------------------------------------------------------------------

def check_structure(g: Dict[str, Any], r: Report) -> None:
    failures: List[str] = []
    outline_ids = {c["id"] for c in g.get("outline", [])}
    for card in g.get("cards", []):
        if card.get("outlinePath") is None:
            failures.append(f"卡片 {card['id']} 缺少 outlinePath")
        if M.outline_id(card.get("outlinePath", "")) not in outline_ids:
            failures.append(f"卡片 {card['id']} 的 outlinePath={card.get('outlinePath')!r} 找不到对应大纲节点")
        if len(card.get("summary", "")) < 10:
            failures.append(f"卡片 {card['id']} 的 summary 太短（广度侧需要一句话可读摘要）")
        for link in card.get("crossLinks", []):
            if link.get("targetKind") not in ("card", "entity", "claim"):
                failures.append(f"卡片 {card['id']} 的交叉链接 targetKind 非法")
    for cl in g.get("claims", []):
        if not cl.get("conditions"):
            failures.append(f"命题 {cl['id']} 没有任何条件（深度图谱必须有边界）")
        if not str(cl.get("evidence") or "").strip():
            failures.append(f"命题 {cl['id']} 缺少 evidence，无法溯源到原文")
        if not CLAIM_RE.match(cl.get("id", "")):
            failures.append(f"命题 {cl['id']} 不匹配 claim ID 正则")
    for e in g.get("edges", []):
        if not str(e.get("evidence") or "").strip():
            failures.append(f"边 {e['id']} 缺少 evidence")
    r.add("9", "结构完整：归属闭合、条件齐备、证据可溯源", failures)


def check_schema_optional(g: Dict[str, Any], r: Report) -> None:
    try:
        import jsonschema  # type: ignore
    except Exception:
        r.add("10", "JSON Schema 校验", [], note="未安装 jsonschema，跳过（核心 9 条已覆盖）")
        return
    schema = json.loads((M.KG_ROOT / "schema" / "graph.schema.json").read_text(encoding="utf-8"))
    validator = jsonschema.Draft7Validator(schema)
    failures = [
        f"{'/'.join(str(p) for p in err.absolute_path)}: {err.message}"
        for err in sorted(validator.iter_errors(g), key=lambda e: list(e.absolute_path))
    ]
    r.add("10", "JSON Schema 校验", failures)


# --------------------------------------------------------------------------
# 入口
# --------------------------------------------------------------------------

def validate(graph: Dict[str, Any]) -> Report:
    r = Report()
    check_ids(graph, r)
    check_primary_card(graph, r)
    check_edges(graph, r)
    check_crosslinks(graph, r)
    check_first_screen(graph, r)
    check_states(graph, r)
    check_navigation(graph, r)
    check_structure(graph, r)
    check_schema_optional(graph, r)
    return r


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    path = None
    if "--graph" in sys.argv:
        path = Path(sys.argv[sys.argv.index("--graph") + 1])
    graph = load(path)
    report = validate(graph)

    if "--json" in sys.argv:
        print(json.dumps({
            "ok": report.ok,
            "counts": graph.get("meta", {}).get("counts", {}),
            "checks": report.checks,
        }, ensure_ascii=False, indent=2))
        return 0 if report.ok else 1

    print(f"图谱：{path or M.GRAPH_JSON}")
    counts = graph.get("meta", {}).get("counts", {})
    print(f"规模：outline {counts.get('outline')} / card {counts.get('card')} / claim {counts.get('claim')} / "
          f"entity {counts.get('entity')} / edge {counts.get('edge')}")
    print("=" * 72)
    for c in report.checks:
        flag = "PASS" if c["passed"] else "FAIL"
        print(f"[{flag}] {c['no']}. {c['title']}")
        if c.get("note"):
            print(f"       · {c['note']}")
        for f in c["failures"][:12]:
            print(f"       ✗ {f}")
        if len(c["failures"]) > 12:
            print(f"       …… 另有 {len(c['failures']) - 12} 条")
    print("=" * 72)
    total = sum(len(c["failures"]) for c in report.checks)
    print("结论：" + ("全部通过（0 项失败）" if report.ok else f"{total} 项失败"))
    return 0 if report.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
