/* =============================================================================
   知境 · UI 原型交互
   -----------------------------------------------------------------------------
   只做原型需要的四件事：切页（含返回栈）、切主题、切字号、切问答状态。
   真正的产品逻辑（解析、检索、生成）不在这里。
   ============================================================================= */
(function () {
  'use strict';

  // ?bare=1：出图模式（见 mock.css 末尾），评审控制台与设备外壳不参与渲染
  if (/[?&]bare=1\b/.test(location.search)) document.body.classList.add('bare');

  var phone = document.getElementById('phone');
  var viewport = document.getElementById('viewport');
  var screens = {};
  Array.prototype.forEach.call(document.querySelectorAll('.screen'), function (s) {
    screens[s.id.replace('screen-', '')] = s;
  });

  var TAB_PAGES = { home: 1, chat: 1, garden: 1, settings: 1 };
  var stack = ['home'];

  function current() { return stack[stack.length - 1]; }

  function show(page, push) {
    if (!screens[page]) return;
    Object.keys(screens).forEach(function (k) {
      screens[k].classList.toggle('is-active', k === page);
    });
    if (push !== false) stack.push(page);

    // 底部导航：只有主页面高亮，详情/对照保持来源页的高亮
    var tabTarget = page;
    if (page === 'detail') tabTarget = 'home';
    if (page === 'compare') tabTarget = 'home';
    Array.prototype.forEach.call(document.querySelectorAll('.tab'), function (t) {
      t.classList.toggle('is-active', t.dataset.page === tabTarget);
      t.setAttribute('aria-current', t.dataset.page === tabTarget ? 'page' : 'false');
    });
    // 侧栏页面列表同步
    Array.prototype.forEach.call(document.querySelectorAll('#pageList button'), function (b) {
      b.classList.toggle('on', b.dataset.page === page);
    });
    screens[page].scrollTop = 0;
  }

  function back() {
    if (stack.length > 1) {
      stack.pop();
      show(stack[stack.length - 1], false);
    } else {
      show('home', false);
    }
  }

  // 底部导航 + 侧栏页面列表
  Array.prototype.forEach.call(document.querySelectorAll('[data-page]'), function (btn) {
    btn.addEventListener('click', function () {
      var page = btn.dataset.page;
      if (page === current()) return;
      if (TAB_PAGES[page]) { stack = [page]; show(page, false); }
      else { show(page, true); }
    });
  });

  // 卡片 / banner / 列表项跳转
  Array.prototype.forEach.call(document.querySelectorAll('[data-goto]'), function (el) {
    el.addEventListener('click', function () { show(el.dataset.goto, true); });
    if (el.getAttribute('role') === 'button') {
      el.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); show(el.dataset.goto, true); }
      });
    }
  });

  Array.prototype.forEach.call(document.querySelectorAll('[data-back]'), function (btn) {
    btn.addEventListener('click', back);
  });

  // 主题
  Array.prototype.forEach.call(document.querySelectorAll('#themeSeg button'), function (btn) {
    btn.addEventListener('click', function () {
      document.documentElement.setAttribute('data-theme', btn.dataset.theme);
      Array.prototype.forEach.call(document.querySelectorAll('#themeSeg button'), function (b) {
        b.classList.toggle('on', b === btn);
      });
    });
  });

  // 字号缩放：只作用于设备内部，模拟系统「字体大小」设置
  Array.prototype.forEach.call(document.querySelectorAll('#scaleSeg button'), function (btn) {
    btn.addEventListener('click', function () {
      phone.style.setProperty('--scale', btn.dataset.scale);
      Array.prototype.forEach.call(document.querySelectorAll('#scaleSeg button'), function (b) {
        b.classList.toggle('on', b === btn);
      });
    });
  });

  // 问答状态：回答中 / 本地无内容
  var userAsk = document.getElementById('userAsk');
  var stream = document.getElementById('answerStream');
  var empty = document.getElementById('answerEmpty');
  Array.prototype.forEach.call(document.querySelectorAll('#chatSeg button'), function (btn) {
    btn.addEventListener('click', function () {
      var isStream = btn.dataset.chat === 'stream';
      stream.hidden = !isStream;
      empty.hidden = isStream;
      // 空态换一个问题，否则「有答案的问题」配「没有内容」会自相矛盾
      userAsk.textContent = isStream
        ? 'ReLU 后面能直接接 Softmax 吗？'
        : '扩散模型和 VAE 有什么区别？';
      Array.prototype.forEach.call(document.querySelectorAll('#chatSeg button'), function (b) {
        b.classList.toggle('on', b === btn);
      });
    });
  });

  // 开关（演示用，不持久化）
  Array.prototype.forEach.call(document.querySelectorAll('.switch'), function (sw) {
    sw.addEventListener('click', function () {
      var on = sw.classList.toggle('is-on');
      sw.setAttribute('aria-checked', on ? 'true' : 'false');
    });
  });

  // 关联反馈：点一下给出选中态
  Array.prototype.forEach.call(document.querySelectorAll('.fb-btn'), function (btn) {
    btn.addEventListener('click', function () {
      Array.prototype.forEach.call(btn.parentNode.children, function (b) {
        b.style.borderColor = '';
        b.style.color = '';
        b.style.background = '';
      });
      btn.style.borderColor = 'var(--leaf)';
      btn.style.color = 'var(--leaf)';
      btn.style.background = 'var(--leaf-soft)';
    });
  });

  // 键盘左滑返回的语义：Esc 返回上一页
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && stack.length > 1) back();
  });

  show('home', false);
})();
