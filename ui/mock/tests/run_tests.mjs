#!/usr/bin/env node
// 知境 UI 原型 · 测试驱动（零依赖，Node 22 原生 + 无头 Chrome）
//
// 用法：
//   node ui/mock/tests/run_tests.mjs
//
// 原理：
//   1. 读 index.html + assertions.js，拼成临时测试页（保证与原型的 DOM 永不漂移）
//   2. 无头 Chrome --dump-dom 加载测试页（普通模式 + ?bare=1 各一次）
//   3. 抓取 <textarea id="zj-test-result"> 里的 JSON，汇总 PASS/FAIL
//   4. 写 ui/mock/tests/results.json，有 FAIL 则退出码 1

import { execFileSync } from 'node:child_process';
import { writeFileSync, existsSync, readFileSync, rmSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const MOCK = resolve(HERE, '..');
const INDEX = join(MOCK, 'index.html');
const ASSERTIONS = join(HERE, 'assertions.js');
const RESULT_FILE = join(HERE, 'results.json');

const CHROME_CANDIDATES = [
  process.env.CHROME,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
].filter(Boolean);

function findChrome() {
  for (const p of CHROME_CANDIDATES) if (existsSync(p)) return p;
  return 'chrome';
}

function buildTestPage() {
  const html = readFileSync(INDEX, 'utf8');
  const asserts = readFileSync(ASSERTIONS, 'utf8');
  // 在 </body> 前注入断言脚本（mock.js 已在 index.html 里，先于断言执行）
  const inject = '<script>' + asserts + '</script>\n</body>';
  if (!html.includes('</body>')) throw new Error('index.html 缺少 </body>');
  const page = html.replace('</body>', inject);

  // 写到临时目录，并把 mock.js / mock.css 一并复制过去（index.html 用相对路径引用它们）
  const dir = mkdtempSync(join(tmpdir(), 'zj-ui-test-'));
  const file = join(dir, 'test.html');
  writeFileSync(file, page, 'utf8');
  for (const asset of ['mock.js', 'mock.css']) {
    writeFileSync(join(dir, asset), readFileSync(join(MOCK, asset)));
  }
  return { file, dir };
}

function runHarness(file, query) {
  const chrome = findChrome();
  const url = 'file:///' + file.replace(/\\/g, '/') + query;
  const args = [
    '--headless=new', '--disable-gpu', '--hide-scrollbars',
    '--window-size=390,844', '--virtual-time-budget=3000',
    '--dump-dom', url,
  ];
  return execFileSync(chrome, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

function extractResult(html) {
  const m = html.match(/<script id="zj-test-result"[^>]*>([\s\S]*?)<\/script>/);
  if (!m) {
    const err = html.match(/__zj_test_error\s*=\s*([^;\n]*)/);
    throw new Error('未找到 #zj-test-result（脚本未跑完？）' + (err ? ' · ' + err[1] : ''));
  }
  return JSON.parse(m[1]);
}

function main() {
  const { file, dir } = buildTestPage();
  try {
    const normal = extractResult(runHarness(file, ''));
    const bare = extractResult(runHarness(file, '?bare=1'));

    // bare 模式下第 12 条才真正成立，用 bare 的结果覆盖
    const results = normal.results.map((r) => {
      if (r.name.startsWith('12.')) return bare.results.find((x) => x.name === r.name) || r;
      return r;
    });

    const failed = results.filter((r) => !r.pass);
    const summary = {
      total: results.length,
      passed: results.length - failed.length,
      failed: failed.length,
      ok: failed.length === 0,
      results,
    };
    writeFileSync(RESULT_FILE, JSON.stringify(summary, null, 2), 'utf8');

    console.log(`知境 UI 原型测试：${summary.passed}/${summary.total} 通过`);
    for (const f of failed) console.log(`  ✗ ${f.name}  ——  ${f.detail}`);
    if (failed.length) {
      console.log(`结果已写入 ${RESULT_FILE}`);
      process.exit(1);
    }
    console.log('全部通过 ✓');
    console.log(`结果已写入 ${RESULT_FILE}`);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

try {
  main();
} catch (e) {
  console.error('测试运行失败：' + e.message);
  process.exit(2);
}
