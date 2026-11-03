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
        self.assertEqual(counts, {"outline": 24, "card": 22, "claim": 35, "entity": 44, "edge": 16})

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
        self.assertEqual(data["meta"]["counts"]["claim"], 35)
        self.assertEqual(data["meta"]["counts"], {"outline": 24, "card": 22, "claim": 35,
                                                  "entity": 44, "edge": 16})

    def test_export_assets_and_sql(self):
        r = run(["kg/src/export.py", "--target", "assets"])
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue((REPO / "app" / "src" / "main" / "assets" / "kg" / "graph.json").exists())

        r = run(["kg/src/export.py", "--target", "sql"])
        self.assertEqual(r.returncode, 0, r.stderr)
        sql = (SRC.parent / "data" / "out" / "graph.sql").read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE", sql.upper())

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
