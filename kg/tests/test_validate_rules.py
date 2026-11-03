#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验规则反向测试：证明 validate_graph 的每条规则「不是恒真摆设」。

思路：从 build_graph 产物出发，逐一注入一类真实会犯的错误，断言对应规则变红。
注入器复用 selftest.py 的 CASES（避免两处维护），并补上 selftest 没覆盖的规则 9/10。

运行：python -m unittest discover -s kg/tests -v
"""

from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

import build_graph as B
import kg_model as M
import selftest as ST
import validate_graph as V


def _fresh_graph():
    """每次取一份全新的 build 产物，避免测试之间互相污染。"""
    return B.build()


class TestValidatorPositive(unittest.TestCase):
    def test_clean_graph_passes(self):
        report = V.validate(_fresh_graph())
        self.assertTrue(report.ok, "干净图谱应全部通过：\n" +
                        "\n".join(f"[{c['no']}] {c['title']}: {c['failures'][:2]}"
                                  for c in report.checks if not c["passed"]))

    def test_counts_match_expected(self):
        g = _fresh_graph()
        c = g["meta"]["counts"]
        self.assertEqual(c["outline"], 24)
        self.assertEqual(c["card"], 22)
        self.assertEqual(c["claim"], 35)
        self.assertEqual(c["entity"], 44)
        self.assertEqual(c["edge"], 16)


class TestValidatorNegative(unittest.TestCase):
    """规则 1–8：复用 selftest 注入器，断言对应规则号确实变红。"""

    @classmethod
    def setUpClass(cls):
        cls.graph = _fresh_graph()

    def _assert_rule_fails(self, inject, expected_no, msg):
        broken = copy.deepcopy(self.graph)
        detail = inject(broken)
        report = V.validate(broken)
        failed = {c["no"] for c in report.checks if not c["passed"]}
        self.assertIn(expected_no, failed,
                      f"{msg} 应命中规则 {expected_no}，实际命中 {sorted(failed)}（注入：{detail}）")

    def test_rule3_dangling_edge(self):
        self._assert_rule_fails(ST.inject_dangling_edge, "3", "悬空边")

    def test_rule4_edge_without_boundary(self):
        self._assert_rule_fails(ST.inject_edge_without_boundary, "4", "边缺 boundary")

    def test_rule5_too_many_crosslinks(self):
        self._assert_rule_fails(ST.inject_too_many_crosslinks, "5", "交叉链接超上限")

    def test_rule8_claim_in_navigation(self):
        self._assert_rule_fails(ST.inject_claim_in_navigation, "8", "导航并列深度入口")

    def test_rule7_bad_state(self):
        self._assert_rule_fails(ST.inject_bad_state, "7", "探测状态越界")

    def test_rule2_double_owner(self):
        self._assert_rule_fails(ST.inject_double_owner, "2", "命题一题两主")

    def test_rule6_leak_to_breadth(self):
        self._assert_rule_fails(ST.inject_leak_to_breadth, "6", "第一屏泄露条件")

    def test_rule1_wrong_id_hash(self):
        self._assert_rule_fails(ST.inject_wrong_id_hash, "1", "ID 哈希与正文不符")


class TestValidatorNegativeExtra(unittest.TestCase):
    """补 selftest 未覆盖的规则 9（结构完整/证据溯源）与规则 10（JSON Schema）。"""

    @classmethod
    def setUpClass(cls):
        cls.graph = _fresh_graph()

    def test_rule9_missing_evidence(self):
        """规则 9 校验命题/边必须有 evidence。清空一条命题的 evidence。"""
        broken = copy.deepcopy(self.graph)
        broken["claims"][0]["evidence"] = ""
        report = V.validate(broken)
        failed = {c["no"] for c in report.checks if not c["passed"]}
        self.assertIn("9", failed)

    def test_rule10_schema_violation(self):
        """规则 10 走 JSON Schema。给某张卡塞一个 schema 未声明的类型字段。

        注意：规则 10 只在装了 jsonschema 时才真正执行，否则标记跳过（note）。
        这里先探测是否可用，不可用则跳过本断言（不误报失败）。
        """
        try:
            import jsonschema  # noqa: F401
        except Exception:
            self.skipTest("未安装 jsonschema，规则 10 本就跳过，无法反向验证")
        broken = copy.deepcopy(self.graph)
        # 把一张卡的 title 改成数字类型：schema 里 title 应为 string
        broken["cards"][0]["title"] = 12345
        report = V.validate(broken)
        failed = {c["no"] for c in report.checks if not c["passed"]}
        self.assertIn("10", failed)


if __name__ == "__main__":
    unittest.main()
