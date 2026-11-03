// 知境 UI 原型 · 断言脚本（被 run_tests.mjs 注入到 index.html 之后执行）
// 不依赖任何框架，只操作 DOM 与 mock.js 暴露的状态。
(function () {
  'use strict';

  window.__zj_test_error = null;
  window.__zj_test_done = false;

  function writeResult(summary) {
    var out = document.getElementById('zj-test-result');
    if (!out) {
      out = document.createElement('script');
      out.id = 'zj-test-result';
      out.type = 'application/json';
      document.body.appendChild(out);
    }
    // script[type=application/json] 的内容不被执行也不被转义，dump-dom 原样输出
    out.textContent = JSON.stringify(summary);
  }

  var results = [];
  function check(name, pass, detail) {
    results.push({ name: name, pass: !!pass, detail: detail || '' });
  }
  function qs(sel) { return document.querySelector(sel); }
  function qsa(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }
  function click(el) {
    el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
  }
  function activeScreen() {
    var s = qs('.screen.is-active');
    if (!s) return null;
    if (!s.id) {
      // 诊断：命中元素却没有 id，把现场写进结果
      window.__zj_active_bug = 'tag=' + s.tagName + ' cls=' + s.className;
    }
    return s.id.replace('screen-', '');
  }
  function activeTab() {
    var t = qs('.tab.is-active');
    return t ? t.dataset.page : null;
  }
  function esc() {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  }

  try {
  // ── 1. 初始态 ──────────────────────────────────────────────
  check('1. 初始态：首页激活', activeScreen() === 'home', '实际=' + activeScreen());
  check('1. 初始态：home tab 高亮', activeTab() === 'home', '实际=' + activeTab());

  // ── 2. 切主页面（tab） ─────────────────────────────────────
  click(qs('.tab[data-page="garden"]'));
  check('2. 点 garden tab → 庭院激活', activeScreen() === 'garden', '实际=' + activeScreen());
  check('2. garden tab 高亮', activeTab() === 'garden', '实际=' + activeTab());

  // ── 3. 推入二级页 detail ───────────────────────────────────
  click(qs('.tab[data-page="home"]'));
  click(qs('[data-goto="detail"]'));
  check('3. 点卡片 → 详情激活', activeScreen() === 'detail', '实际=' + activeScreen());
  check('3. detail 不抢 tab，仍高亮 home', activeTab() === 'home', '实际=' + activeTab());

  // ── 4. 返回栈 ─────────────────────────────────────────────
  click(qs('[data-goto="compare"]'));
  check('4a. 详情→对照', activeScreen() === 'compare', '实际=' + activeScreen());
  click(qs('[data-back]')); // 回到 detail
  check('4b. back → 详情', activeScreen() === 'detail', '实际=' + activeScreen());
  click(qs('[data-back]')); // 回到 home
  check('4c. back → 首页', activeScreen() === 'home', '实际=' + activeScreen());
  // Esc 等价返回
  click(qs('[data-goto="detail"]'));
  esc();
  check('4d. Esc → 返回首页', activeScreen() === 'home', '实际=' + activeScreen());

  // ── 5. 主题切换 ───────────────────────────────────────────
  click(qs('#themeSeg button[data-theme="dark"]'));
  var theme = document.documentElement.getAttribute('data-theme');
  check('5. 切深色 → html[data-theme]=dark', theme === 'dark', '实际=' + theme);
  click(qs('#themeSeg button[data-theme="light"]'));
  check('5. 切回浅色', document.documentElement.getAttribute('data-theme') === 'light');

  // ── 6. 字号缩放 ───────────────────────────────────────────
  click(qs('#scaleSeg button[data-scale="1.3"]'));
  var scale = qs('#phone').style.getPropertyValue('--scale');
  check('6. 字号 130% → #phone --scale=1.3', scale === '1.3', '实际=' + scale);

  // ── 7. 问答状态 ───────────────────────────────────────────
  click(qs('.tab[data-page="chat"]'));
  click(qs('#chatSeg button[data-chat="empty"]'));
  var streamHidden = qs('#answerStream').hidden;
  var emptyShown = !qs('#answerEmpty').hidden;
  var askText = qs('#userAsk').textContent;
  check('7. 空态 → answerStream 隐藏', streamHidden === true);
  check('7. 空态 → answerEmpty 显示', emptyShown === true);
  check('7. 空态 → 提问文案切换', askText.indexOf('扩散模型') >= 0, '实际=' + askText);
  click(qs('#chatSeg button[data-chat="stream"]'));
  check('7. 切回回答中 → stream 显示', !qs('#answerStream').hidden && qs('#answerEmpty').hidden);

  // ── 8. 开关 ───────────────────────────────────────────────
  var sw = qs('.switch');
  var beforeChecked = sw.getAttribute('aria-checked');
  click(sw);
  var afterChecked = sw.getAttribute('aria-checked');
  check('8. 开关点击 → aria-checked 翻转', beforeChecked !== afterChecked,
        beforeChecked + '→' + afterChecked);
  check('8. 开关 .is-on 同步', sw.classList.contains('is-on') === (afterChecked === 'true'));

  // ── 9. 反馈按钮 ───────────────────────────────────────────
  click(qs('.tab[data-page="home"]'));
  var fbs = qsa('.fb-btn');
  click(fbs[1]); // 点「平常」
  var color = fbs[1].style.color;
  var siblingCleared = (fbs[0].style.color === '') && (fbs[2].style.color === '');
  check('9. 反馈选中 → 当前按钮变绿', color.indexOf('var(--leaf)') >= 0, '实际=' + color);
  check('9. 兄弟按钮清除', siblingCleared === true);

  // ── 10. 四态角标存在性 ────────────────────────────────────
  check('10. badge--mastered 存在', qsa('.badge--mastered').length >= 1);
  check('10. badge--confused 存在', qsa('.badge--confused').length >= 1);
  click(qs('.tab[data-page="garden"]'));
  check('10. 庭院 .node--focus 存在', qsa('.node--focus').length >= 1);

  // ── 11. 深色冠层提亮 ──────────────────────────────────────
  click(qs('#themeSeg button[data-theme="dark"]'));
  var canopy = qs('.canopy');
  var opacity = canopy ? window.getComputedStyle(canopy).opacity : null;
  check('11. dark 下 .canopy opacity=0.8', opacity === '0.8', '实际=' + opacity);
  click(qs('#themeSeg button[data-theme="light"]'));

  // ── 12. ?bare=1 出图模式 ──────────────────────────────────
  check('12. bare 模式：body.bare 存在', document.body.classList.contains('bare'),
        '（当前 URL ' + location.search + '）');

  var summary = {
    total: results.length,
    passed: results.filter(function (r) { return r.pass; }).length,
    results: results
  };
  writeResult(summary);
  window.__zj_test_done = true;
  } catch (e) {
    window.__zj_test_error = e.message + ' @ ' + (e.stack || '').split('\n')[1];
    writeResult({ total: 0, passed: 0, error: window.__zj_test_error, results: results });
  }
})();

