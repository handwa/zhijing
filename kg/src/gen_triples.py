#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_triples.py —— 离线批量产出「条件—机制—反事实」三元组候选。

为什么是离线脚本，而不是运行时链路：
    契约生成是**内容工程**。接到「用户点生成图谱」上会同时放大成本与质量风险
    （每张图多 N 次调用，且模型写的契约经常不自洽）。所以这里只负责**产候选**，
    人工过一遍闸门再合并进增补源；App 运行时判定依旧只走本地 ContractEvaluator。

用法（凭据只走环境变量，不落盘、不进仓库）：

    $env:ZHIJING_BASE_URL="https://api.deepseek.com"
    $env:ZHIJING_MODEL="deepseek-chat"
    $env:ZHIJING_API_KEY="sk-..."
    python kg/src/gen_triples.py --limit 3 --dry-run     # 先看提示词装得对不对
    python kg/src/gen_triples.py --rps 1                 # 真正调用
    python kg/src/triple_gate.py                         # 过闸门，看报告

产物：
    kg/data/out/triples_candidates.json   候选（按 claimId 归并）
    kg/data/out/triples_prompts.json      仅 --dry-run：装配好的请求，便于人工检查

约定：
    * 默认**跳过已有三元组的命题**，可反复重跑只补缺口；
    * claimId / cardId 以本地图谱为准覆盖，防止模型改写；
    * 单条失败不中断整批，失败原因写进 errors。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kg_model as M  # noqa: E402

GRAPH_PATH = M.REPO_ROOT / "app" / "src" / "main" / "assets" / "kg" / "graph.json"
PROMPT_PATH = M.KG_ROOT / "prompts" / "extract_triples.md"
OUT_DIR = M.KG_ROOT / "data" / "out"
CANDIDATES_PATH = OUT_DIR / "triples_candidates.json"
PROMPTS_PATH = OUT_DIR / "triples_prompts.json"

DEFAULT_TIMEOUT = 120
RETRY_BACKOFF = (2, 5)

FENCE = re.compile(r"```(?:json)?\s*(.*?)```", re.S)


# --------------------------------------------------------------------------
# 1. 输入装配
# --------------------------------------------------------------------------

def load_graph(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise SystemExit(f"找不到图谱：{path}（先跑 python kg/src/export.py --all）")
    return json.loads(path.read_text(encoding="utf-8"))


def extract_system_prompt(markdown: str) -> str:
    """取 `## 5. system prompt` 下面的围栏正文；取不到就退回整篇。"""
    marker = markdown.find("system prompt")
    body = markdown[marker:] if marker >= 0 else markdown
    match = FENCE.search(body)
    if match:
        return match.group(1).strip()
    return markdown.strip()


def kind_of_claim(graph: Dict[str, Any], claim_id: str) -> str:
    """命题的依赖类型从深度边上取；没有边就按 semantic 兜底。"""
    for edge in graph.get("edges", []):
        if edge.get("from") == claim_id:
            kind = edge.get("condition", {}).get("kind")
            if kind:
                return kind
    return "semantic"


def build_user_message(claim: Dict[str, Any], graph: Dict[str, Any]) -> str:
    """把一条命题拼成 JSON 请求体。字段与提示词第 4 节输出契约对齐。"""
    payload = {
        "claimId": claim["id"],
        "cardId": claim["breadth"]["cardId"],
        "statement": claim["statement"],
        "conditions": claim.get("conditions", []),
        "evidence": claim.get("evidence", ""),
        "kind": kind_of_claim(graph, claim["id"]),
        "reminder": "只输出一个三元组 JSON 对象，不要数组、不要解释、不要 Markdown。",
    }
    return json.dumps(payload, ensure_ascii=False)


def pending_claims(
    graph: Dict[str, Any],
    only_claim: Optional[str] = None,
    include_existing: bool = False,
) -> List[Dict[str, Any]]:
    covered = {t.get("claimId") for t in graph.get("triples", [])}
    claims = graph.get("claims", [])
    if only_claim:
        claims = [c for c in claims if c.get("id") == only_claim]
    elif not include_existing:
        claims = [c for c in claims if c.get("id") not in covered]
    return claims


# --------------------------------------------------------------------------
# 2. 调用与解析
# --------------------------------------------------------------------------

def post_chat(
    base_url: str,
    model: str,
    api_key: str,
    system: str,
    user: str,
    timeout: int = DEFAULT_TIMEOUT,
) -> str:
    url = base_url.rstrip("/") + "/v1/chat/completions"
    body = json.dumps(
        {
            "model": model,
            "temperature": 0.2,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        },
        ensure_ascii=False,
    ).encode("utf-8")

    request = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))

    choices = payload.get("choices") or []
    if not choices:
        raise ValueError("接口返回没有 choices")
    return choices[0].get("message", {}).get("content", "")


def parse_candidate(raw: str) -> Dict[str, Any]:
    """从模型输出里取第一个 JSON 对象。围栏、前后废话都能容忍。"""
    text = raw.strip()
    fenced = FENCE.search(text)
    if fenced:
        text = fenced.group(1).strip()

    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("输出里没有 JSON 对象")
    data = json.loads(text[start : end + 1])

    if isinstance(data.get("triples"), list) and data["triples"]:
        data = data["triples"][0]
    if not isinstance(data, dict):
        raise ValueError("输出不是 JSON 对象")
    return data


def normalize_candidate(
    candidate: Dict[str, Any],
    claim: Dict[str, Any],
    kind: str,
) -> Dict[str, Any]:
    """把模型可能写错的归属字段按本地图谱纠正，并补上缺的 id。"""
    fixed = dict(candidate)
    fixed["claimId"] = claim["id"]
    fixed["cardId"] = claim["breadth"]["cardId"]
    if not fixed.get("statement"):
        fixed["statement"] = claim.get("statement", "")
    if not fixed.get("id"):
        tail = claim["id"].rsplit(".", 1)[-1] or "auto"
        fixed["id"] = f"tri.gen.{tail}"
    fixed.setdefault("kind", kind)
    return fixed


# --------------------------------------------------------------------------
# 3. CLI
# --------------------------------------------------------------------------

def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    parser = argparse.ArgumentParser(description="离线批量产三元组候选")
    parser.add_argument("--limit", type=int, default=0, help="最多处理几条命题（0 = 全部）")
    parser.add_argument("--claim", default=None, help="只处理某一条命题 id")
    parser.add_argument("--include-existing", action="store_true", help="连已有三元组的命题也重跑")
    parser.add_argument("--dry-run", action="store_true", help="只装配请求并打印，不调用接口")
    parser.add_argument("--rps", type=float, default=1.0, help="每秒最多几次调用")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT)
    parser.add_argument("--graph", default=str(GRAPH_PATH), help="图谱路径（默认 assets）")
    parser.add_argument("--out", default=str(CANDIDATES_PATH), help="候选输出文件")
    args = parser.parse_args()

    graph = load_graph(Path(args.graph))
    system = extract_system_prompt(PROMPT_PATH.read_text(encoding="utf-8"))
    claims = pending_claims(graph, args.claim, args.include_existing)
    if args.limit > 0:
        claims = claims[: args.limit]

    if not claims:
        print("没有需要处理的命题（都已有三元组）。")
        return 0

    if args.dry_run:
        preview = [
            {"claimId": claim["id"], "user": build_user_message(claim, graph)}
            for claim in claims
        ]
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        Path(PROMPTS_PATH).write_text(
            json.dumps(preview, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"待处理 {len(claims)} 条命题；请求已装配到 {PROMPTS_PATH}")
        print(f"system prompt {len(system)} 字；示例 user message：")
        print(preview[0]["user"])
        return 0

    base_url = os.environ.get("ZHIJING_BASE_URL", "").strip()
    model = os.environ.get("ZHIJING_MODEL", "").strip()
    api_key = os.environ.get("ZHIJING_API_KEY", "").strip()
    missing = [
        name
        for name, value in (
            ("ZHIJING_BASE_URL", base_url),
            ("ZHIJING_MODEL", model),
            ("ZHIJING_API_KEY", api_key),
        )
        if not value
    ]
    if missing:
        print(f"缺少环境变量：{', '.join(missing)}")
        print('示例：$env:ZHIJING_BASE_URL="https://api.deepseek.com"')
        return 2

    candidates: List[Dict[str, Any]] = []
    errors: List[Dict[str, str]] = []
    delay = 1.0 / args.rps if args.rps > 0 else 0.0

    for index, claim in enumerate(claims, start=1):
        print(f"[{index}/{len(claims)}] {claim['id']}")
        user = build_user_message(claim, graph)
        last_error = ""
        for attempt in range(len(RETRY_BACKOFF) + 1):
            try:
                raw = post_chat(base_url, model, api_key, system, user, args.timeout)
                candidate = normalize_candidate(
                    parse_candidate(raw), claim, kind_of_claim(graph, claim["id"])
                )
                candidates.append(candidate)
                print(f"    ok · {candidate.get('id')}")
                break
            except (urllib.error.URLError, urllib.error.HTTPError, ValueError, KeyError) as error:
                last_error = f"{type(error).__name__}: {error}"
                if attempt < len(RETRY_BACKOFF):
                    wait = RETRY_BACKOFF[attempt]
                    print(f"    重试（{wait}s）：{last_error}")
                    time.sleep(wait)
        else:
            errors.append({"claimId": claim["id"], "error": last_error})
            print(f"    失败：{last_error}")

        if delay:
            time.sleep(delay)

    payload = {
        "meta": {
            "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "model": model,
            "graph": str(Path(args.graph).relative_to(M.REPO_ROOT))
            if str(args.graph).startswith(str(M.REPO_ROOT))
            else args.graph,
            "requested": len(claims),
            "ok": len(candidates),
            "failed": len(errors),
        },
        "candidates": candidates,
        "errors": errors,
    }
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"\n候选 {len(candidates)} 条，失败 {len(errors)} 条 → {out_path}")
    print("下一步：python kg/src/triple_gate.py")
    return 0 if candidates else 1


if __name__ == "__main__":
    raise SystemExit(main())
