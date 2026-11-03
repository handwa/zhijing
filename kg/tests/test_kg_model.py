#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""kg_model 单元测试：文本规范化 / 哈希 / ID 生成校验 / 标注块解析器。

只测「纯函数、无 IO」的部分；链路测试见 test_pipeline.py。
运行：python -m unittest discover -s kg/tests -v
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

# kg/src 平铺脚本，无包结构，手动加入 import 路径
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

import kg_model as M


class TestNormalize(unittest.TestCase):
    def test_strips_whitespace_and_punctuation(self):
        self.assertEqual(M.normalize("Softmax 输出沿类别维求和为 1。"), "softmax输出沿类别维求和为1")

    def test_fullwidth_space(self):
        self.assertEqual(M.normalize("a\u3000b"), "ab")

    def test_lowercase(self):
        self.assertEqual(M.normalize("ReLU"), "relu")

    def test_empty(self):
        self.assertEqual(M.normalize(""), "")
        self.assertEqual(M.normalize("   "), "")

    def test_punctuation_only(self):
        self.assertEqual(M.normalize("，。！？"), "")


class TestShortHash(unittest.TestCase):
    def test_stable(self):
        a = M.short_hash("ReLU 后面接 Softmax")
        b = M.short_hash("ReLU 后面接 Softmax")
        self.assertEqual(a, b)

    def test_length(self):
        self.assertEqual(len(M.short_hash("x", 4)), 4)
        self.assertEqual(len(M.short_hash("x", 8)), 8)

    def test_punctuation_does_not_change_hash(self):
        self.assertEqual(M.short_hash("ReLU 后面接 Softmax"), M.short_hash("ReLU，后面接 Softmax！"))

    def test_word_change_changes_hash(self):
        self.assertNotEqual(M.short_hash("ReLU"), M.short_hash("GELU"))


class TestIds(unittest.TestCase):
    def test_claim_id_format_and_stability(self):
        cid = M.claim_id("arch.softmax-sum", "Softmax 输出沿类别维求和为 1。")
        self.assertTrue(cid.startswith("clm.arch.softmax-sum."))
        self.assertEqual(cid, M.claim_id("arch.softmax-sum", "Softmax 输出沿类别维求和为 1。"))

    def test_claim_id_punctuation_keeps_id(self):
        a = M.claim_id("arch.x", "ReLU 后面接 Softmax")
        b = M.claim_id("arch.x", "ReLU，后面接 Softmax！")
        self.assertEqual(a, b)

    def test_claim_id_word_change_changes_id(self):
        a = M.claim_id("arch.x", "ReLU 后面接 Softmax")
        b = M.claim_id("arch.x", "GELU 后面接 Softmax")
        self.assertNotEqual(a, b)

    def test_entity_id_format(self):
        eid = M.entity_id("method", "ReLU")
        self.assertTrue(eid.startswith("ent.method."))
        self.assertEqual(eid, M.entity_id("method", "ReLU"))

    def test_edge_id_format_and_sensitivity(self):
        e1 = M.edge_id("clm.a", "clm.b", "depends_on")
        e2 = M.edge_id("clm.a", "clm.b", "depends_on")
        e3 = M.edge_id("clm.a", "clm.c", "depends_on")
        e4 = M.edge_id("clm.a", "clm.b", "affects")
        self.assertTrue(e1.startswith("edg.dep."))
        self.assertEqual(e1, e2)
        self.assertNotEqual(e1, e3)
        self.assertNotEqual(e1, e4)

    def test_outline_and_card_index(self):
        self.assertEqual(M.outline_id("transformer"), "out.transformer")
        self.assertEqual(M.card_id("arch", 1), "crd.arch")
        self.assertEqual(M.card_id("arch", 2), "crd.arch.2")


class TestIsValidId(unittest.TestCase):
    def test_valid_ids(self):
        self.assertTrue(M.is_valid_id("out.transformer"))
        self.assertTrue(M.is_valid_id("crd.arch.2"))
        self.assertTrue(M.is_valid_id("clm.arch.x.a702"))
        self.assertTrue(M.is_valid_id("ent.method.relu.ab12"))
        self.assertTrue(M.is_valid_id("edg.dep.1a2b3c"))

    def test_claim_requires_hash(self):
        self.assertFalse(M.is_valid_id("clm.arch.x"))
        self.assertFalse(M.is_valid_id("ent.method.relu"))
        self.assertFalse(M.is_valid_id("edg.dep"))

    def test_outline_card_no_hash_needed(self):
        self.assertTrue(M.is_valid_id("out.root-transformer"))
        self.assertTrue(M.is_valid_id("crd.root-transformer"))

    def test_reject_non_string_and_garbage(self):
        self.assertFalse(M.is_valid_id(123))
        self.assertFalse(M.is_valid_id(""))
        self.assertFalse(M.is_valid_id("CLM.arch.x.a702"))  # 前缀必须小写


class TestParseKvBlock(unittest.TestCase):
    def test_map_and_list(self):
        parsed = M.parse_kv_block("statement: 你好。\nconditions:\n  - 条件一\n  - 条件二\n")
        self.assertEqual(parsed["statement"], "你好。")
        self.assertEqual(parsed["conditions"], ["条件一", "条件二"])

    def test_list_of_maps(self):
        parsed = M.parse_kv_block(
            "edges:\n"
            "  - to: clm.arch.softmax-shift\n"
            "    type: depends_on\n"
            "    kind: statistics\n"
        )
        self.assertEqual(parsed["edges"][0]["to"], "clm.arch.softmax-shift")
        self.assertEqual(parsed["edges"][0]["type"], "depends_on")

    def test_empty_value_then_list(self):
        parsed = M.parse_kv_block("conditions:\n  - a\n  - b\n")
        self.assertEqual(parsed["conditions"], ["a", "b"])

    def test_scalar_types(self):
        parsed = M.parse_kv_block("expected: true\nweight: 0.9\nn: 3\n")
        self.assertIs(parsed["expected"], True)
        self.assertAlmostEqual(parsed["weight"], 0.9)
        self.assertEqual(parsed["n"], 3)

    def test_inconsistent_indent_raises(self):
        with self.assertRaises(M.KgBlockError):
            M.parse_kv_block("conditions:\n  - a\n    - b\n")

    def test_missing_colon_raises(self):
        with self.assertRaises(M.KgBlockError):
            M.parse_kv_block("没有冒号的一行\n")

    def test_parse_list_value(self):
        self.assertEqual(M.parse_list_value({"k": [1, 2]}, "k"), [1, 2])
        self.assertEqual(M.parse_list_value({"k": 1}, "k"), [1])  # 单值容错
        self.assertEqual(M.parse_list_value({}, "k"), [])


if __name__ == "__main__":
    unittest.main()
