#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
triple_gate.py —— 候选三元组的**结构闸门**与人工合并入口。

分工（重要）：
    这里只做**结构**校验：字段齐全、枚举合法、误判库存在、computable 与 contract 一致、
    契约引用的符号有没有声明。真正的语义裁判仍是 Kotlin 的 `ContractEvaluator.validate()`
    与 `BundledTriplesTest` —— 在 Python 里重写一套求值器必然与 Kotlin 漂移。

用法：
    python kg/src/triple_gate.py                       # 校验 + 写报告
    python kg/src/triple_gate.py --apply               # 把**全部通过**的候选合并进增补源
    python kg/src/triple_gate.py --apply --only tri.x,tri.y
    python kg/src/triple_gate.py --apply --dry-run     # 只看会写入什么

拒绝项**不会**自动丢弃：模型写错契约常见于措辞（少声明一个符号），
报告给人看比重试烧钱更划算。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kg_model as M  # noqa: E402

GRAPH_PATH = M.REPO_ROOT / "app" / "src" / "main" / "assets" / "kg" / "graph.json"
CANDIDATES_PATH = M.KG_ROOT / "data" / "out" / "triples_candidates.json"
REPORT_PATH = M.KG_ROOT / "data" / "out" / "triples_report.md"
SUPPLEMENT_PATH = M.KG_ROOT / "data" / "source" / "deep_supplement.json"

KINDS = {"shape", "range", "statistics", "gradient", "semantic"}
OUTCOMES = {"HOLDS", "WEAKENED", "BECOMES_OTHER", "FAILS"}
SOURCES = {"COMPUTED", "AUTHORED_HUMAN", "AUTHORED_MODEL"}
OP_KINDS = {"matmul", "elementWise", "softmax", "reduce", "softmaxSum"}
CONSTRAINT_KINDS = {"equals", "notEquals", "compare", "positive"}

MIN_INTERVENTIONS = 2
MIN_CORRECTION = 10

SYMBOL_EXPR = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)(?:\s*[+-]\s*\d+)?$")


# --------------------------------------------------------------------------
# 1. 单条候选的校验
# --------------------------------------------------------------------------

def _symbols_in_expr(expr: Any) -> List[str]:
    """`in_x` / `in_x+1` / `H-1` 都能取出符号名；不合法返回空列表。"""
    if not isinstance(expr, str):
        return []
    match = SYMBOL_EXPR.match(expr.strip())
    return [match.group(1)] if match else []


def _op_symbols(op: Dict[str, Any]) -> List[str]:
    names: List[str] = []
    for key in ("lhs", "rhs", "input"):
        value = op.get(key)
        if isinstance(value, list):
            names.extend(str(item) for item in value)
    return names


def check_contract(contract: Any) -> List[str]:
    reasons: List[str] = []
    if not isinstance(contract, dict):
        return ["contract 不是对象"]

    symbols = contract.get("symbols")
    if not isinstance(symbols, dict) or not symbols:
        return ["contract.symbols 为空或不是对象"]
    declared = set(symbols.keys())
    for name, value in symbols.items():
        if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
            reasons.append(f"符号 {name} 的值必须是正整数（现在是 {value!r}）")

    op = contract.get("op")
    if not isinstance(op, dict):
        return reasons + ["contract.op 缺失或不是对象"]
    if op.get("kind") not in OP_KINDS:
        reasons.append(f"算子 kind 非法：{op.get('kind')!r}")
    for name in _op_symbols(op):
        if name not in declared:
            reasons.append(f"算子引用了未声明的符号 {name}")

    constraints = contract.get("constraints") or []
    if not isinstance(constraints, list):
        return reasons + ["contract.constraints 不是数组"]
    for constraint in constraints:
        if not isinstance(constraint, dict):
            reasons.append("constraints 里有非对象项")
            continue
        if constraint.get("kind") not in CONSTRAINT_KINDS:
            reasons.append(f"约束 kind 非法：{constraint.get('kind')!r}")
            continue
        for key in ("left", "right", "symbol"):
            if key not in constraint:
                continue
            for name in _symbols_in_expr(constraint[key]):
                if name not in declared:
                    reasons.append(f"约束引用了未声明的符号 {name}")
    return reasons


def check_candidate(
    candidate: Dict[str, Any],
    claims: Dict[str, Dict[str, Any]],
) -> List[str]:
    """返回拒绝原因列表；空列表 = 通过结构闸门。"""
    reasons: List[str] = []

    for field in ("id", "claimId", "cardId", "statement", "kind", "mechanism", "computable"):
        if candidate.get(field) in (None, ""):
            reasons.append(f"缺少字段 {field}")

    claim_id = candidate.get("claimId")
    claim = claims.get(claim_id)
    if claim_id and claim is None:
        reasons.append(f"claimId 不存在于图谱：{claim_id}")
    elif claim is not None:
        expected_card = claim.get("breadth", {}).get("cardId")
        if candidate.get("cardId") != expected_card:
            reasons.append(
                f"cardId 与命题归属不一致（应为 {expected_card}）"
            )

    if candidate.get("kind") and candidate["kind"] not in KINDS:
        reasons.append(f"kind 非法：{candidate['kind']!r}")

    if not isinstance(candidate.get("computable"), bool):
        reasons.append("computable 必须是布尔值")

    if not str(candidate.get("mechanism") or "").strip():
        reasons.append("机制为空（入库三门槛之一）")

    interventions = candidate.get("interventions")
    if not isinstance(interventions, list) or not interventions:
        return reasons + ["缺少 interventions"]
    if len(interventions) < MIN_INTERVENTIONS:
        reasons.append(f"干预点少于 {MIN_INTERVENTIONS} 个")

    computable = candidate.get("computable") is True
    for index, intervention in enumerate(interventions, start=1):
        if not isinstance(intervention, dict):
            reasons.append(f"第 {index} 个干预点不是对象")
            continue
        if not intervention.get("id"):
            reasons.append(f"第 {index} 个干预点缺少 id")
        if not str(intervention.get("consequence") or "").strip():
            reasons.append(f"干预 {intervention.get('id')} 缺少 consequence")

        source = intervention.get("source")
        if source not in SOURCES:
            reasons.append(f"干预 {intervention.get('id')} 的 source 非法：{source!r}")

        misjudgements = intervention.get("misjudgements")
        if not isinstance(misjudgements, list) or not misjudgements:
            reasons.append(f"干预 {intervention.get('id')} 没有误判库")
        else:
            for item in misjudgements:
                if not isinstance(item, dict):
                    reasons.append(f"干预 {intervention.get('id')} 的误判项不是对象")
                    continue
                predicted = item.get("predicted")
                if predicted not in OUTCOMES:
                    reasons.append(
                        f"干预 {intervention.get('id')} 的 predicted 非法：{predicted!r}"
                    )
                if len(str(item.get("correction") or "")) < MIN_CORRECTION:
                    reasons.append(
                        f"干预 {intervention.get('id')} 的纠正没回到机制上（太短）"
                    )

        contract = intervention.get("contract")
        if computable:
            if contract is None:
                reasons.append(f"可算干预 {intervention.get('id')} 没带 contract")
            else:
                reasons.extend(check_contract(contract))
            overrides = intervention.get("overrides") or {}
            if not isinstance(overrides, dict):
                reasons.append(f"干预 {intervention.get('id')} 的 overrides 不是对象")
            elif isinstance(contract, dict):
                declared = set((contract.get("symbols") or {}).keys())
                for name, value in overrides.items():
                    if name not in declared:
                        reasons.append(f"overrides 引用了未声明的符号 {name}")
                    if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
                        reasons.append(f"overrides.{name} 必须是正整数")
        else:
            if contract is not None:
                reasons.append(f"预置答案干预 {intervention.get('id')} 不该带 contract")
            if intervention.get("outcome") not in OUTCOMES:
                reasons.append(
                    f"预置答案干预 {intervention.get('id')} 的 outcome 非法："
                    f"{intervention.get('outcome')!r}"
                )
    return reasons


# --------------------------------------------------------------------------
# 2. 批量校验与报告
# --------------------------------------------------------------------------

def claims_index(graph: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    return {claim["id"]: claim for claim in graph.get("claims", [])}


def evaluate(candidates: Iterable[Dict[str, Any]], graph: Dict[str, Any]):
    """返回 [(候选, 拒绝原因列表)]，顺序与输入一致。"""
    claims = claims_index(graph)
    return [(candidate, check_candidate(candidate, claims)) for candidate in candidates]


def render_report(rows: List[Tuple[Dict[str, Any], List[str]]]) -> str:
    passed = [row for row in rows if not row[1]]
    lines = [
        "# 三元组候选闸门报告",
        "",
        f"- 候选总数：{len(rows)}",
        f"- 通过：{len(passed)}",
        f"- 拒绝：{len(rows) - len(passed)}",
        "",
        "> 拒绝不代表内容有错，常见原因是契约少声明了一个符号。改完可重跑本脚本。",
        "",
        "| 命题 | 三元组 | 结论 | 首个原因 |",
        "|---|---|---|---|",
    ]
    for candidate, reasons in rows:
        verdict = "通过" if not reasons else "拒绝"
        first = "" if not reasons else reasons[0].replace("|", "\\|")
        lines.append(
            f"| `{candidate.get('claimId', '?')}` | `{candidate.get('id', '?')}` "
            f"| {verdict} | {first} |"
        )
    detail = [row for row in rows if row[1]]
    if detail:
        lines += ["", "## 拒绝明细", ""]
        for candidate, reasons in detail:
            lines.append(f"### `{candidate.get('id', '?')}`")
            lines.extend(f"- {reason}" for reason in reasons)
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


# --------------------------------------------------------------------------
# 3. 人工合并
# --------------------------------------------------------------------------

def apply_candidates(
    candidates: List[Dict[str, Any]],
    supplement_path: Path,
    dry_run: bool = False,
) -> Tuple[int, int]:
    """把候选追加进增补源；同 id 已存在则跳过。返回 (新增, 跳过)。"""
    supplement = (
        json.loads(supplement_path.read_text(encoding="utf-8"))
        if supplement_path.exists()
        else {}
    )
    existing = {t.get("id") for t in supplement.get("triples", [])}
    added = 0
    skipped = 0
    for candidate in candidates:
        if candidate.get("id") in existing:
            skipped += 1
            continue
        supplement.setdefault("triples", []).append(candidate)
        existing.add(candidate.get("id"))
        added += 1
    if not dry_run and added:
        supplement.setdefault("meta", {})["updatedAt"] = "2026-11-18"
        supplement_path.write_text(
            json.dumps(supplement, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    return added, skipped


# --------------------------------------------------------------------------
# 4. CLI
# --------------------------------------------------------------------------

def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    parser = argparse.ArgumentParser(description="三元组候选结构闸门")
    parser.add_argument("--input", default=str(CANDIDATES_PATH))
    parser.add_argument("--report", default=str(REPORT_PATH))
    parser.add_argument("--graph", default=str(GRAPH_PATH))
    parser.add_argument("--supplement", default=str(SUPPLEMENT_PATH))
    parser.add_argument("--apply", action="store_true", help="把通过项合并进增补源")
    parser.add_argument("--only", default="", help="只合并这些 id（逗号分隔）")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"找不到候选文件：{input_path}（先跑 python kg/src/gen_triples.py）")
        return 2

    payload = json.loads(input_path.read_text(encoding="utf-8"))
    candidates = payload.get("candidates", [])
    graph = json.loads(Path(args.graph).read_text(encoding="utf-8"))

    rows = evaluate(candidates, graph)
    report = render_report(rows)
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    if not args.dry_run:
        report_path.write_text(report, encoding="utf-8")

    passed = [candidate for candidate, reasons in rows if not reasons]
    rejected = len(rows) - len(passed)
    print(f"候选 {len(rows)} 条：通过 {len(passed)}，拒绝 {rejected}")
    for candidate, reasons in rows:
        if reasons:
            print(f"  ✗ {candidate.get('id', '?')}：{reasons[0]}")
    if not args.dry_run:
        print(f"报告 → {report_path}")

    if args.apply:
        only = {item.strip() for item in args.only.split(",") if item.strip()}
        to_apply = [c for c in passed if not only or c.get("id") in only]
        added, skipped = apply_candidates(to_apply, Path(args.supplement), args.dry_run)
        print(f"合并：新增 {added}，已存在跳过 {skipped}")
        if not args.dry_run and added:
            print("下一步：python kg/src/export.py --all  然后同步计数用例")

    if rejected and not args.apply:
        print("拒绝项已保留在候选文件里，改完重跑即可（不会自动丢弃）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
