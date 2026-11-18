#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""三元组候选闸门与生成脚本的离线单测。

全部离线：不调云端接口、不读写仓库数据（合并用例写临时文件）。
运行：python -m unittest discover -s kg/tests -v
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "src"
sys.path.insert(0, str(SRC))

import gen_triples  # noqa: E402
import triple_gate  # noqa: E402

CLAIMS = {
    "clm.demo.linear.0001": {
        "id": "clm.demo.linear.0001",
        "statement": "输入最后一维必须等于权重输入维",
        "breadth": {"cardId": "crd.demo"},
    }
}
CARD_ID = "crd.demo"
CLAIM_ID = "clm.demo.linear.0001"


def valid_computable() -> dict:
    return {
        "id": "tri.demo.linear.1",
        "claimId": CLAIM_ID,
        "cardId": CARD_ID,
        "statement": "输入最后一维必须等于权重输入维",
        "kind": "shape",
        "mechanism": "矩阵乘要沿内维归约，内维不等时求和未定义",
        "computable": True,
        "interventions": [
            {
                "id": "inv.demo.4-to-5",
                "action": "把输入最后一维 4 改成 5",
                "overrides": {"in_x": 5},
                "contract": {
                    "symbols": {"N": 8, "in_x": 4, "in_w": 4},
                    "op": {"kind": "matmul", "lhs": ["N", "in_x"], "rhs": ["in_w", "in_w"]},
                    "constraints": [
                        {"kind": "equals", "left": "in_x", "right": "in_w", "describe": "内维相等"}
                    ],
                },
                "consequence": "内维 5 与 4 不等，运算未定义",
                "source": "COMPUTED",
                "misjudgements": [
                    {
                        "predicted": "HOLDS",
                        "why": "把内维当成结果维",
                        "correction": "内维是要消掉的那一维，必须相等才能求和",
                    }
                ],
            },
            {
                "id": "inv.demo.batch",
                "action": "把 batch 8 改成 160",
                "overrides": {"N": 160},
                "contract": {
                    "symbols": {"N": 8, "in_x": 4, "in_w": 4},
                    "op": {"kind": "matmul", "lhs": ["N", "in_x"], "rhs": ["in_w", "in_w"]},
                    "constraints": [
                        {"kind": "equals", "left": "in_x", "right": "in_w", "describe": "内维相等"}
                    ],
                },
                "consequence": "batch 是前导维，命题仍成立",
                "source": "COMPUTED",
                "misjudgements": [
                    {
                        "predicted": "FAILS",
                        "why": "以为输入一变就破坏形状",
                        "correction": "约束只作用于最后一维，前导维可以任意变",
                    }
                ],
            },
        ],
    }


def valid_preset() -> dict:
    return {
        "id": "tri.demo.preset.1",
        "claimId": CLAIM_ID,
        "cardId": CARD_ID,
        "statement": "ReLU 并非处处可微",
        "kind": "gradient",
        "mechanism": "x=0 处左导 0、右导 1，两条单侧导数不等",
        "computable": False,
        "interventions": [
            {
                "id": "inv.demo.zero",
                "action": "只考察 x = 0",
                "outcome": "FAILS",
                "consequence": "导数不存在，框架回传 0 是次梯度约定",
                "source": "AUTHORED_HUMAN",
                "misjudgements": [
                    {
                        "predicted": "HOLDS",
                        "why": "把框架能反传当成可微",
                        "correction": "次梯度约定能让计算继续，但不等于左右导数相等",
                    }
                ],
            },
            {
                "id": "inv.demo.positive",
                "action": "只考察 x > 0",
                "outcome": "WEAKENED",
                "consequence": "正半轴导数恒为 1，结论只剩一半",
                "source": "AUTHORED_HUMAN",
                "misjudgements": [
                    {
                        "predicted": "HOLDS",
                        "why": "以为收窄范围不改变强度",
                        "correction": "适用范围变窄，能断言的东西少了",
                    }
                ],
            },
        ],
    }


class TestTripleGate(unittest.TestCase):
    def check(self, candidate) -> list:
        return triple_gate.check_candidate(candidate, CLAIMS)

    def test_valid_computable_passes(self):
        self.assertEqual([], self.check(valid_computable()))

    def test_valid_preset_passes(self):
        self.assertEqual([], self.check(valid_preset()))

    def test_missing_mechanism_rejected(self):
        candidate = valid_computable()
        candidate["mechanism"] = "  "
        reasons = self.check(candidate)
        self.assertTrue(any("机制" in r for r in reasons), reasons)

    def test_bad_kind_rejected(self):
        candidate = valid_computable()
        candidate["kind"] = "shapey"
        self.assertTrue(any("kind" in r for r in self.check(candidate)))

    def test_computable_without_contract_rejected(self):
        candidate = valid_computable()
        candidate["interventions"][0].pop("contract")
        reasons = self.check(candidate)
        self.assertTrue(any("没带 contract" in r for r in reasons), reasons)

    def test_constraint_symbol_not_declared(self):
        candidate = valid_computable()
        candidate["interventions"][0]["contract"]["constraints"][0]["left"] = "in_h"
        reasons = self.check(candidate)
        self.assertTrue(any("in_h" in r for r in reasons), reasons)

    def test_op_symbol_not_declared(self):
        candidate = valid_computable()
        candidate["interventions"][0]["contract"]["op"]["lhs"] = ["N", "in_h"]
        reasons = self.check(candidate)
        self.assertTrue(any("in_h" in r for r in reasons), reasons)

    def test_preset_with_contract_rejected(self):
        candidate = valid_preset()
        candidate["interventions"][0]["contract"] = {"symbols": {"N": 8}}
        reasons = self.check(candidate)
        self.assertTrue(any("不该带 contract" in r for r in reasons), reasons)

    def test_override_symbol_not_declared(self):
        candidate = valid_computable()
        candidate["interventions"][0]["overrides"] = {"in_h": 3}
        reasons = self.check(candidate)
        self.assertTrue(any("in_h" in r for r in reasons), reasons)

    def test_too_few_interventions(self):
        candidate = valid_preset()
        candidate["interventions"] = candidate["interventions"][:1]
        self.assertTrue(any("干预点少于" in r for r in self.check(candidate)))

    def test_bad_card_id_rejected(self):
        candidate = valid_computable()
        candidate["cardId"] = "crd.other"
        self.assertTrue(any("cardId" in r for r in self.check(candidate)))

    def test_unknown_claim_rejected(self):
        candidate = valid_computable()
        candidate["claimId"] = "clm.nope"
        self.assertTrue(any("不存在" in r for r in self.check(candidate)))

    def test_report_counts(self):
        rows = [
            (valid_computable(), []),
            (valid_preset(), ["缺少字段 mechanism"]),
        ]
        report = triple_gate.render_report(rows)
        self.assertIn("候选总数：2", report)
        self.assertIn("通过：1", report)
        self.assertIn("拒绝明细", report)

    def test_apply_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            supplement = Path(tmp) / "deep_supplement.json"
            supplement.write_text('{"meta": {}, "triples": []}\n', encoding="utf-8")

            added, skipped = triple_gate.apply_candidates([valid_computable()], supplement)
            self.assertEqual((1, 0), (added, skipped))

            added, skipped = triple_gate.apply_candidates([valid_computable()], supplement)
            self.assertEqual((0, 1), (added, skipped))

            data = json.loads(supplement.read_text(encoding="utf-8"))
            self.assertEqual(1, len(data["triples"]))
            self.assertEqual("2026-11-18", data["meta"]["updatedAt"])

    def test_apply_dry_run_does_not_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            supplement = Path(tmp) / "deep_supplement.json"
            supplement.write_text('{"triples": []}\n', encoding="utf-8")

            added, _ = triple_gate.apply_candidates([valid_computable()], supplement, dry_run=True)
            self.assertEqual(1, added)
            self.assertEqual([], json.loads(supplement.read_text(encoding="utf-8"))["triples"])


class TestGenTriplesHelpers(unittest.TestCase):
    def test_system_prompt_extracted_from_markdown(self):
        markdown = gen_triples.PROMPT_PATH.read_text(encoding="utf-8")
        prompt = gen_triples.extract_system_prompt(markdown)
        self.assertGreater(len(prompt), 100)
        self.assertIn("JSON", prompt)

    def test_parse_candidate_strips_fence_and_wrapper(self):
        raw = '好的，结果如下：\n```json\n{"triples": [{"id": "tri.x", "kind": "shape"}]}\n```'
        parsed = gen_triples.parse_candidate(raw)
        self.assertEqual("tri.x", parsed["id"])

    def test_parse_candidate_rejects_plain_text(self):
        with self.assertRaises(ValueError):
            gen_triples.parse_candidate("我不确定，这条命题不适合抽取。")

    def test_normalize_overrides_ownership(self):
        claim = CLAIMS[CLAIM_ID]
        candidate = {"id": "tri.x", "claimId": "clm.other", "cardId": "crd.other", "kind": "shape"}
        fixed = gen_triples.normalize_candidate(candidate, claim, "statistics")
        self.assertEqual(CLAIM_ID, fixed["claimId"])
        self.assertEqual(CARD_ID, fixed["cardId"])
        # 模型给了 kind 就尊重它，没给才用本地推断
        self.assertEqual("shape", fixed["kind"])

    def test_pending_claims_skips_covered(self):
        graph = {
            "claims": [{"id": CLAIM_ID, "statement": "x", "breadth": {"cardId": CARD_ID}}],
            "triples": [{"claimId": CLAIM_ID}],
            "edges": [],
        }
        self.assertEqual([], gen_triples.pending_claims(graph))
        self.assertEqual(1, len(gen_triples.pending_claims(graph, include_existing=True)))


if __name__ == "__main__":
    unittest.main()
