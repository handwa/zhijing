#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
export.py —— 把 graph.json 导出成「能直接用的三种形态」。

    1. 默认          → kg/web/graph-data.js      （window.KG = {...}，file:// 双击即开）
    2. --target assets → app/src/main/assets/kg/graph.json （给 Android 端读取）
    3. --target sql    → kg/data/out/graph.sql   （Room 建表 + 种子数据，供 :data 模块导入）

为什么默认导出 js 而不是让网页 fetch json：
    演示页要能在 `file://` 下双击直接打开，而 file:// 里 fetch 会被 CORS 拦掉。
    把数据包成 `window.KG = {...}` 的脚本，零服务器、零构建，评审时最省事。

SQL 导出说明：
    刻意不改 `:data` 模块的 Room 实体与 version（那是队友正在动的代码），只生成
    「参考 schema + 种子数据」。`kg_node.id` 用 TEXT 主键，直接落命题 ID —— 一旦
    换成自增整数，ID 就不再是唯一真相源（见 schema/id-spec.md 第 5 节）。

命令行：
    python kg/src/export.py                 # 只导出 web 数据
    python kg/src/export.py --all           # web + assets + sql
    python kg/src/export.py --target sql
    python kg/src/export.py --stats
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, List

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kg_model as M

ASSETS_PATH = M.REPO_ROOT / "app" / "src" / "main" / "assets" / "kg" / "graph.json"
SQL_PATH = M.KG_ROOT / "data" / "out" / "graph.sql"

#: 随包深度内容的增补源（前置关系 + 条件三元组）。
#:
#: 为什么单独一个文件：`build_graph.py` 只从 markdown 语料产出 outline/card/claim/edge，
#: 前置关系是模型抽取后人工核对的、三元组是逐条撰写并跑过准入检查的，两者都不在
#: 语料里。以前它们直接手改在 `assets/kg/graph.json` 上，结果**只要重跑一次管线
#: （包括 `kg/tests` 里的 export 测试）就会被整份覆盖掉**。
#: 现在它们有自己的源文件，export 时合并，重跑导出不会再丢。
SUPPLEMENT_PATH = M.KG_ROOT / "data" / "source" / "deep_supplement.json"


def load_graph() -> Dict[str, Any]:
    if not M.GRAPH_JSON.exists():
        raise SystemExit("找不到 kg/data/out/graph.json，先运行 python kg/src/build_graph.py")
    return json.loads(M.GRAPH_JSON.read_text(encoding="utf-8"))


def load_supplement() -> Dict[str, Any]:
    """读增补源；文件不存在时返回空（老仓库照样能导出）。"""
    if not SUPPLEMENT_PATH.exists():
        return {}
    return json.loads(SUPPLEMENT_PATH.read_text(encoding="utf-8"))


def merge_supplement(graph: Dict[str, Any], supplement: Dict[str, Any]) -> Dict[str, Any]:
    """
    把增补源合进 assets 视图。

    引用不到的条目（卡片被删、命题改 ID）**丢弃并计数**，不让它把导出带崩：
    宁可少一条前置/三元组，也不能让 APK 里的 graph.json 出现悬空引用。
    """
    claim_ids = {c["id"] for c in graph["claims"]}
    card_ids = {c["id"] for c in graph["cards"]}

    prerequisites: List[Dict[str, Any]] = []
    dropped_prerequisites = 0
    for p in supplement.get("prerequisites", []):
        src, dst = p.get("from"), p.get("to")
        if src in card_ids and dst in card_ids and src != dst:
            item = {
                "from": src,
                "to": dst,
                "kind": p.get("kind", "recommended"),
                "reason": p.get("reason", ""),
            }
            if p.get("source"):
                item["source"] = p["source"]
            prerequisites.append(item)
        else:
            dropped_prerequisites += 1

    triples: List[Dict[str, Any]] = []
    dropped_triples = 0
    for t in supplement.get("triples", []):
        if t.get("id") and t.get("claimId") in claim_ids and t.get("cardId") in card_ids:
            triples.append(t)
        else:
            dropped_triples += 1

    return {
        "prerequisites": prerequisites,
        "triples": triples,
        "droppedPrerequisites": dropped_prerequisites,
        "droppedTriples": dropped_triples,
    }


# --------------------------------------------------------------------------
# 1. web 数据
# --------------------------------------------------------------------------

def export_web(graph: Dict[str, Any]) -> Path:
    """包成 JS 全局变量，顺带把「广度侧要用的最小视图」预计算好，减少页面里的遍历。"""
    cards = {c["id"]: c for c in graph["cards"]}
    probe_by_card: Dict[str, Dict[str, int]] = {}
    for cl in graph["claims"]:
        cid = cl["breadth"]["cardId"]
        status = cl["depth"]["probeState"]["probe_status"]
        bucket = probe_by_card.setdefault(cid, {s: 0 for s in M.PROBE_STATES})
        bucket[status] = bucket.get(status, 0) + 1
        bucket["total"] = bucket.get("total", 0) + 1

    payload = {
        "meta": graph["meta"],
        "enums": {
            "nodeTypes": list(M.NODE_TYPES),
            "edgeTypes": M.EDGE_TYPES,
            "conditionKinds": M.CONDITION_KINDS,
            "probeStates": M.PROBE_STATES,
            "entityKinds": M.ENTITY_KINDS,
        },
        "outline": graph["outline"],
        "cards": graph["cards"],
        "claims": graph["claims"],
        "entities": graph["entities"],
        "edges": graph["edges"],
        "navigation": graph["navigation"],
        "derived": {
            "probeByCard": probe_by_card,
            "cardTitles": {c["id"]: c["title"] for c in graph["cards"]},
        },
    }
    M.WEB_DATA_JS.parent.mkdir(parents=True, exist_ok=True)
    js = ("// 由 kg/src/export.py 自动生成，请勿手改。\n"
          "// 数据源：kg/data/out/graph.json\n"
          "window.KG = " + json.dumps(payload, ensure_ascii=False, indent=1) + ";\n")
    M.WEB_DATA_JS.write_text(js, encoding="utf-8")
    return M.WEB_DATA_JS


# --------------------------------------------------------------------------
# 2. Android assets
# --------------------------------------------------------------------------

def export_assets(graph: Dict[str, Any]) -> Path:
    """
    导出 Android assets 版图谱。

    与其它两个目标不同：**这里要合并增补源**（前置关系 + 条件三元组）。
    web/SQL 只需要 build 产出的那部分，多出来的字段它们也用不上。
    """
    merged = merge_supplement(graph, load_supplement())
    payload = dict(graph)
    payload["prerequisites"] = merged["prerequisites"]
    payload["triples"] = merged["triples"]

    ASSETS_PATH.parent.mkdir(parents=True, exist_ok=True)
    ASSETS_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    dropped = merged["droppedPrerequisites"] + merged["droppedTriples"]
    if dropped:
        print(
            f"  ! 增补源有 {dropped} 条引用不到节点被丢弃"
            f"（前置 {merged['droppedPrerequisites']} / 三元组 {merged['droppedTriples']}）"
        )
    return ASSETS_PATH


# --------------------------------------------------------------------------
# 3. SQLite 种子
# --------------------------------------------------------------------------

def _q(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def export_sql(graph: Dict[str, Any]) -> Path:
    lines: List[str] = [
        "-- 由 kg/src/export.py 自动生成，请勿手改。",
        "-- 用途：给 :data 模块提供知识图谱的参考表结构与种子数据。",
        "-- 注意：本文件不影响 Room 现有 12 张表；接入时由队友在一次 Migration 中引入。",
        "",
        "PRAGMA foreign_keys = ON;",
        "",
        "CREATE TABLE IF NOT EXISTS kg_node (",
        "  id          TEXT PRIMARY KEY,           -- 命题/卡片/实体 ID，唯一真相源",
        "  kind        TEXT NOT NULL,              -- outline | card | claim | entity",
        "  title       TEXT,                       -- 显示名（命题取 statement 前 40 字）",
        "  path        TEXT,                       -- 语义路径，如 s1-1-linear",
        "  parent_id   TEXT,                       -- 大纲父子关系 / 主归属卡",
        "  probe_status TEXT NOT NULL DEFAULT 'unprobed', -- 仅 claim 有值：四态",
        "  payload     TEXT NOT NULL               -- 完整 JSON 片段，读侧按需反序列化",
        ");",
        "",
        "CREATE INDEX IF NOT EXISTS idx_kg_node_kind ON kg_node(kind);",
        "CREATE INDEX IF NOT EXISTS idx_kg_node_parent ON kg_node(parent_id);",
        "",
        "CREATE TABLE IF NOT EXISTS kg_edge (",
        "  id            TEXT PRIMARY KEY,",
        "  from_id       TEXT NOT NULL,",
        "  to_id         TEXT NOT NULL,",
        "  type          TEXT NOT NULL,            -- depends_on / affects / contains / contrasts / refutes / prerequisite",
        "  condition_kind TEXT NOT NULL,           -- shape / range / statistics / gradient / semantic",
        "  premise       TEXT NOT NULL,",
        "  boundary      TEXT NOT NULL,",
        "  counterexample TEXT,",
        "  weight        REAL NOT NULL DEFAULT 0.5,",
        "  evidence      TEXT",
        ");",
        "",
        "CREATE INDEX IF NOT EXISTS idx_kg_edge_from ON kg_edge(from_id);",
        "CREATE INDEX IF NOT EXISTS idx_kg_edge_to ON kg_edge(to_id);",
        "",
        "-- ------------------------------------------------------------------",
        "-- 种子数据",
        "-- ------------------------------------------------------------------",
    ]

    for o in graph["outline"]:
        lines.append(
            "INSERT OR REPLACE INTO kg_node (id, kind, title, path, parent_id, payload) VALUES ("
            f"{_q(o['id'])}, 'outline', {_q(o['title'])}, {_q(o['path'])}, {_q(o['parentId'])}, "
            f"{_q(json.dumps(o, ensure_ascii=False))});"
        )
    for c in graph["cards"]:
        lines.append(
            "INSERT OR REPLACE INTO kg_node (id, kind, title, path, parent_id, payload) VALUES ("
            f"{_q(c['id'])}, 'card', {_q(c['title'])}, {_q(c['outlinePath'])}, NULL, "
            f"{_q(json.dumps(c, ensure_ascii=False))});"
        )
    for e in graph["entities"]:
        lines.append(
            "INSERT OR REPLACE INTO kg_node (id, kind, title, path, parent_id, payload) VALUES ("
            f"{_q(e['id'])}, 'entity', {_q(e['canonicalName'])}, NULL, NULL, "
            f"{_q(json.dumps(e, ensure_ascii=False))});"
        )
    for cl in graph["claims"]:
        title = cl["statement"][:40]
        lines.append(
            "INSERT OR REPLACE INTO kg_node (id, kind, title, path, parent_id, probe_status, payload) VALUES ("
            f"{_q(cl['id'])}, 'claim', {_q(title)}, {_q(cl['breadth']['outlinePath'])}, "
            f"{_q(cl['breadth']['cardId'])}, {_q(cl['depth']['probeState']['probe_status'])}, "
            f"{_q(json.dumps(cl, ensure_ascii=False))});"
        )
    for e in graph["edges"]:
        cond = e["condition"]
        lines.append(
            "INSERT OR REPLACE INTO kg_edge (id, from_id, to_id, type, condition_kind, premise, boundary, "
            "counterexample, weight, evidence) VALUES ("
            f"{_q(e['id'])}, {_q(e['from'])}, {_q(e['to'])}, {_q(e['type'])}, {_q(cond['kind'])}, "
            f"{_q(cond['premise'])}, {_q(cond['boundary'])}, {_q(cond.get('counterexample'))}, "
            f"{_q(e['weight'])}, {_q(e.get('evidence'))});"
        )

    lines += [
        "",
        "-- 与现有业务表的衔接（生成式主题树，不重复存储）：",
        "--   1) kg_node(kind='outline') 的 path 可作为 topic_table.tag_name 的前缀来源；",
        "--   2) 一条用户收藏内容对某个命题的引用，建议落在 association_table（shared_topics 填命题 ID）；",
        "--   3) 判断状态不要抄进 topic_table —— 广度侧必须从 kg_node.probe_status 派生。",
        "",
    ]
    SQL_PATH.parent.mkdir(parents=True, exist_ok=True)
    SQL_PATH.write_text("\n".join(lines), encoding="utf-8")
    return SQL_PATH


# --------------------------------------------------------------------------
# 统计
# --------------------------------------------------------------------------

def print_stats(graph: Dict[str, Any]) -> None:
    counts = graph["meta"]["counts"]
    print("规模")
    print(f"  outline {counts['outline']}  card {counts['card']}  claim {counts['claim']}  "
          f"entity {counts['entity']}  edge {counts['edge']}")
    print("命题状态分布")
    dist: Dict[str, int] = {}
    for cl in graph["claims"]:
        s = cl["depth"]["probeState"]["probe_status"]
        dist[s] = dist.get(s, 0) + 1
    for k, v in M.PROBE_STATES.items():
        bar = "█" * dist.get(k, 0)
        print(f"  {v:<4} {dist.get(k, 0):>3}  {bar}")
    print("边类型分布")
    edist: Dict[str, int] = {}
    for e in graph["edges"]:
        edist[e["type"]] = edist.get(e["type"], 0) + 1
    for k, v in sorted(edist.items(), key=lambda kv: -kv[1]):
        print(f"  {k:<12} {v:>3}  {M.EDGE_TYPES.get(k, '')}")
    print("条件类型分布（撞错时定位错点的依据）")
    kdist: Dict[str, int] = {}
    for e in graph["edges"]:
        k = e["condition"]["kind"]
        kdist[k] = kdist.get(k, 0) + 1
    for k, v in sorted(kdist.items(), key=lambda kv: -kv[1]):
        print(f"  {k:<12} {v:>3}  {M.CONDITION_KINDS.get(k, '')}")
    print("命题数最多的卡片")
    top = sorted(graph["cards"], key=lambda c: -len(c["claimIds"]))[:5]
    for c in top:
        print(f"  {len(c['claimIds']):>2} 条  {c['title']}")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    graph = load_graph()
    targets = set()
    if "--all" in sys.argv:
        targets = {"web", "assets", "sql"}
    elif "--target" in sys.argv:
        targets = {sys.argv[sys.argv.index("--target") + 1]}
    else:
        targets = {"web"}

    mapping = {"web": export_web, "assets": export_assets, "sql": export_sql}
    for name in ("web", "assets", "sql"):
        if name in targets:
            path = mapping[name](graph)
            try:
                shown = path.relative_to(M.REPO_ROOT)
            except ValueError:
                shown = path
            print(f"已导出 {name:<6} → {shown}")

    if "--stats" in sys.argv or "--all" in sys.argv:
        print()
        print_stats(graph)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
