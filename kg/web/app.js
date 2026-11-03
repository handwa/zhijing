/* =============================================================================
   知境 · 知识图谱展示页
   -----------------------------------------------------------------------------
   三条硬约束（来自 docs/knowledge-graph-design(1).md）：
     1. 广度视图：分层大纲 + 卡片 + 交叉链接（封顶 5 条、按权重排序）
     2. 深度视图：第一屏只有「一句话命题 + 两个按钮」，条件藏在撞错之后
     3. 状态回写：只回写收敛后的摘要；广度侧角标从深度侧「派生」，不单独存储

   数据来自 graph-data.js（window.KG），零 fetch、零依赖、file:// 双击可开。
   判断状态写 localStorage，模拟「深度侧是唯一真相源」。
   ========================================================================== */
(function () {
  'use strict';

  var KG = window.KG;
  if (!KG) {
    document.body.innerHTML = '<p style="padding:40px;font-family:sans-serif">' +
      '找不到 window.KG：请先运行 <code>python kg/src/build_graph.py &amp;&amp; python kg/src/export.py</code></p>';
    return;
  }

  var STORE_KEY = 'zhijing.kg.probe.v1';
  var STATE_TEXT = { unprobed: '未探测', verified: '已验证', mastered: '已掌握', confused: '易混淆' };
  var KIND_SHORT = { shape: '形状', range: '值域', statistics: '统计量', gradient: '梯度', semantic: '语义' };
  var EDGE_TEXT = KG.enums.edgeTypes;
  var EDGE_SHORT = {
    depends_on: '依赖', affects: '影响', contains: '组成',
    contrasts: '对比', refutes: '反例', prerequisite: '前置'
  };
  var KIND_TEXT = KG.enums.conditionKinds;

  /* ------------------------------------------------------------------ 索引 */
  var cardsById = {};
  var claimsById = {};
  var edgesById = {};
  var outlineByPath = {};
  var byCard = {};            // cardId -> [claimId]
  KG.cards.forEach(function (c) { cardsById[c.id] = c; outlineByPath[c.outlinePath] = c; });
  KG.claims.forEach(function (cl) {
    claimsById[cl.id] = cl;
    (byCard[cl.breadth.cardId] = byCard[cl.breadth.cardId] || []).push(cl.id);
  });
  KG.edges.forEach(function (e) { edgesById[e.id] = e; });

  /* ------------------------------------------------------- 本地判断状态存储 */
  function loadLocal() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || '{}'); } catch (e) { return {}; }
  }
  function saveLocal(obj) {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(obj)); } catch (e) { /* 隐私模式忽略 */ }
  }
  var local = loadLocal();

  /** 命题的当前探测状态：本地作答优先，否则用图谱里的种子状态。 */
  function probeOf(claimId) {
    if (local[claimId]) return local[claimId];
    return claimsById[claimId].depth.probeState;
  }

  /**
   * 广度侧角标：从深度侧聚合派生。
   * 优先级：有易混淆 → 易混淆；全部已掌握 → 已掌握；有已验证/已掌握 → 已验证；否则未探测。
   * 这就是设计文档说的「广度侧不存状态，渲染时从深度侧聚合」。
   */
  function cardProbe(cardId) {
    var ids = byCard[cardId] || [];
    if (!ids.length) return { status: null, total: 0, misconceptions: [] };
    var st = ids.map(function (id) { return probeOf(id).probe_status; });
    var miss = [];
    ids.forEach(function (id) {
      (probeOf(id).misconceptions || []).forEach(function (m) { if (m) miss.push(m); });
    });
    var status = 'unprobed';
    if (st.indexOf('confused') >= 0) status = 'confused';
    else if (st.every(function (s) { return s === 'mastered'; })) status = 'mastered';
    else if (st.some(function (s) { return s === 'verified' || s === 'mastered'; })) status = 'verified';
    return { status: status, total: ids.length, misconceptions: miss };
  }

  /* --------------------------------------------------------------- 工具函数 */
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function inline(s) {
    return esc(s)
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
  }

  /** 极简 Markdown → HTML：段落 / 列表 / 表格 / 代码块 / 加粗 / 行内代码。 */
  function renderMarkdown(md) {
    var stash = [];
    var rest = String(md || '').replace(/```[a-z]*\n([\s\S]*?)```/g, function (_, code) {
      stash.push('<pre>' + esc(code.replace(/\s+$/, '')) + '</pre>');
      return '\u0000' + (stash.length - 1) + '\u0000';
    });
    var out = rest.split(/\n{2,}/).map(function (part) {
      var lines = part.split('\n').filter(function (l) { return l.trim() !== ''; });
      if (!lines.length) return '';
      if (lines.every(function (l) { return /^\s*\|/.test(l); })) {
        var rows = lines.filter(function (l) { return !/^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(l); })
          .map(function (l) { return l.trim().replace(/^\||\|$/g, '').split('|').map(function (c) { return c.trim(); }); });
        if (!rows.length) return '';
        var head = rows.shift();
        return '<table><thead><tr>' + head.map(function (h) { return '<th>' + inline(h) + '</th>'; }).join('') +
          '</tr></thead><tbody>' + rows.map(function (r) {
            return '<tr>' + r.map(function (c) { return '<td>' + inline(c) + '</td>'; }).join('') + '</tr>';
          }).join('') + '</tbody></table>';
      }
      if (lines.every(function (l) { return /^\s*[-*]\s+/.test(l); })) {
        return '<ul>' + lines.map(function (l) {
          return '<li>' + inline(l.replace(/^\s*[-*]\s+/, '')) + '</li>';
        }).join('') + '</ul>';
      }
      return '<p>' + inline(lines.join(' ')) + '</p>';
    }).join('');
    return out.replace(/\u0000(\d+)\u0000/g, function (_, i) { return stash[+i]; });
  }

  function $(sel) { return document.querySelector(sel); }
  /** 语义路径 → 可读面包屑（大纲标题链），失败时回退到路径本身。 */
  var outlineByID = {};
  KG.outline.forEach(function (o) { outlineByID[o.id] = o; });
  function breadcrumb(path) {
    var node = outlineByID['out.' + path];
    if (!node) return path;
    var parts = [node.title];
    var guard = 0;
    while (node.parentId && outlineByID[node.parentId] && guard++ < 8) {
      node = outlineByID[node.parentId];
      parts.unshift(node.title);
    }
    return parts.length > 1 ? parts.slice(1).join(' / ') : parts[0];
  }
  function el(tag, cls, html) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  }

  /* ------------------------------------------------------------------ 顶栏 */
  function renderStats() {
    var c = KG.meta.counts;
    var learned = KG.claims.filter(function (cl) {
      var s = probeOf(cl.id).probe_status;
      return s === 'verified' || s === 'mastered';
    }).length;
    var confused = KG.claims.filter(function (cl) { return probeOf(cl.id).probe_status === 'confused'; }).length;
    $('#statChips').innerHTML =
      '<span class="stat-chip">卡片 <b>' + c.card + '</b></span>' +
      '<span class="stat-chip">命题 <b>' + c.claim + '</b></span>' +
      '<span class="stat-chip">条件边 <b>' + c.edge + '</b></span>' +
      '<span class="stat-chip">实体 <b>' + c.entity + '</b></span>' +
      '<span class="stat-chip">已判对 <b>' + learned + '</b></span>' +
      '<span class="stat-chip">易混淆 <b>' + confused + '</b></span>';
  }

  /* -------------------------------------------------------------- 大纲树 */
  var filterPath = null;
  var collapsed = {};

  function renderTree() {
    var root = $('#outlineTree');
    root.innerHTML = '';
    KG.navigation.forEach(function (node) { root.appendChild(treeNode(node, 1)); });
  }

  function treeNode(node, level) {
    var wrap = el('div', 'tree-node lv' + Math.min(level, 3));
    var row = el('div', 'tree-row');
    var hasKids = (node.children || []).length > 0;
    var isCollapsed = collapsed[node.id];
    if (isCollapsed) row.classList.add('collapsed');
    if (filterPath === nodeIDPath(node)) row.classList.add('active');

    row.appendChild(el('span', 'caret', hasKids ? '▾' : ''));
    // 卡片与所属小节同名，侧栏里只写「正文内容」，避免标题重复两遍
    row.appendChild(el('span', 'tree-label', esc(node.kind === 'card' ? '正文内容' : node.title)));
    if (node.kind === 'card') {
      row.appendChild(el('span', 'tree-count', node.claimCount ? node.claimCount + ' 命题' : ''));
    } else if (node.claimCount) {
      row.appendChild(el('span', 'tree-count', node.claimCount));
    }
    row.addEventListener('click', function (ev) {
      if (node.kind === 'card') {
        selectPath(cardsById[node.id].outlinePath);
        scrollToCard(node.id);
        return;
      }
      if (hasKids && (ev.target.classList.contains('caret') || collapsed[node.id])) {
        collapsed[node.id] = !collapsed[node.id];
        renderTree();
        return;
      }
      selectPath(nodeIDPath(node));
    });
    wrap.appendChild(row);

    if (hasKids) {
      var kids = el('div', 'tree-children');
      if (isCollapsed) kids.hidden = true;
      node.children.forEach(function (ch) { kids.appendChild(treeNode(ch, level + 1)); });
      wrap.appendChild(kids);
    }
    return wrap;
  }

  function nodeIDPath(node) {
    if (!node.id) return null;
    if (node.kind === 'outline') return node.id.indexOf('out.') === 0 ? node.id.slice(4) : null;
    var card = cardsById[node.id];
    return card ? card.outlinePath : null;
  }

  function selectPath(path) {
    filterPath = path;
    renderTree();
    renderCards();
    var outline = path ? KG.outline.filter(function (o) { return o.path === path; })[0] : null;
    $('#listTitle').textContent = outline ? outline.title : '全部卡片';
    $('#listHint').textContent = outline
      ? '当前范围：' + outline.path + '（含子章节）。叶子卡片右下角可下钻到判断面板。'
      : '每张卡是一段可独立阅读的内容；叶子卡片右下角可下钻到判断面板。';
  }

  /* ------------------------------------------------------------- 卡片流 */
  var keyword = '';

  function inPath(card, path) {
    if (!path) return true;
    return card.outlinePath === path || card.outlinePath.indexOf(path + '-') === 0;
  }

  function matches(card) {
    if (!keyword) return true;
    var k = keyword.toLowerCase();
    if ((card.title + ' ' + card.summary + ' ' + card.body).toLowerCase().indexOf(k) >= 0) return true;
    return (byCard[card.id] || []).some(function (id) {
      return claimsById[id].statement.toLowerCase().indexOf(k) >= 0;
    });
  }

  function renderCards() {
    var list = $('#cardList');
    list.innerHTML = '';
    var cards = KG.cards.filter(function (c) { return inPath(c, filterPath) && matches(c); });
    if (!cards.length) {
      list.appendChild(el('p', 'empty', '这个范围里没有卡片。换个章节，或清空搜索词。'));
      return;
    }
    cards.forEach(function (card) { list.appendChild(cardEl(card)); });
  }

  function cardEl(card) {
    var probe = cardProbe(card.id);
    var art = el('article', 'card');
    art.id = 'card-' + card.id;

    var head = el('div', 'card-head');
    var left = el('div');
    left.appendChild(el('div', 'card-title', esc(card.title)));
    left.appendChild(el('div', 'card-path', esc(breadcrumb(card.outlinePath))));
    head.appendChild(left);
    if (probe.status) {
      head.appendChild(el('span', 'badge ' + probe.status,
        '<span class="dot"></span>' + STATE_TEXT[probe.status]));
    }
    art.appendChild(head);

    // 摘要本身就是正文里的一句话：正文里能找到就不再单独显示一遍
    var bodyPlain = card.body.replace(/\*\*|`/g, '');
    var summaryHead = card.summary.replace(/…$/, '').slice(0, 18);
    if (!(bodyPlain.indexOf(summaryHead) >= 0 && summaryHead.length >= 8)) {
      art.appendChild(el('p', 'card-summary', esc(card.summary)));
    }

    var body = el('div', 'card-body collapsed');
    body.innerHTML = renderMarkdown(card.body);
    art.appendChild(body);

    if (probe.misconceptions.length) {
      art.appendChild(el('div', 'misconception-note',
        '易错点注记：' + esc(probe.misconceptions[0])));
    }

    var foot = el('div', 'card-foot');
    var links = el('div', 'links');
    if (card.crossLinks.length) {
      links.appendChild(el('span', 'links-label', '交叉链接'));
      card.crossLinks.forEach(function (l) {
        var chip = el('button', 'link-chip', esc(l.label));
        chip.title = (l.reason || '') + '（权重 ' + l.weight + '）';
        chip.addEventListener('click', function () { scrollToCard(l.target); });
        links.appendChild(chip);
      });
    } else {
      links.appendChild(el('span', 'links-label', '暂无交叉链接'));
    }
    foot.appendChild(links);

    var ids = byCard[card.id] || [];
    if (ids.length) {
      var btn = el('button', 'descend-btn', '下钻 ' + ids.length + ' 条命题');
      btn.addEventListener('click', function () { openDepth(card.id); });
      foot.appendChild(btn);
    } else {
      foot.appendChild(el('span', 'no-claim', '本卡只负责覆盖，不挂命题'));
    }
    art.appendChild(foot);

    if (card.body.length > 260) {
      var more = el('button', 'text-btn', '展开全文');
      more.style.marginTop = '10px';
      more.addEventListener('click', function () {
        var on = body.classList.toggle('collapsed');
        more.textContent = on ? '展开全文' : '收起';
      });
      art.appendChild(more);
    }
    return art;
  }

  function scrollToCard(cardId) {
    if (filterPath) {
      var card = cardsById[cardId];
      if (!inPath(card, filterPath)) selectPath(card.outlinePath);
    }
    var node = document.getElementById('card-' + cardId);
    if (!node) { selectPath(null); node = document.getElementById('card-' + cardId); }
    if (!node) return;
    node.scrollIntoView({ behavior: 'smooth', block: 'center' });
    node.classList.remove('flash');
    void node.offsetWidth;
    node.classList.add('flash');
  }

  /* -------------------------------------------------------- 深度下钻面板 */
  var depthCard = null;
  var queue = [];
  var qIndex = 0;

  function openDepth(cardId, claimId) {
    depthCard = cardId;
    var ids = (byCard[cardId] || []).slice();
    // 未探测的先出现：状态要驱动动作（易错先读 / 已掌握跳过）
    ids.sort(function (a, b) {
      var rank = { confused: 0, unprobed: 1, verified: 2, mastered: 3 };
      return rank[probeOf(a).probe_status] - rank[probeOf(b).probe_status];
    });
    queue = ids;
    qIndex = claimId ? Math.max(0, ids.indexOf(claimId)) : 0;
    if (!queue.length) return;
    $('#depthOverlay').hidden = false;
    document.body.style.overflow = 'hidden';
    showClaim(queue[qIndex]);
  }

  function closeDepth() {
    $('#depthOverlay').hidden = true;
    document.body.style.overflow = '';
    renderStats();
    renderTree();
    renderCards();
  }

  function showClaim(claimId) {
    var cl = claimsById[claimId];
    var card = cardsById[cl.breadth.cardId];
    $('#depthBreadcrumb').textContent = card.outlinePath + ' · ' + card.title;
    $('#depthStatement').textContent = cl.statement;
    $('#depthProgress').textContent = '第 ' + (qIndex + 1) + ' / ' + queue.length + ' 条命题 · ' +
      (STATE_TEXT[probeOf(claimId).probe_status] || '未探测');
    $('#depthFirst').hidden = false;
    $('#depthReveal').hidden = true;
    var btns = document.querySelectorAll('.judge-btn');
    for (var i = 0; i < btns.length; i++) btns[i].disabled = false;
    btns[0].focus();
  }

  /** 一次作答 → 收敛状态。区分「撞错后纠正」与「仍易错」。 */
  function answer(choice) {
    var claimId = queue[qIndex];
    var cl = claimsById[claimId];
    var panel = cl.depth.judgementPanel;
    var right = (choice === panel.expected);

    var prev = probeOf(claimId);
    var next = {
      probe_status: prev.probe_status,
      last_probed_at: new Date().toISOString(),
      misconceptions: (prev.misconceptions || []).slice(),
      confidence: prev.confidence || 0,
      attempts: (prev.attempts || 0) + 1,
      correct: (prev.correct || 0) + (right ? 1 : 0),
      streak: (prev.streak || 0)
    };
    if (right) {
      next.streak = (prev.streak || 0) + 1;
      next.probe_status = next.streak >= 2 ? 'mastered' : 'verified';
    } else {
      next.streak = 0;
      next.probe_status = 'confused';
      var miss = panel.reveal.misconception ||
        ('对「' + cl.statement + '」判错了 —— 边界还没吃准。');
      if (next.misconceptions.indexOf(miss) < 0) next.misconceptions.push(miss);
    }
    next.confidence = Math.round((next.correct / next.attempts) * 100) / 100;

    local[claimId] = next;
    saveLocal(local);

    var btns = document.querySelectorAll('.judge-btn');
    for (var i = 0; i < btns.length; i++) btns[i].disabled = true;
    renderReveal(cl, right, next);
  }

  function renderReveal(cl, right, probe) {
    var panel = cl.depth.judgementPanel;
    $('#depthFirst').hidden = true;
    $('#depthReveal').hidden = false;

    var verdict = $('#verdict');
    verdict.className = 'verdict ' + (right ? 'right' : 'wrong');
    verdict.innerHTML = '<span class="mark">' + (right ? '✓' : '✗') + '</span>' +
      (right ? '判断正确 —— 这条命题的结论是「' + (panel.expected ? '成立' : '不成立') + '」'
             : '撞错了 —— 正确结论是「' + (panel.expected ? '成立' : '不成立') + '」，错点已定位在下面');

    $('#revealExplain').textContent = panel.reveal.explanation || '（标注块未给出解释）';

    var conds = $('#revealConditions');
    conds.innerHTML = '';
    (cl.conditions || []).forEach(function (c) { conds.appendChild(el('li', null, esc(c))); });

    var kinds = $('#errorKinds');
    kinds.innerHTML = '';
    (panel.reveal.errorKinds || []).forEach(function (k) {
      var row = el('div', 'error-kind');
      row.appendChild(el('span', 'tag', esc(KIND_SHORT[k.kind] || k.kind) + '依赖'));
      row.appendChild(el('span', 'why', esc(k.why || '')));
      kinds.appendChild(row);
    });

    var edges = (cl.depth.edges || []).map(function (id) { return edgesById[id]; }).filter(Boolean);
    var list = $('#edgeList');
    list.innerHTML = '';
    if (!edges.length) {
      list.appendChild(el('p', 'muted', '这条命题目前没有关联的带条件边 —— 它可以独立判断。'));
    }
    edges.forEach(function (e) {
      var otherId = e.from === cl.id ? e.to : e.from;
      var other = claimsById[otherId];
      var item = el('div', 'edge-item');
      var top = el('div', 'edge-top');
      top.appendChild(el('span', 'edge-type', esc(EDGE_SHORT[e.type] || e.type)));
      top.appendChild(el('span', null, esc(EDGE_TEXT[e.type] || '')));
      top.appendChild(el('span', null, '· ' + esc(KIND_TEXT[e.condition.kind] || e.condition.kind)));
      top.appendChild(el('span', null, '· 权重 ' + e.weight));
      item.appendChild(top);
      item.appendChild(el('div', 'edge-statement', esc((e.from === cl.id ? '→ ' : '← ') + other.statement)));
      var cond = el('div', 'edge-cond');
      cond.appendChild(el('div', null, '<b>前提：</b>' + esc(e.condition.premise)));
      cond.appendChild(el('div', null, '<b>边界：</b>' + esc(e.condition.boundary)));
      if (e.condition.counterexample) {
        cond.appendChild(el('div', null, '<b>反例：</b>' + esc(e.condition.counterexample)));
      }
      item.appendChild(cond);
      list.appendChild(item);
    });

    var missBlock = $('#misconceptionBlock');
    var missTitle = missBlock.querySelector('h4');
    var missText = $('#misconceptionText');
    var st = probe.probe_status;
    if (st === 'confused') {
      missBlock.hidden = false;
      missTitle.textContent = '这条误解会被记下来';
      missText.textContent = (probe.misconceptions[probe.misconceptions.length - 1] || '') +
        '（回写广度侧后，卡片带「易混淆」角标与这条注记）';
    } else if (st === 'mastered') {
      missBlock.hidden = false;
      missTitle.textContent = '状态已收敛';
      missText.textContent = '连续判对，状态收敛为「已掌握」；广度侧角标随之变为已掌握。';
    } else if (st === 'verified') {
      missBlock.hidden = false;
      missTitle.textContent = '状态：已验证';
      missText.textContent = '判对一次，状态为「已验证」；再连续判对一次即收敛为「已掌握」。';
    } else {
      missBlock.hidden = true;
    }

    $('#nextClaimBtn').textContent = qIndex + 1 < queue.length ? '下一条命题' : '这张卡看完了';
  }

  /* ------------------------------------------------------------ 结构图 SVG */
  var view = { k: 1, x: 0, y: 0 };
  var SVG_NS = 'http://www.w3.org/2000/svg';

  function measure(text, size) {
    var w = 0;
    for (var i = 0; i < text.length; i++) {
      w += text.charCodeAt(i) > 255 ? size : size * 0.56;
    }
    return w;
  }

  /**
   * 分栏布局：每个一级章节占一列，章节内的节与卡片纵向排列。
   * 比「所有叶子排成一行」紧凑得多 —— 27 张卡横排会有 5000px 宽，缩到 0.3 倍就没法读了。
   */
  function layoutTree() {
    var COL_W = 216, MARGIN = 24;
    var nodes = [], links = [];
    var root = KG.navigation[0];
    if (!root) return { nodes: [], links: [], width: 800, height: 400 };

    var kids = root.children || [];
    var chapters = kids.filter(function (n) { return n.kind === 'outline'; });
    var rootCard = kids.filter(function (n) { return n.kind === 'card'; })[0];
    var width = MARGIN * 2 + Math.max(1, chapters.length) * COL_W;
    var rootNode = {
      node: root, depth: 0, x: width / 2, y: 8,
      w: Math.max(130, measure(root.title, 12) + 26), h: 26
    };
    nodes.push(rootNode);
    var maxY = 120;

    if (rootCard) {
      var rc = { node: rootCard, depth: 1, x: width / 2, y: 52, w: 200, h: 30 };
      nodes.push(rc);
      links.push({ from: rootNode, to: rc });
      maxY = Math.max(maxY, 92);
    }

    chapters.forEach(function (ch, i) {
      var cx = MARGIN + COL_W / 2 + i * COL_W;
      var chNode = {
        node: ch, depth: 1, x: cx, y: 100,
        w: Math.max(104, Math.min(COL_W - 14, measure(ch.title, 12) + 24)), h: 26
      };
      nodes.push(chNode);
      links.push({ from: rootNode, to: chNode });

      var y = 142;
      (ch.children || []).filter(function (n) { return n.kind === 'card'; }).forEach(function (c) {
        var n = { node: c, depth: 2, x: cx, y: y, w: 200, h: 30 };
        nodes.push(n); links.push({ from: chNode, to: n });
        y += 40;
      });
      (ch.children || []).filter(function (n) { return n.kind === 'outline'; }).forEach(function (sec) {
        var sn = {
          node: sec, depth: 2, x: cx, y: y,
          w: Math.max(112, Math.min(200, measure(sec.title, 12) + 22)), h: 24
        };
        nodes.push(sn); links.push({ from: chNode, to: sn });
        y += 30;
        (sec.children || []).filter(function (n) { return n.kind === 'card'; }).forEach(function (c) {
          var cn = { node: c, depth: 3, x: cx, y: y, w: 200, h: 30 };
          nodes.push(cn); links.push({ from: sn, to: cn });
          y += 38;
        });
        y += 14;
      });
      maxY = Math.max(maxY, y);
    });

    return { nodes: nodes, links: links, width: width, height: maxY + 24 };
  }

  function maxDepth(nodes) {
    return nodes.reduce(function (m, n) { return Math.max(m, n.depth); }, 0);
  }

  function renderStructure() {
    var svg = $('#structureSvg');
    var wrap = $('#canvasWrap');
    svg.innerHTML = '';
    var lay = layoutTree();
    var W = wrap.clientWidth || 900;
    var H = wrap.clientHeight || 600;
    var k = Math.min(1, (W - 40) / lay.width, (H - 40) / lay.height);
    view = { k: k, x: (W - lay.width * k) / 2, y: 18 };

    var g = document.createElementNS(SVG_NS, 'g');
    g.setAttribute('id', 'zoomLayer');
    svg.appendChild(g);

    var pos = {};
    lay.nodes.forEach(function (n) { pos[n.node.id || n.node.title] = n; });

    // 交叉链接：虚线，画在层级线下面
    KG.cards.forEach(function (card) {
      (card.crossLinks || []).forEach(function (l) {
        var a = pos[card.id], b = pos[l.target];
        if (!a || !b) return;
        var p = document.createElementNS(SVG_NS, 'path');
        var y1 = a.y + a.h, y2 = b.y;
        p.setAttribute('d', 'M' + a.x + ',' + y1 + ' C' + a.x + ',' + (y1 + 40) + ' ' +
          b.x + ',' + (y2 - 40) + ' ' + b.x + ',' + y2);
        p.setAttribute('class', 'cross-edge');
        p.setAttribute('stroke-width', 0.6 + l.weight * 1.6);
        g.appendChild(p);
      });
    });

    lay.links.forEach(function (l) {
      var p = document.createElementNS(SVG_NS, 'path');
      var y1 = l.from.y + l.from.h, y2 = l.to.y;
      var mid = (y1 + y2) / 2;
      p.setAttribute('d', 'M' + l.from.x + ',' + y1 + ' V' + mid + ' H' + l.to.x + ' V' + y2);
      p.setAttribute('class', 'tree-edge');
      g.appendChild(p);
    });

    lay.nodes.forEach(function (n) {
      var node = n.node;
      var isCard = node.kind === 'card';
      var group = document.createElementNS(SVG_NS, 'g');

      if (isCard) {
        var rect = document.createElementNS(SVG_NS, 'rect');
        rect.setAttribute('x', n.x - n.w / 2);
        rect.setAttribute('y', n.y);
        rect.setAttribute('width', n.w);
        rect.setAttribute('height', n.h);
        rect.setAttribute('rx', 9);
        rect.setAttribute('class', 'node-card');
        group.appendChild(rect);
        var label = document.createElementNS(SVG_NS, 'text');
        label.setAttribute('x', n.x);
        label.setAttribute('y', n.y + 19);
        label.setAttribute('text-anchor', 'middle');
        label.setAttribute('class', 'node-label');
        // 卡片节点显示内容摘要而不是标题：标题与所属小节重复，摘要才带信息
        var info = cardsById[node.id];
        label.textContent = truncate(info ? info.summary.replace(/[。；].*$/, '') : node.title, 13);
        var tip = document.createElementNS(SVG_NS, 'title');
        tip.textContent = (info ? info.title + '：' + info.summary : node.title) +
          (node.claimCount ? '（' + node.claimCount + ' 条命题）' : '');
        group.appendChild(tip);
        group.appendChild(label);
        if (node.claimCount) {
          var pill = document.createElementNS(SVG_NS, 'rect');
          pill.setAttribute('x', n.x + n.w / 2 - 24);
          pill.setAttribute('y', n.y - 7);
          pill.setAttribute('width', 26);
          pill.setAttribute('height', 15);
          pill.setAttribute('rx', 7.5);
          pill.setAttribute('class', 'claim-pill');
          group.appendChild(pill);
          var cnt = document.createElementNS(SVG_NS, 'text');
          cnt.setAttribute('x', n.x + n.w / 2 - 11);
          cnt.setAttribute('y', n.y + 4);
          cnt.setAttribute('text-anchor', 'middle');
          cnt.setAttribute('class', 'claim-count');
          cnt.textContent = node.claimCount;
          group.appendChild(cnt);
        }
        group.style.cursor = 'pointer';
        group.addEventListener('click', function () {
          switchView('breadth');
          selectPath(cardsById[node.id].outlinePath);
          setTimeout(function () { scrollToCard(node.id); }, 60);
        });
      } else {
        var r = document.createElementNS(SVG_NS, 'rect');
        r.setAttribute('x', n.x - n.w / 2);
        r.setAttribute('y', n.y);
        r.setAttribute('width', n.w);
        r.setAttribute('height', n.h);
        r.setAttribute('rx', 7);
        r.setAttribute('class', 'node-outline' + (n.depth === 0 ? ' lv1' : ''));
        group.appendChild(r);
        var t = document.createElementNS(SVG_NS, 'text');
        t.setAttribute('x', n.x);
        t.setAttribute('y', n.y + 17);
        t.setAttribute('text-anchor', 'middle');
        t.setAttribute('class', 'node-label' + (n.depth === 0 ? ' lv1' : ''));
        t.textContent = truncate(node.title, n.w > 150 ? 14 : 9);
        group.appendChild(t);
      }
      g.appendChild(group);
    });

    applyTransform();
    bindPanZoom(svg, wrap);
  }

  function truncate(s, n) {
    return s.length > n ? s.slice(0, n) + '…' : s;
  }

  function applyTransform() {
    var g = $('#zoomLayer');
    if (!g) return;
    g.setAttribute('transform', 'translate(' + view.x + ',' + view.y + ') scale(' + view.k + ')');
  }

  function bindPanZoom(svg, wrap) {
    if (svg.dataset.bound) return;
    svg.dataset.bound = '1';
    var dragging = false, sx = 0, sy = 0, ox = 0, oy = 0;

    svg.addEventListener('pointerdown', function (e) {
      if (e.target.closest('g') && e.target.tagName === 'text') return;
      dragging = true; sx = e.clientX; sy = e.clientY; ox = view.x; oy = view.y;
      wrap.classList.add('dragging');
      svg.setPointerCapture(e.pointerId);
    });
    svg.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      view.x = ox + (e.clientX - sx);
      view.y = oy + (e.clientY - sy);
      applyTransform();
    });
    svg.addEventListener('pointerup', function () {
      dragging = false; wrap.classList.remove('dragging');
    });
    svg.addEventListener('wheel', function (e) {
      e.preventDefault();
      var rect = svg.getBoundingClientRect();
      var mx = e.clientX - rect.left, my = e.clientY - rect.top;
      var factor = e.deltaY < 0 ? 1.12 : 1 / 1.12;
      var nk = Math.max(0.35, Math.min(2.4, view.k * factor));
      view.x = mx - (mx - view.x) * (nk / view.k);
      view.y = my - (my - view.y) * (nk / view.k);
      view.k = nk;
      applyTransform();
    }, { passive: false });
  }

  function zoom(factor) {
    var wrap = $('#canvasWrap');
    var cx = (wrap.clientWidth || 900) / 2, cy = (wrap.clientHeight || 600) / 2;
    var nk = Math.max(0.35, Math.min(2.4, view.k * factor));
    view.x = cx - (cx - view.x) * (nk / view.k);
    view.y = cy - (cy - view.y) * (nk / view.k);
    view.k = nk;
    applyTransform();
  }

  /* -------------------------------------------------------------- 视图切换 */
  function switchView(v) {
    document.body.classList.toggle('view-structure', v === 'structure');
    $('#breadthView').hidden = v === 'structure';
    $('#structureView').hidden = v !== 'structure';
    document.querySelectorAll('.chip-btn').forEach(function (b) {
      var on = b.dataset.view === v;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    if (v === 'structure') setTimeout(renderStructure, 30);
  }

  /* ---------------------------------------------------------------- 事件 */
  document.querySelectorAll('.chip-btn').forEach(function (b) {
    b.addEventListener('click', function () { switchView(b.dataset.view); });
  });
  $('#resetBtn').addEventListener('click', function () {
    local = {};
    saveLocal(local);
    renderStats(); renderTree(); renderCards();
    if (!$('#depthOverlay').hidden) closeDepth();
  });
  $('#allBtn').addEventListener('click', function () { selectPath(null); });
  $('#searchInput').addEventListener('input', function (e) {
    keyword = e.target.value.trim();
    renderCards();
  });
  $('#depthClose').addEventListener('click', closeDepth);
  $('#backToCardBtn').addEventListener('click', closeDepth);
  $('#nextClaimBtn').addEventListener('click', function () {
    if (qIndex + 1 < queue.length) {
      qIndex += 1;
      showClaim(queue[qIndex]);
      $('#depthPanelScroll') && ($('#depthPanelScroll').scrollTop = 0);
    } else {
      closeDepth();
    }
  });
  document.querySelectorAll('.judge-btn').forEach(function (b) {
    b.addEventListener('click', function () {
      answer(b.dataset.answer === 'true');
    });
  });
  $('#depthOverlay').addEventListener('click', function (e) {
    if (e.target === $('#depthOverlay')) closeDepth();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !$('#depthOverlay').hidden) closeDepth();
    if ($('#depthOverlay').hidden) return;
    if (!$('#depthReveal').hidden) return;
    if (e.key === 'ArrowLeft' || e.key === '1') answer(true);
    if (e.key === 'ArrowRight' || e.key === '2') answer(false);
  });
  $('#zoomIn').addEventListener('click', function () { zoom(1.18); });
  $('#zoomOut').addEventListener('click', function () { zoom(1 / 1.18); });
  $('#zoomReset').addEventListener('click', function () {
    var svg = $('#structureSvg');
    svg.dataset.bound = '';
    renderStructure();
  });
  window.addEventListener('resize', function () {
    if (document.body.classList.contains('view-structure')) {
      var svg = $('#structureSvg');
      svg.dataset.bound = '';
      renderStructure();
    }
  });

  /* ----------------------------------------------------------------- 启动 */
  $('#metaSource').textContent = KG.meta.source;
  $('#metaTime').textContent = '生成于 ' + KG.meta.generatedAt + '（schema v' + KG.meta.schemaVersion + '）';
  renderStats();
  renderTree();
  renderCards();
  if (KG.meta.warnings && KG.meta.warnings.length) {
    console.info('[kg] 构建警告 ' + KG.meta.warnings.length + ' 条：', KG.meta.warnings);
  }
})();
