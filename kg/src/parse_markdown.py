#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
parse_markdown.py —— 把 Markdown 语料切成结构化块，并建立大纲树。

只做「读」和「切」，不做语义判断（实体、关系、命题都在 extract_rules.py）。
输出是一份中间文档（dict），供后续两步消费：

    parse_markdown.parse_file(path) -> {
        "title":    文档标题,
        "sections": [ {path,id,level,parentPath,order,title,number,blocks:[...]} ],
        "kg_blocks":[ {kind:"claim"|"edge", data:{...}, sectionPath, line} ],
        "stats":    {...}
    }

块类型：heading / para / list / table / code / kg / hr

对应设计文档：
    - heading  → 广度图谱的「分层大纲」，分类节点只组织不判断
    - 段落/列表/表格 → 「卡片」的正文（一段可独立阅读的内容）
    - kg 块    → 命题与边的精标通道（人工标注与 LLM 回填共用）

命令行：
    python kg/src/parse_markdown.py [--json]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kg_model as M

HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
NUMBER_RE = re.compile(r"^(\d+(?:\.\d+)*)[\.、]?\s+(.*)$")
LIST_RE = re.compile(r"^\s{0,3}(?:[-*+]\s+|\d+[.)]\s+)")
TABLE_SEP_RE = re.compile(r"^\s*\|?[\s:|-]+\|[\s:|-]*$")
FENCE_RE = re.compile(r"^\s*```(\S*)\s*$")
HR_RE = re.compile(r"^\s*(?:-{3,}|\*{3,}|_{3,})\s*$")
IMG_RE = re.compile(r"!\[[^\]]*\]\([^)]*\)")


def _ascii_slug(text: str) -> str:
    """只从标题里取 ASCII 词做可读后缀（中文不转拼音，保持零依赖）。"""
    words = re.findall(r"[A-Za-z][A-Za-z0-9+#._-]*", text)
    if not words:
        return ""
    slug = "-".join(w.lower() for w in words)
    return re.sub(r"[^a-z0-9._-]+", "-", slug).strip("-")[:32]


def section_path(number: Optional[str], level: int, title: str, used: set) -> str:
    """生成大纲节点的语义路径。

    有编号用编号（s1 / s1-1），再拼上标题里的 ASCII 词（-linear），
    这样 `out.s1-1-linear` 既稳定又可读；中文标题无 ASCII 词时只用编号。
    """
    if number:
        base = "s" + number.replace(".", "-")
    elif level == 1:
        base = "root"
    else:
        base = "sec"
    extra = _ascii_slug(title)
    if extra and not base.endswith(extra):
        base = f"{base}-{extra}"
    candidate, i = base, 2
    while candidate in used:
        candidate = f"{base}-{i}"
        i += 1
    used.add(candidate)
    return candidate


def _clean_inline(text: str) -> str:
    """去掉 Markdown 行内标记，用于摘要（不改变原文 body）。"""
    text = IMG_RE.sub("", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    return text.strip()


def summarize(text: str, limit: int = 72) -> str:
    """取首句做摘要，超长截断。"""
    plain = _clean_inline(re.sub(r"\s+", " ", text)).strip()
    first = re.split(r"(?<=[。！？])", plain)[0].strip()
    if len(first) > limit:
        first = first[:limit] + "…"
    return first


def parse_table(lines: List[str]) -> Optional[Dict[str, Any]]:
    """解析 GFM 表格；无分隔行时不认为是表格。"""
    if len(lines) < 2 or not TABLE_SEP_RE.match(lines[1]):
        return None
    def cells(line: str) -> List[str]:
        line = line.strip().strip("|")
        return [c.strip() for c in line.split("|")]
    header = cells(lines[0])
    rows = [cells(l) for l in lines[2:] if l.strip().startswith("|")]
    rows = [r for r in rows if len(r) == len(header)]
    return {"header": header, "rows": rows}


def parse_text(text: str, source: str = "") -> Dict[str, Any]:
    """把整篇 Markdown 切成 sections + blocks。"""
    lines = text.replace("\r\n", "\n").split("\n")

    doc: Dict[str, Any] = {
        "title": "",
        "source": source,
        "sections": [],
        "kg_blocks": [],
        "stats": {},
    }
    sections: List[Dict[str, Any]] = doc["sections"]
    used_paths: set = set()
    stack: List[Dict[str, Any]] = []          # 当前 heading 栈
    current: Optional[Dict[str, Any]] = None
    order_counter: Dict[Optional[str], int] = {}

    i = 0
    n = len(lines)
    while i < n:
        raw = lines[i]
        line = raw.rstrip()
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        # ---- 代码块 / kg 标注块 ----
        fence = FENCE_RE.match(line)
        if fence:
            lang = fence.group(1).lower()
            body: List[str] = []
            i += 1
            while i < n and not lines[i].strip().startswith("```"):
                body.append(lines[i].rstrip())
                i += 1
            i += 1  # 跳过结束围栏
            content = "\n".join(body)
            first_idx = next((k for k, b in enumerate(body) if b.strip()), None)
            first_line = body[first_idx].strip() if first_idx is not None else ""
            if first_line.startswith("kg:"):
                kind = first_line.split(":", 1)[1].strip() or "claim"
                payload = "\n".join(body[first_idx + 1:]) if first_idx is not None else ""
                data = M.parse_kv_block(payload)
                doc["kg_blocks"].append({
                    "kind": kind,
                    "data": data,
                    "sectionPath": current["path"] if current else None,
                    "line": i,
                    "raw": content,
                })
                _add_block(current, doc, {"kind": "kg", "kg": kind, "text": content, "line": i})
            else:
                _add_block(current, doc, {
                    "kind": "code", "lang": lang, "text": content, "line": i,
                })
            continue

        # ---- 分隔线 ----
        if HR_RE.match(line):
            i += 1
            continue

        # ---- 标题 ----
        heading = HEADING_RE.match(line)
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip()
            if not doc["title"]:
                doc["title"] = title
            number, bare = None, title
            m = NUMBER_RE.match(title)
            if m:
                number, bare = m.group(1), m.group(2).strip()
            while stack and stack[-1]["level"] >= level:
                stack.pop()
            parent = stack[-1]["path"] if stack else None
            path = section_path(number, level, title, used_paths)
            siblings = [s for s in sections if s["parentPath"] == parent]
            section = {
                "id": M.outline_id(path),
                "path": path,
                "level": level,
                "title": title,
                "number": number,
                "bareTitle": bare,
                "parentPath": parent,
                "order": len(siblings),
                "blocks": [],
            }
            sections.append(section)
            stack.append(section)
            current = section
            i += 1
            continue

        # ---- 表格 ----
        if stripped.startswith("|"):
            table_lines: List[str] = []
            while i < n and lines[i].strip().startswith("|"):
                table_lines.append(lines[i].strip())
                i += 1
            table = parse_table(table_lines)
            if table:
                _add_block(current, doc, {
                    "kind": "table", "header": table["header"], "rows": table["rows"],
                    "text": "\n".join(table_lines), "line": i,
                })
            continue

        # ---- 列表 ----
        if LIST_RE.match(line):
            items: List[str] = []
            while i < n and (LIST_RE.match(lines[i]) or (lines[i].startswith("  ") and lines[i].strip())):
                if LIST_RE.match(lines[i]):
                    items.append(re.sub(r"^\s*(?:[-*+]|\d+[.)])\s+", "", lines[i]).strip())
                i += 1
            _add_block(current, doc, {"kind": "list", "items": items, "line": i})
            continue

        # ---- 段落 ----
        para: List[str] = []
        while i < n:
            cur = lines[i]
            if not cur.strip() or HEADING_RE.match(cur) or FENCE_RE.match(cur) or cur.strip().startswith("|"):
                break
            if LIST_RE.match(cur) or HR_RE.match(cur):
                break
            para.append(cur.strip())
            i += 1
        if para:
            _add_block(current, doc, {"kind": "para", "text": " ".join(para), "line": i})
        else:
            i += 1
    doc["stats"] = {
        "sections": len(sections),
        "blocks": sum(len(s["blocks"]) for s in sections),
        "kg_blocks": len(doc["kg_blocks"]),
        "claims": sum(1 for b in doc["kg_blocks"] if b["kind"] == "claim"),
        "edges": sum(len(b["data"].get("edges") or []) for b in doc["kg_blocks"] if b["kind"] == "claim")
                 + sum(1 for b in doc["kg_blocks"] if b["kind"] == "edge"),
    }
    return doc


def _add_block(section: Optional[Dict[str, Any]], doc: Dict[str, Any], block: Dict[str, Any]) -> None:
    """把块挂到当前 section；文档级内容（章节前的引言）挂到 `_preamble`。"""
    if section is None:
        doc.setdefault("preamble", []).append(block)
    else:
        section["blocks"].append(block)


def parse_file(path: Path) -> Dict[str, Any]:
    text = Path(path).read_text(encoding="utf-8")
    return parse_text(text, source=str(path))


def prose_blocks(section: Dict[str, Any]) -> List[Dict[str, Any]]:
    """卡片正文只收「可读内容」，不含 kg 精标块（那是判断层的东西）。"""
    return [b for b in section["blocks"] if b["kind"] in ("para", "list", "table", "code")]


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    doc = parse_file(M.SOURCE_MD)
    if "--json" in sys.argv:
        print(json.dumps(doc, ensure_ascii=False, indent=2))
        return 0
    print(f"标题：{doc['title']}")
    print(f"章节：{doc['stats']['sections']}  块：{doc['stats']['blocks']}  "
          f"标注块：{doc['stats']['kg_blocks']}（命题 {doc['stats']['claims']}，边 {doc['stats']['edges']}）")
    print("-" * 60)
    for s in doc["sections"]:
        indent = "  " * (s["level"] - 1)
        kinds = "".join({"para": "p", "list": "l", "table": "t", "code": "c", "kg": "k"}.get(b["kind"], "?")
                        for b in s["blocks"])
        print(f"{indent}{s['id']:<28} {s['title']}   [{kinds}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
