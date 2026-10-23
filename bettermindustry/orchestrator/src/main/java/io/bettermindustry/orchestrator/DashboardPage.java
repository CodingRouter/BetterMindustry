package io.bettermindustry.orchestrator;

public final class DashboardPage {
    private DashboardPage() {
    }

    public static String html() {
        return """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>BetterMindustry Debugger</title>
  <style>
    :root { color-scheme: dark; --bg:#091018; --panel:#111c28; --line:#203244; --text:#dce8f2; --muted:#8297aa; --good:#45d483; --warn:#f0b95b; --bad:#ff7070; --accent:#71b7ff; }
    * { box-sizing: border-box; }
    [hidden] { display:none !important; }
    body { margin:0; background:radial-gradient(circle at top left,#18304a 0,var(--bg) 36%,#05090d 100%); color:var(--text); font:14px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace; }
    header, .pipeline, .summary, main, .footer { max-width:1800px; margin-left:auto; margin-right:auto; }
    header { padding:28px 30px 10px; display:flex; justify-content:space-between; gap:20px; align-items:end; }
    h1 { margin:0; font:700 27px ui-sans-serif,system-ui,sans-serif; letter-spacing:.02em; }
    .subtitle { color:var(--muted); margin-top:5px; }
    .toolbar { display:flex; gap:8px; }
    button { border:1px solid var(--line); background:#172536; color:var(--text); padding:9px 13px; border-radius:7px; cursor:pointer; font:inherit; }
    button:hover { border-color:var(--accent); background:#1d3146; }
    button:disabled { opacity:.38; cursor:not-allowed; border-color:var(--line); background:#101923; }
    button.primary { border-color:#2b6c9c; background:#19415e; }
    button.danger { color:#ffd4d4; border-color:#613434; background:#3a2025; }
    .session-pill { display:inline-flex; align-items:center; padding:8px 11px; border:1px solid #2a5a78; border-radius:999px; color:#9ed6ff; background:#102b3e; white-space:nowrap; }
    dialog { width:min(460px,calc(100vw - 32px)); border:1px solid #2b4c69; border-radius:14px; background:#101d29; color:var(--text); box-shadow:0 24px 80px #000b; }
    dialog::backdrop { background:#02060acc; backdrop-filter:blur(3px); }
    .auth-form { display:grid; gap:11px; }
    .auth-form h2 { margin:0 0 4px; font:700 22px ui-sans-serif,system-ui,sans-serif; }
    .auth-form p { margin:0; color:var(--muted); font-size:12px; }
    .auth-form label { display:grid; gap:5px; color:var(--muted); font-size:12px; }
    .auth-form input { width:100%; border:1px solid var(--line); background:#09131d; color:var(--text); padding:10px; border-radius:7px; font:inherit; }
    .auth-actions { display:flex; gap:8px; margin-top:4px; }
    .auth-message { min-height:20px; color:var(--warn); }
    .session-list { display:grid; gap:8px; max-height:55vh; overflow:auto; margin:4px 0; }
    .session-item { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10px; align-items:center; padding:10px; border:1px solid var(--line); border-radius:8px; background:#0a141e; }
    .session-item strong { display:block; font-weight:600; overflow-wrap:anywhere; }
    .session-item small { display:block; margin-top:3px; color:var(--muted); }
    .session-current { color:var(--good); }
    .pipeline { margin-top:18px; padding:14px 18px; border:1px solid var(--line); background:#0d1824cc; border-radius:12px; display:flex; align-items:center; gap:18px; flex-wrap:wrap; }
    .pipe-node { padding:10px 14px; border:1px solid #2b4c69; border-radius:8px; background:#122538; min-width:150px; }
    .pipe-node small { color:var(--muted); }
    .pipe-arrow { color:var(--accent); font-size:20px; }
    .pipe-note { color:var(--muted); }
    .summary { margin-top:14px; padding:0 30px; display:grid; grid-template-columns:repeat(auto-fit,minmax(130px,1fr)); gap:12px; }
    .summary-card { padding:13px 15px; border:1px solid var(--line); background:#0e1924cc; border-radius:10px; }
    .summary-card span { display:block; color:var(--muted); font-size:12px; }
    .summary-card strong { display:block; margin-top:2px; font:700 22px ui-sans-serif,system-ui,sans-serif; }
    main { padding:20px 30px 30px; display:grid; grid-template-columns:repeat(auto-fit,minmax(520px,1fr)); gap:18px; }
    .worker { min-width:0; background:linear-gradient(150deg,#142232,#0e1721); border:1px solid var(--line); border-radius:14px; overflow:hidden; box-shadow:0 16px 40px #0005; }
    .worker.focused { border-color:#4d8cbc; box-shadow:0 0 0 1px #2d638b,0 16px 40px #0005; }
    .worker-head { padding:17px 20px; display:flex; justify-content:space-between; gap:15px; border-bottom:1px solid var(--line); }
    .worker-title { font:700 20px ui-sans-serif,system-ui,sans-serif; }
    .state { align-self:start; font-size:12px; padding:5px 9px; border-radius:20px; background:#1b2b3b; color:var(--muted); }
    .state.RUNNING { color:#9ff0bb; background:#163d2a; }
    .state.STARTING,.state.STOPPING { color:#ffe0a0; background:#44351b; }
    .state.FAILED { color:#ffb0b0; background:#4b2228; }
    .focus-badge { font-size:11px; padding:4px 8px; border:1px solid #2a5a78; border-radius:999px; color:#9ed6ff; background:#102b3e; }
    .focus-badge.observe { color:var(--muted); border-color:var(--line); background:#121e2a; }
    .meta { padding:14px; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }
    .meta-item { min-width:0; padding:8px 10px; background:#09141e; border:1px solid #182b3b; border-radius:7px; }
    .meta-item span { display:block; color:var(--muted); font-size:11px; }
    .meta-item strong { display:block; margin-top:2px; color:var(--text); font-weight:500; overflow-wrap:anywhere; font-size:13px; }
    .preview-shell { position:relative; height:clamp(360px, 50vh, 620px); background:#05090d; border-top:1px solid var(--line); border-bottom:1px solid var(--line); }
    .worker[data-view-mode="SUMMARY"] .preview-shell { height:240px; opacity:.72; }
    .world-preview { display:block; width:100%; height:100%; object-fit:contain; image-rendering:pixelated; background:#05090d; }
    .preview-empty { position:absolute; inset:0; display:grid; place-items:center; color:var(--muted); font-size:12px; text-align:center; padding:20px; }
    .controls { padding:14px 18px; display:flex; gap:8px; flex-wrap:wrap; }
    .command { display:flex; gap:8px; padding:0 18px 15px; }
    .command input { flex:1; min-width:0; border:1px solid var(--line); background:#09131d; color:var(--text); padding:10px; border-radius:7px; font:inherit; }
    .logs { height:280px; margin:0; padding:13px 16px; overflow:auto; background:#060b10; border-top:1px solid var(--line); color:#9fb4c5; white-space:pre-wrap; overflow-wrap:anywhere; font-size:12px; }
    .footer { color:var(--muted); padding:0 30px 28px; font-size:12px; }
    @media (max-width:760px) { header { align-items:start; flex-direction:column; } .pipeline,.summary,main { margin-left:16px; margin-right:16px; } .summary { grid-template-columns:repeat(2,1fr); } main { padding-left:0; padding-right:0; } .footer { padding-left:16px; } }
  </style>
</head>
<body>
  <header>
    <div><h1>BetterMindustry Debugger</h1><div class="subtitle" id="subtitle">正在建立安全会话…</div></div>
    <div class="toolbar"><span class="session-pill" id="session-role">GUEST</span><button id="sessions-button">会话</button><button id="login-button">登录 / 注册</button><button id="revoke-session">退出并重置</button><button class="primary" id="start-all">启动全部</button><button class="danger" id="stop-all">停止全部</button></div>
  </header>
  <section class="pipeline">
    <span class="pipe-node">Game Tick<br><small id="game-pipe-status">等待 Game Tick</small></span>
    <span class="pipe-arrow">→</span>
    <span class="pipe-node">Render Tick<br><small id="render-pipe-status">正在连接快照流…</small></span>
    <span class="pipe-note">浏览器独立渲染，只读消费 Game Tick 快照。</span>
  </section>
  <section class="summary">
    <div class="summary-card"><span>运行 Worker</span><strong id="summary-workers">-</strong></div>
    <div class="summary-card"><span>Focus Sector</span><strong id="summary-focus">-</strong></div>
    <div class="summary-card"><span>控制权</span><strong id="summary-control">-</strong></div>
    <div class="summary-card"><span>在线玩家</span><strong id="summary-players">-</strong></div>
    <div class="summary-card"><span>单位 / 敌人</span><strong id="summary-units">-</strong></div>
    <div class="summary-card"><span>建筑总数</span><strong id="summary-buildings">-</strong></div>
  </section>
  <main id="workers"></main>
  <div class="footer">本地控制面板 · 仅监听 127.0.0.1 · 地图来自真实 Server Worker</div>
  <dialog id="auth-dialog">
    <form class="auth-form" method="dialog">
      <h2>BetterMindustry 账户</h2>
      <p>访客只能观察。PLAYER 可以控制 Sector；第一个注册账户会成为本地 ADMIN。</p>
      <label>用户名<input id="auth-username" autocomplete="username" maxlength="32" placeholder="3-32 位字母、数字、_ 或 -"></label>
      <label>密码<input id="auth-password" type="password" autocomplete="current-password" maxlength="128" placeholder="至少 10 个字符"></label>
      <div class="auth-message" id="auth-message"></div>
      <div class="auth-actions">
        <button class="primary" id="auth-login" type="button">登录</button>
        <button id="auth-register" type="button">注册</button>
        <button value="cancel">关闭</button>
      </div>
    </form>
  </dialog>
  <dialog id="sessions-dialog">
    <div class="auth-form">
      <h2>设备会话</h2>
      <p id="sessions-scope">正在加载 Session…</p>
      <div class="session-list" id="sessions-list"></div>
      <div class="auth-actions">
        <button class="danger" id="revoke-other-sessions" type="button">退出其他设备</button>
        <button id="refresh-sessions" type="button">刷新</button>
        <button id="close-sessions" type="button">关闭</button>
      </div>
    </div>
  </dialog>
  <script>
    const cards = new Map();
    const worldVersions = new Map();
    const workersRoot = document.getElementById('workers');
    let renderFrames = 0;
    let displayFrames = 0;
    let latestRenderFrame = null;
    let latestGateway = null;
    let renderConnected = false;
    let renderStream = null;
    let clientId = null;
    let currentRole = 'GUEST';
    let currentUsername = null;

    async function apiFetch(url, options = {}) {
      const headers = new Headers(options.headers || {});
      return fetch(url, {...options, headers, credentials:'same-origin'});
    }

    async function establishSession() {
      let response = await fetch('/api/gateway/session', {
        method:'GET',
        credentials:'same-origin',
        cache:'no-store'
      });
      if (response.status === 401) {
        response = await fetch('/api/gateway/session', {
          method:'POST',
          credentials:'same-origin',
          cache:'no-store'
        });
      }
      if (!response.ok) throw new Error('无法建立 Client Session: HTTP ' + response.status);
      const session = await response.json();
      clientId = session.clientId;
      currentRole = session.role || 'GUEST';
      currentUsername = session.username || null;
      updateIdentityUI();
      return session;
    }

    function hasRole(required) {
      const levels = {GUEST:0, PLAYER:1, ADMIN:2};
      return (levels[currentRole] || 0) >= levels[required];
    }
    function updateIdentityUI() {
      const label = currentUsername ? currentUsername + ' · ' + currentRole : 'GUEST';
      document.getElementById('session-role').textContent = label;
      document.getElementById('start-all').disabled = !hasRole('ADMIN');
      document.getElementById('stop-all').disabled = !hasRole('ADMIN');
      workersRoot.querySelectorAll('[data-action="start"],[data-action="stop"],[data-action="save"]').forEach(button => {
        button.disabled = !hasRole('ADMIN');
      });
      workersRoot.querySelectorAll('[data-gateway-action="acquire"],[data-gateway-action="release"],[data-gateway-action="pause"],[data-gateway-action="resume"],[data-gateway-action="chat"]').forEach(button => {
        button.disabled = !hasRole('PLAYER');
      });
      workersRoot.querySelectorAll('[data-command]').forEach(input => {
        input.disabled = !hasRole('PLAYER');
      });
    }

    function set(card, name, value) {
      const el = card.querySelector('[data-field="' + name + '"]');
      if (el) el.textContent = value ?? '-';
    }
    function stateClass(state) { return state || 'STOPPED'; }
    function createCard(worker) {
      const article = document.createElement('article');
      article.className = 'worker';
      article.dataset.worker = worker.id;

      const head = document.createElement('div');
      head.className = 'worker-head';
      const title = document.createElement('div');
      const name = document.createElement('div');
      name.className = 'worker-title';
      name.textContent = worker.id;
      const subtitle = document.createElement('div');
      subtitle.className = 'subtitle';
      subtitle.textContent = worker.map + ' · port ' + worker.port;
      title.append(name, subtitle);
      const state = document.createElement('span');
      state.className = 'state';
      state.dataset.field = 'state';
      state.textContent = 'STOPPED';
      const badges = document.createElement('div');
      badges.style.display = 'flex';
      badges.style.gap = '6px';
      const focus = document.createElement('span');
      focus.className = 'focus-badge observe';
      focus.dataset.field = 'focusBadge';
      focus.textContent = 'OBSERVE';
      badges.append(state, focus);
      head.append(title, badges);
      article.append(head);

      const previewShell = document.createElement('div');
      previewShell.className = 'preview-shell';
      const image = document.createElement('img');
      image.className = 'world-preview';
      image.dataset.field = 'worldImage';
      image.alt = '';
      image.hidden = true;
      const empty = document.createElement('div');
      empty.className = 'preview-empty';
      empty.textContent = '等待地图快照…';
      image.onload = () => {
        image.hidden = false;
        empty.hidden = true;
      };
      image.onerror = () => {
        image.hidden = true;
        empty.hidden = false;
        empty.textContent = '地图快照加载失败';
        worldVersions.delete(worker.id);
      };
      previewShell.append(image, empty);
      article.append(previewShell);

      const meta = document.createElement('div');
      meta.className = 'meta';
      [
        ['进程 PID','pid'], ['运行时长','uptime'], ['退出码','exit'], ['端口监听','portOpen'],
        ['地图','mapName'], ['玩家','players'], ['Game Tick','tick'], ['游戏时间','gameTime'],
        ['波次','wave'], ['单位','units'], ['敌人','enemies'], ['建筑','buildings'],
        ['暂停','paused'], ['视口模式','viewMode'], ['控制权','control'], ['世界快照','worldAge'], ['快照 Tick','snapshotTick'], ['检查点','checkpoint']
      ].forEach(([label, field]) => {
        const item = document.createElement('div');
        item.className = 'meta-item';
        const key = document.createElement('span');
        key.textContent = label;
        const value = document.createElement('strong');
        value.dataset.field = field;
        value.textContent = '-';
        item.append(key, value);
        meta.append(item);
      });
      article.append(meta);

      const controls = document.createElement('div');
      controls.className = 'controls';
      [['start','启动'],['stop','停止'],['save','保存检查点'],['gateway-focus','设为主控'],['gateway-view','切换视口'],['gateway-acquire','获取控制'],['gateway-release','释放控制'],['gateway-pause','暂停'],['gateway-resume','继续']].forEach(([action,label]) => {
        const button = document.createElement('button');
        if (action.startsWith('gateway-')) {
          button.dataset.gatewayAction = action.substring('gateway-'.length);
        } else {
          button.dataset.action = action;
        }
        button.textContent = label;
        controls.append(button);
      });
      article.append(controls);

      const command = document.createElement('div');
      command.className = 'command';
      const input = document.createElement('input');
      input.dataset.command = '';
      input.placeholder = '获得控制权后向 ' + worker.id + ' 发送聊天…';
      const send = document.createElement('button');
      send.dataset.gatewayAction = 'chat';
      send.textContent = '发送聊天';
      command.append(input, send);
      article.append(command);

      const logs = document.createElement('pre');
      logs.className = 'logs';
      logs.dataset.field = 'logs';
      article.append(logs);
      workersRoot.append(article);
      cards.set(worker.id, article);
      updateIdentityUI();
      return article;
    }

    async function refresh() {
      try {
        const response = await apiFetch('/api/overview', {cache:'no-store'});
        if (!response.ok) throw new Error('HTTP ' + response.status);
        const data = await response.json();
        document.getElementById('subtitle').textContent = 'Project: ' + data.projectRoot
          + ' · session ' + clientId.slice(0, 12) + '…';
        let running = 0, players = 0, units = 0, enemies = 0, buildings = 0;
        const gateway = data.gateway || {focusSectorId:'-', views:{}};
        latestGateway = gateway;
        for (const w of data.workers) {
          const card = cards.get(w.id) || createCard(w);
          const d = w.diagnostics;
          if (w.state === 'RUNNING') running++;
          if (d) { players += d.players; units += d.units; enemies += d.enemies; buildings += d.buildings; }
          set(card, 'state', w.state);
          card.querySelector('[data-field="state"]').className = 'state ' + stateClass(w.state);
          set(card, 'pid', w.pid ?? '-');
          set(card, 'uptime', w.alive ? w.uptimeSeconds + 's' : '-');
          set(card, 'exit', w.exitCode ?? '-');
          set(card, 'portOpen', w.portOpen ? 'listening' : 'closed');
          set(card, 'mapName', d ? d.map : w.map);
          set(card, 'players', d ? d.players : '-');
          set(card, 'tick', d ? Math.floor(d.tick) : '-');
          set(card, 'gameTime', d ? formatTime(d.gameTimeSeconds) : '-');
          set(card, 'wave', d ? d.wave : '-');
          set(card, 'units', d ? d.units : '-');
          set(card, 'enemies', d ? d.enemies : '-');
          set(card, 'buildings', d ? d.buildings : '-');
          set(card, 'paused', d ? (d.paused ? 'yes' : 'no') : '-');
          const mode = w.viewMode || gateway.views[w.id] || 'OFF';
          set(card, 'viewMode', mode);
          card.dataset.viewMode = mode;
          const isFocus = gateway.focusSectorId === w.id;
          card.classList.toggle('focused', isFocus);
          const focusBadge = card.querySelector('[data-field="focusBadge"]');
          focusBadge.textContent = isFocus ? 'FOCUS' : 'OBSERVE';
          focusBadge.classList.toggle('observe', !isFocus);
          const control = isFocus
            ? (gateway.controlOwnerId === clientId
                ? 'owned · ' + Math.ceil(gateway.controlExpiresInMillis / 1000) + 's'
                : gateway.controlOwnerId ? 'held by ' + gateway.controlOwnerId.slice(0, 8) : 'available')
            : 'observe';
          set(card, 'control', control);
          set(card, 'worldAge', w.worldDataAvailable ? (w.worldSnapshotAgeMillis >= 0 ? Math.ceil(w.worldSnapshotAgeMillis / 1000) + 's' : 'ready') : 'waiting');
          set(card, 'snapshotTick', w.worldDataAvailable ? Math.floor(w.worldSnapshotTick) : '-');
          set(card, 'checkpoint', w.checkpoint ? 'available' : 'none');

          const preview = card.querySelector('[data-field="worldImage"]');
          card.querySelector('.preview-shell').style.display = mode === 'OFF' ? 'none' : 'block';
          if (w.worldDataAvailable && w.worldImageDataUrl && worldVersions.get(w.id) !== w.worldSnapshotVersion) {
            worldVersions.set(w.id, w.worldSnapshotVersion);
            preview.src = w.worldImageDataUrl;
          }
          const logs = card.querySelector('[data-field="logs"]');
          logs.textContent = w.logs.join('\\n');
          logs.scrollTop = logs.scrollHeight;
        }
        document.getElementById('summary-workers').textContent = running + ' / ' + data.workers.length;
        document.getElementById('summary-focus').textContent = gateway.focusSectorId || '-';
        document.getElementById('summary-control').textContent = gateway.controlOwnerId
          ? (gateway.controlOwnerId === clientId ? 'FOCUS OWNED' : 'HELD')
          : 'FREE';
        document.getElementById('summary-players').textContent = players;
        document.getElementById('summary-units').textContent = units + ' / ' + enemies;
        document.getElementById('summary-buildings').textContent = buildings;
      } catch (e) {
        document.getElementById('subtitle').textContent = '控制层离线：' + e.message;
      }
    }

    function formatTime(seconds) {
      if (seconds == null) return '-';
      const total = Math.floor(seconds);
      const h = Math.floor(total / 3600);
      const m = Math.floor((total % 3600) / 60);
      const s = total % 60;
      return (h ? h + ':' : '') + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
    }
    function animate() {
      displayFrames++;
      requestAnimationFrame(animate);
    }
    requestAnimationFrame(animate);

    async function action(worker, name) {
      await apiFetch('/api/worker/' + worker + '/' + name, {method:'POST'});
      refresh();
    }
    async function sendInputEnvelope(card, type, message = '') {
      const sectorId = card.dataset.worker;
      const sector = latestRenderFrame && latestRenderFrame.sectors.find(item => item.id === sectorId);
      if (!sector || !Number.isFinite(sector.gameTick)) {
        throw new Error('尚未收到 ' + sectorId + ' 的 Game Tick');
      }
      const expectedTick = Math.max(0, Math.floor(sector.gameTick));
      const commandId = crypto.randomUUID
        ? crypto.randomUUID()
        : 'command-' + Date.now() + '-' + Math.random().toString(16).slice(2);
      const envelope = {
        protocolVersion: 1,
        commandId,
        sectorId,
        expectedTick,
        deadlineTick: expectedTick + 6000,
        type,
        message
      };
      const request = () => apiFetch('/api/gateway/input/envelope', {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(envelope)
      });
      try {
        return await request();
      } catch (networkError) {
        return request();
      }
    }
    workersRoot.addEventListener('click', async event => {
      const gatewayButton = event.target.closest('[data-gateway-action]');
      if (gatewayButton) {
        const card = gatewayButton.closest('[data-worker]');
        let response;
        if (gatewayButton.dataset.gatewayAction === 'focus') {
          response = await apiFetch('/api/gateway/focus/' + card.dataset.worker, {method:'POST'});
        } else if (gatewayButton.dataset.gatewayAction === 'view') {
          const current = card.dataset.viewMode || 'FULL';
          let next = current === 'FULL' ? 'SUMMARY' : current === 'SUMMARY' ? 'OFF' : 'FULL';
          if (card.classList.contains('focused') && next === 'OFF') next = 'FULL';
          response = await apiFetch('/api/gateway/views/' + card.dataset.worker + '/' + next, {method:'POST'});
        } else if (gatewayButton.dataset.gatewayAction === 'acquire') {
          response = await apiFetch('/api/gateway/control/acquire/' + card.dataset.worker, {method:'POST'});
        } else if (gatewayButton.dataset.gatewayAction === 'release') {
          response = await apiFetch('/api/gateway/control/release/' + card.dataset.worker, {method:'POST'});
        } else if (gatewayButton.dataset.gatewayAction === 'pause') {
          response = await sendInputEnvelope(card, 'pause');
        } else if (gatewayButton.dataset.gatewayAction === 'resume') {
          response = await sendInputEnvelope(card, 'resume');
        } else if (gatewayButton.dataset.gatewayAction === 'chat') {
          const input = card.querySelector('[data-command]');
          const message = input.value.trim();
          if (!message) return;
          response = await sendInputEnvelope(card, 'chat', message);
          if (response.ok) input.value = '';
        }
        if (response && !response.ok) alert(await response.text());
        refresh();
        return;
      }
      const button = event.target.closest('[data-action]');
      if (!button) return;
      const card = button.closest('[data-worker]');
      const actionName = button.dataset.action;
      await action(card.dataset.worker, actionName);
    });
    const authDialog = document.getElementById('auth-dialog');
    const authUsername = document.getElementById('auth-username');
    const authPassword = document.getElementById('auth-password');
    const authMessage = document.getElementById('auth-message');

    async function submitCredentials(url) {
      const form = new URLSearchParams();
      form.set('username', authUsername.value.trim());
      form.set('password', authPassword.value);
      return apiFetch(url, {
        method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded'},
        body:form.toString()
      });
    }
    document.getElementById('login-button').addEventListener('click', () => {
      authMessage.textContent = '';
      authUsername.value = currentUsername || '';
      authPassword.value = '';
      authDialog.showModal();
      authUsername.focus();
    });
    document.getElementById('auth-login').addEventListener('click', async () => {
      const response = await submitCredentials('/api/gateway/session');
      if (!response.ok) {
        authMessage.textContent = await response.text();
        return;
      }
      location.reload();
    });
    document.getElementById('auth-register').addEventListener('click', async () => {
      const registration = await submitCredentials('/api/auth/register');
      if (!registration.ok) {
        authMessage.textContent = await registration.text();
        return;
      }
      const account = await registration.json();
      authMessage.textContent = '注册成功，角色 ' + account.role + '，正在登录…';
      const login = await submitCredentials('/api/gateway/session');
      if (!login.ok) {
        authMessage.textContent = '注册成功，但登录失败：' + await login.text();
        return;
      }
      location.reload();
    });
    authPassword.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault();
        document.getElementById('auth-login').click();
      }
    });

    const sessionsDialog = document.getElementById('sessions-dialog');
    const sessionsList = document.getElementById('sessions-list');
    const sessionsScope = document.getElementById('sessions-scope');
    const revokeOtherSessions = document.getElementById('revoke-other-sessions');

    function sessionEndpoint() {
      return currentRole === 'ADMIN' ? '/api/admin/sessions' : '/api/account/sessions';
    }
    async function loadSessions() {
      sessionsList.replaceChildren();
      sessionsScope.textContent = currentRole === 'ADMIN'
        ? '管理员视图：显示全部账户和访客 Session。'
        : '当前账户的设备 Session。';
      const response = await apiFetch(sessionEndpoint(), {cache:'no-store'});
      if (!response.ok) {
        sessionsScope.textContent = await response.text();
        return;
      }
      const data = await response.json();
      for (const session of data.sessions) {
        const item = document.createElement('div');
        item.className = 'session-item';
        const text = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = session.label;
        const details = document.createElement('small');
        const identity = session.username ? session.username + ' · ' + session.role : 'GUEST';
        details.textContent = identity + ' · ' + session.clientId.slice(0, 14)
          + '… · 剩余 ' + formatTime(Math.floor(session.expiresInMillis / 1000));
        if (session.current) {
          title.classList.add('session-current');
          title.textContent += ' · 当前设备';
          details.textContent = identity + ' · ' + session.clientId.slice(0, 14) + '…';
        }
        text.append(title, details);
        const revoke = document.createElement('button');
        revoke.textContent = session.current ? '当前' : '撤销';
        revoke.disabled = session.current;
        revoke.addEventListener('click', async () => {
          if (!confirm('撤销这个 Session？')) return;
          const target = sessionEndpoint() + '/' + encodeURIComponent(session.clientId);
          const revokeResponse = await apiFetch(target, {method:'DELETE'});
          if (!revokeResponse.ok) alert(await revokeResponse.text());
          await loadSessions();
        });
        item.append(text, revoke);
        sessionsList.append(item);
      }
      if (!data.sessions.length) {
        sessionsScope.textContent = '当前没有可显示的 Session。';
      }
    }
    document.getElementById('sessions-button').addEventListener('click', () => {
      if (currentRole === 'GUEST') {
        authMessage.textContent = '请先登录账户再管理设备 Session。';
        authDialog.showModal();
        return;
      }
      revokeOtherSessions.hidden = currentRole === 'ADMIN';
      sessionsDialog.showModal();
      loadSessions();
    });
    document.getElementById('refresh-sessions').addEventListener('click', loadSessions);
    document.getElementById('close-sessions').addEventListener('click', () => sessionsDialog.close());
    revokeOtherSessions.addEventListener('click', async () => {
      if (!confirm('撤销当前账户除本机外的全部 Session？')) return;
      const response = await apiFetch('/api/account/sessions', {method:'DELETE'});
      if (!response.ok) alert(await response.text());
      await loadSessions();
    });

    document.getElementById('revoke-session').addEventListener('click', async () => {
      if (!clientId || !confirm('销毁当前会话并释放它的控制权？页面会以新的访客身份重新加载。')) return;
      if (renderStream) renderStream.close();
      const response = await apiFetch('/api/gateway/session', {method:'DELETE'});
      if (!response.ok) {
        alert(await response.text());
        return;
      }
      location.reload();
    });
    document.getElementById('start-all').addEventListener('click', async () => { await apiFetch('/api/start-all',{method:'POST'}); refresh(); });
    document.getElementById('stop-all').addEventListener('click', async () => { await apiFetch('/api/stop-all',{method:'POST'}); refresh(); });

    async function startDashboard() {
      await establishSession();
      renderStream = new EventSource('/api/render/stream');
      renderStream.addEventListener('render', event => {
        renderConnected = true;
        renderFrames++;
        latestRenderFrame = JSON.parse(event.data);
      });
      renderStream.onerror = () => { renderConnected = false; };
      setInterval(() => {
        const latestTick = latestRenderFrame && latestRenderFrame.sectors.length
          ? Math.max(...latestRenderFrame.sectors.map(s => s.gameTick))
          : 0;
        document.getElementById('game-pipe-status').textContent = latestRenderFrame
          ? '帧 #' + latestRenderFrame.sequence + ' · tick ' + Math.floor(latestTick)
          : '等待 Game Tick';
        document.getElementById('render-pipe-status').textContent = (renderConnected ? 'render ' + displayFrames + ' fps' : '快照流断开')
          + ' · snapshots ' + renderFrames + '/s';
        renderFrames = 0;
        displayFrames = 0;
      }, 1000);
      setInterval(async () => {
        if (latestGateway && latestGateway.controlOwnerId === clientId && latestGateway.focusSectorId) {
          await apiFetch('/api/gateway/control/renew/' + latestGateway.focusSectorId, {method:'POST'});
        }
      }, 5000);
      await refresh();
      setInterval(refresh, 1000);
    }

    startDashboard().catch(error => {
      document.getElementById('subtitle').textContent = '安全会话失败：' + error.message;
    });
  </script>
</body>
</html>
""";
    }
}
