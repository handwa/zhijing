#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""端到端管线测试：通过 subprocess 跑真实 CLI，验证 build→validate→export 链路。

验证点：
  - build_graph 能落盘、validate 判定 ok=true、退出码 0
  - counts 回归锚点（语料被破坏时立刻报警）
  - export web 产物可被反解、assets/sql 产物生成
  - 幂等性：连跑两次 build，graph.json 逐字节一致

运行：python -m unittest discover -s kg/tests -v
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

PY = sys.executable
SRC = Path(__file__).resolve().parent.parent / "src"
REPO = SRC.parent.parent


def run(args, cwd=REPO):
    return subprocess.run([PY, *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8")


class TestPipeline(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix="zj-kg-test-"))
        cls.graph_out = cls.tmp / "graph.json"

    def test_build_then_validate_ok(self):
        r = run(["kg/src/build_graph.py", "--out", str(self.graph_out), "--quiet"])
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(self.graph_out.exists())

        v = run(["kg/src/validate_graph.py", "--graph", str(self.graph_out), "--json"])
        self.assertEqual(v.returncode, 0, v.stderr)
        data = json.loads(v.stdout)
        self.assertTrue(data["ok"], f"validate 判定未通过：{data['checks']}")

    def test_counts_anchor(self):
        # 语料是「gold」，counts 是回归锚点：任何意外增删节点都会被这里抓住
        v = run(["kg/src/validate_graph.py", "--graph", str(self.graph_out), "--json"])
        counts = json.loads(v.stdout)["counts"]
        self.assertEqual(counts, {"outline": 24, "card": 22, "claim": 36, "entity": 44, "edge": 16})

    def test_export_web(self):
        web = self.tmp / "graph-data.js"
        # export.py 默认写到 kg/web/graph-data.js，这里只验证其内容形态：
        # 直接读仓库里已被 build 覆盖的产物，而非改 export 的目标路径（export 不支持 --out）
        r = run(["kg/src/export.py", "--target", "web"])
        self.assertEqual(r.returncode, 0, r.stderr)
        out = (SRC.parent / "web" / "graph-data.js").read_text(encoding="utf-8")
        # 产物带 // 注释头，去掉注释与 window.KG = 前缀后应能反解出合法 JSON
        body = out.split("=", 1)[1].rstrip().rstrip(";").strip()
        data = json.loads(body)
        self.assertEqual(data["meta"]["counts"]["claim"], 36)
        self.assertEqual(data["meta"]["counts"], {"outline": 24, "card": 22, "claim": 36,
                                                 "entity": 44, "edge": 16})

    def test_export_assets_and_sql(self):
        r = run(["kg/src/export.py", "--target", "assets"])
        self.assertEqual(r.returncode, 0, r.stderr)
        assets = REPO / "app" / "src" / "main" / "assets" / "kg" / "graph.json"
        self.assertTrue(assets.exists())

        # 增补源（前置关系 + 条件三元组）必须在导出后仍然存在。
        # 这条守的是一个真实事故：这些内容曾经只活在 assets 里，跑一次 export 就被
        # build 产物整份覆盖，深度侧的「预测 → 揭示」直接变空。
        exported = json.loads(assets.read_text(encoding="utf-8"))
        self.assertTrue(exported["prerequisites"], "导出后前置关系丢失")
        self.assertTrue(exported["triples"], "导出后条件三元组丢失")

        r = run(["kg/src/export.py", "--target", "sql"])
        self.assertEqual(r.returncode, 0, r.stderr)
        sql = (SRC.parent / "data" / "out" / "graph.sql").read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE", sql.upper())

    def test_supplement_references_existing_nodes(self):
        """增补源里每一条都必须指到真实存在的卡片/命题，否则导出时会被静默丢弃。"""
        supplement_path = SRC.parent / "data" / "source" / "deep_supplement.json"
        self.assertTrue(supplement_path.exists(), "缺少深度内容增补源")

        graph = json.loads(self.graph_out.read_text(encoding="utf-8"))
        claim_ids = {c["id"] for c in graph["claims"]}
        card_ids = {c["id"] for c in graph["cards"]}
        supplement = json.loads(supplement_path.read_text(encoding="utf-8"))

        for p in supplement.get("prerequisites", []):
            self.assertIn(p["from"], card_ids, f"前置起点不存在：{p}")
            self.assertIn(p["to"], card_ids, f"前置终点不存在：{p}")

        for t in supplement.get("triples", []):
            self.assertIn(t["claimId"], claim_ids, f"三元组指向不存在的命题：{t['id']}")
            self.assertIn(t["cardId"], card_ids, f"三元组指向不存在的卡片：{t['id']}")
            self.assertTrue(t["interventions"], f"三元组没有干预点：{t['id']}")
            for inv in t["interventions"]:
                self.assertTrue(inv["misjudgements"], f"干预点缺误判库：{inv['id']}")

        # 语料更新后这些数字不该悄悄变。
        # 第二批又补了 6 条（交叉熵 / BN 小 batch / ReLU 接 Softmax / 缩放 / 掩码顺序 / 多头共享输入），
        # 每条 4 个干预点，全部是预置答案（契约引擎表达不了这些机制）。
        self.assertEqual(len(supplement["triples"]), 12)
        self.assertEqual(sum(len(t["interventions"]) for t in supplement["triples"]), 48)

        # 深度边：22 条补边，端点必须是真实命题，且带条件三段（否则揭示屏没东西可讲）
        edges = supplement.get("edges", [])
        self.assertEqual(len(edges), 22)
        for e in edges:
            self.assertIn(e["from"], claim_ids, f"边起点不存在：{e['id']}")
            self.assertIn(e["to"], claim_ids, f"边终点不存在：{e['id']}")
            self.assertNotEqual(e["from"], e["to"], f"边不能是自环：{e['id']}")
            condition = e["condition"]
            self.assertTrue(
                condition.get("premise") or condition.get("boundary"),
                f"边缺前提与边界：{e['id']}",
            )

    def test_idempotent_build(self):
        """连跑两次 build，产物应一致（忽略 generatedAt 时间戳）—— 哈希稳定性的最高层验证。"""
        a = self.tmp / "a.json"
        b = self.tmp / "b.json"
        run(["kg/src/build_graph.py", "--out", str(a), "--quiet"])
        run(["kg/src/build_graph.py", "--out", str(b), "--quiet"])

        ga = json.loads(a.read_text(encoding="utf-8"))
        gb = json.loads(b.read_text(encoding="utf-8"))
        # generatedAt 是运行时刻，必然不同；其余字段必须逐字节一致
        ga["meta"]["generatedAt"] = gb["meta"]["generatedAt"] = "<fixed>"
        self.assertEqual(ga, gb)


if __name__ == "__main__":
    unittest.main()
