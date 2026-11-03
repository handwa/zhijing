"""知识图谱校验器的「反向自检」——证明规则不是永远 PASS 的摆设。

做法：读取已构建的 graph.json，逐条注入一类真实会犯的错误（人工标注、LLM 抽取、
迁移脚本都容易犯），再跑一遍 validate，确认**对应的那条规则**确实变红。

如果哪天有人把某条规则改成了恒真（或误删），这个脚本会立刻失败。

用法：
    python kg/src/selftest.py          # 全部用例，失败非 0 退出
    python kg/src/selftest.py -v       # 同时打印每条规则的失败明细
"""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path
from typing import Any, Callable, Dict, List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kg_model as M
import validate_graph as V

Graph = Dict[str, Any]


def _clone(g: Graph) -> Graph:
    return json.loads(json.dumps(g))


# --------------------------------------------------------------------------
# 注入器：每个函数拿到一份可改的深拷贝，制造一类错误
# --------------------------------------------------------------------------

def inject_dangling_edge(g: Graph) -> str:
    """把一条边的终点改成不存在的命题 ID。"""
    g["edges"][0]["to"] = "clm.ghost.0000"
    return f"边 {g['edges'][0]['id']} 指向 clm.ghost.0000"


def inject_edge_without_boundary(g: Graph) -> str:
    """边只写前提、不写边界——最常见的偷懒写法。"""
    edge = g["edges"][0]
    edge["condition"]["boundary"] = ""
    return f"边 {edge['id']} 的 boundary 被清空"


def inject_too_many_crosslinks(g: Graph) -> str:
    """给一张卡片挂 8 条交叉链接（上限 5），退化成节点图。"""
    card = g["cards"][0]
    others = [c["id"] for c in g["cards"] if c["id"] != card["id"]][:8]
    card["crossLinks"] = [
        {"target": cid, "targetKind": "card", "label": "注入的链接", "weight": round(0.9 - i * 0.1, 2)}
        for i, cid in enumerate(others)
    ]
    return f"卡片 {card['id']} 挂了 {len(card['crossLinks'])} 条交叉链接"


def inject_claim_in_navigation(g: Graph) -> str:
    """在导航里并列一个「深度图谱」入口，绕开「只能从叶子卡片下钻」。"""
    g["navigation"][0]["children"].append({
        "title": "深度图谱（偷懒入口）",
        "kind": "claim",
        "id": g["claims"][0]["id"],
    })
    return "导航根节点下多了 kind=claim 的条目"


def inject_bad_state(g: Graph) -> str:
    """写一个四态之外的状态值（比如前端习惯的 done）。"""
    g["claims"][0]["depth"]["probeState"]["probe_status"] = "done"
    return f"命题 {g['claims'][0]['id']} 状态改为 done"


def inject_double_owner(g: Graph) -> str:
    """同一条命题被两张卡片同时认领 —— 只改其中一张，制造「一题两主」。"""
    claim = g["claims"][0]
    owner = next(c for c in g["cards"] if claim["id"] in c["claimIds"])
    other = next(c for c in g["cards"] if c["id"] != owner["id"])
    other["claimIds"].append(claim["id"])
    return f"命题 {claim['id']} 同时属于 {owner['id']} 与 {other['id']}"


def inject_leak_to_breadth(g: Graph) -> str:
    """把边界条件写进广度侧 —— 等于第一屏就把答案给了。"""
    g["claims"][0]["breadth"]["boundary"] = "注入：不该出现在第一屏的边界"
    return f"命题 {g['claims'][0]['id']} 的 breadth 多了 boundary 字段"


def inject_wrong_id_hash(g: Graph) -> str:
    """ID 尾部的哈希与正文不符（正文改过但没重建 ID）。"""
    claim = g["claims"][0]
    old = claim["id"]
    claim["id"] = old[:-4] + "dead"
    for card in g["cards"]:
        card["claimIds"] = [claim["id"] if c == old else c for c in card["claimIds"]]
    for edge in g["edges"]:
        for side in ("from", "to"):
            if edge[side] == old:
                edge[side] = claim["id"]
    return f"命题 {old} 的哈希尾段被改成 dead"


CASES: List[Tuple[str, Callable[[Graph], str], str]] = [
    ("悬空边（指向不存在的命题）", inject_dangling_edge, "3"),
    ("边缺 boundary（只写前提）", inject_edge_without_boundary, "4"),
    ("交叉链接超上限", inject_too_many_crosslinks, "5"),
    ("导航并列深度入口", inject_claim_in_navigation, "8"),
    ("探测状态越界", inject_bad_state, "7"),
    ("命题一题两主", inject_double_owner, "2"),
    ("第一屏泄露条件", inject_leak_to_breadth, "6"),
    ("ID 哈希与正文不符", inject_wrong_id_hash, "1"),
]


def main() -> int:
    verbose = "-v" in sys.argv
    graph = V.load(None)
    base = V.validate(copy.deepcopy(graph))
    if not base.ok:
        print("基线图谱本身未通过校验，先修语料再跑自检：")
        for c in base.checks:
            if not c["passed"]:
                print(f"  [{c['no']}] {c['title']}")
        return 2

    print(f"基线：{len(base.checks)} 条规则全部通过（{M.GRAPH_JSON.name}）")
    print("=" * 78)

    rows: List[Tuple[str, str, str, bool]] = []
    for title, inject, expect in CASES:
        broken = _clone(graph)
        detail = inject(broken)
        report = V.validate(broken)
        failed = [c["no"] for c in report.checks if not c["passed"]]
        hit = expect in failed
        rows.append((title, expect, ",".join(failed) or "（无）", hit))
        if verbose or not hit:
            print(f"  {title}：注入「{detail}」")
            for c in report.checks:
                if not c["passed"]:
                    for msg in c["failures"][:2]:
                        print(f"      [{c['no']}] {msg}")

    print(f"{'注入的错误':<26}{'应命中':<8}{'实际命中':<12}判定")
    print("-" * 78)
    for title, expect, actual, hit in rows:
        print(f"{title:<26}{expect:<10}{actual:<14}{'PASS' if hit else 'FAIL'}")

    bad = [r for r in rows if not r[3]]
    print("-" * 78)
    if bad:
        print(f"结论：{len(bad)} 条用例未被拦截 —— 校验器存在盲区，需补规则")
        return 1
    print(f"结论：{len(rows)} 类错误全部被对应规则拦截（校验器不是恒真）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
