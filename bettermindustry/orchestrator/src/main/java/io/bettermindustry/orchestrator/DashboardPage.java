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
    body { margin:0; background:radial-gradient(circle at top left,#18304a 0,var(--bg) 38%,#05090d 100%); color:var(--text); font:14px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace; }
    header { padding:26px 30px 10px; display:flex; justify-content:space-between; gap:20px; align-items:end; }
    h1 { margin:0; font:700 25px ui-sans-serif,system-ui,sans-serif; letter-spacing:.02em; }
    .subtitle { color:var(--muted); margin-top:5px; }
    .toolbar { display:flex; gap:8px; }
    button { border:1px solid var(--line); background:#172536; color:var(--text); padding:8px 12px; border-radius:7px; cursor:pointer; font:inherit; }
    button:hover { border-color:var(--accent); background:#1d3146; }
    button.primary { border-color:#2b6c9c; background:#19415e; }
    button.danger { color:#ffd4d4; border-color:#613434; background:#3a2025; }
    .pipeline { margin:18px 30px 0; padding:14px 18px; border:1px solid var(--line); background:#0d1824cc; border-radius:12px; display:flex; align-items:center; gap:18px; flex-wrap:wrap; }
    .pipe-node { padding:9px 13px; border:1px solid #2b4c69; border-radius:8px; background:#122538; }
    .pipe-arrow { color:var(--accent); font-size:20px; }
    .pipe-note { color:var(--muted); }
    main { padding:20px 30px 30px; display:grid; grid-template-columns:repeat(auto-fit,minmax(380px,1fr)); gap:18px; }
    .worker { min-width:0; background:linear-gradient(150deg,#142232,#0e1721); border:1px solid var(--line); border-radius:13px; overflow:hidden; box-shadow:0 16px 40px #0005; }
    .worker-head { padding:16px 18px; display:flex; justify-content:space-between; gap:15px; border-bottom:1px solid var(--line); }
    .worker-title { font:700 18px ui-sans-serif,system-ui,sans-serif; }
    .state { align-self:start; font-size:12px; padding:4px 8px; border-radius:20px; background:#1b2b3b; color:var(--muted); }
    .state.RUNNING { color:#9ff0bb; background:#163d2a; }
    .state.STARTING,.state.STOPPING { color:#ffe0a0; background:#44351b; }
    .state.FAILED { color:#ffb0b0; background:#4b2228; }
    .meta { padding:13px 18px; display:grid; grid-template-columns:90px 1fr; gap:5px 12px; color:var(--muted); }
    .meta strong { color:var(--text); font-weight:500; overflow-wrap:anywhere; }
    .controls { padding:0 18px 14px; display:flex; gap:8px; flex-wrap:wrap; }
    .command { display:flex; gap:8px; padding:0 18px 15px; }
    .command input { flex:1; min-width:0; border:1px solid var(--line); background:#09131d; color:var(--text); padding:9px 10px; border-radius:7px; font:inherit; }
    .logs { height:245px; margin:0; padding:13px 16px; overflow:auto; background:#060b10; border-top:1px solid var(--line); color:#9fb4c5; white-space:pre-wrap; overflow-wrap:anywhere; font-size:12px; }
    .footer { color:var(--muted); padding:0 30px 28px; font-size:12px; }
    @media (max-width:700px) { header { align-items:start; flex-direction:column; } main { padding:16px; } .pipeline { margin:16px; } .footer { padding-left:16px; padding-right:16px; } }
  </style>
</head>
<body>
  <header>
    <div><h1>BetterMindustry Debugger</h1><div class="subtitle" id="subtitle">正在连接本地控制层…</div></div>
    <div class="toolbar"><button class="primary" id="start-all">启动全部</button><button class="danger" id="stop-all">停止全部</button></div>
  </header>
  <section class="pipeline">
    <span class="pipe-node">Game Tick<br><small id="game-pipe-status">World Worker 运行中</small></span>
    <span class="pipe-arrow">→</span>
    <span class="pipe-node">Render Tick<br><small id="render-pipe-status">正在连接快照流…</small></span>
    <span class="pipe-note">浏览器是独立渲染进程：以自身帧率消费只读 Game Tick 快照，不回写权威世界。</span>
  </section>
  <main id="workers"></main>
  <div class="footer">本地控制面板 · 仅监听 127.0.0.1 · 命令和日志来自真实 Server Worker</div>
  <script>
    const cards = new Map();
    const workersRoot = document.getElementById('workers');
    let renderFrames = 0;
    let displayFrames = 0;
    let latestRenderFrame = null;
    let renderConnected = false;
    function set(card, name, value) { const el = card.querySelector('[data-field="' + name + '"]'); if (el) el.textContent = value ?? '-'; }
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
      subtitle.textContent = worker.map + ' · :' + worker.port;
      title.append(name, subtitle);
      const state = document.createElement('span');
      state.className = 'state';
      state.dataset.field = 'state';
      state.textContent = 'STOPPED';
      head.append(title, state);
      article.append(head);

      const meta = document.createElement('div');
      meta.className = 'meta';
      [['进程 PID','pid'],['运行时长','uptime'],['退出码','exit'],['端口监听','portOpen'],['Game Tick','tick'],['游戏时间','gameTime'],['波次','wave'],['单位 / 建筑','entities'],['暂停','paused'],['检查点','checkpoint'],['工作目录','runtime']].forEach(([label, field]) => {
        const key = document.createElement('span');
        key.textContent = label;
        const value = document.createElement('strong');
        value.dataset.field = field;
        value.textContent = '-';
        meta.append(key, value);
      });
      article.append(meta);

      const controls = document.createElement('div');
      controls.className = 'controls';
      [['start','启动'],['stop','停止'],['save','保存检查点']].forEach(([action,label]) => {
        const button = document.createElement('button');
        button.dataset.action = action;
        button.textContent = label;
        controls.append(button);
      });
      article.append(controls);

      const command = document.createElement('div');
      command.className = 'command';
      const input = document.createElement('input');
      input.dataset.command = '';
      input.placeholder = '向 ' + worker.id + ' 发送服务端命令…';
      const send = document.createElement('button');
      send.dataset.action = 'command';
      send.textContent = '发送';
      command.append(input, send);
      article.append(command);

      const logs = document.createElement('pre');
      logs.className = 'logs';
      logs.dataset.field = 'logs';
      article.append(logs);
      workersRoot.append(article);
      cards.set(worker.id, article);
      return article;
    }
    async function refresh() {
      try {
        const data = await fetch('/api/overview', {cache:'no-store'}).then(r => r.json());
        document.getElementById('subtitle').textContent = 'Project: ' + data.projectRoot;
        for (const w of data.workers) {
          const card = cards.get(w.id) || createCard(w);
          set(card, 'state', w.state);
          card.querySelector('[data-field="state"]').className = 'state ' + stateClass(w.state);
          set(card, 'pid', w.pid ?? '-');
          set(card, 'uptime', w.alive ? w.uptimeSeconds + 's' : '-');
          set(card, 'exit', w.exitCode ?? '-');
          set(card, 'portOpen', w.portOpen ? 'listening' : 'closed');
          set(card, 'tick', w.diagnostics ? Math.floor(w.diagnostics.tick) : '-');
          set(card, 'gameTime', w.diagnostics ? formatTime(w.diagnostics.gameTimeSeconds) : '-');
          set(card, 'wave', w.diagnostics ? w.diagnostics.wave : '-');
          set(card, 'entities', w.diagnostics ? w.diagnostics.units + ' / ' + w.diagnostics.buildings : '-');
          set(card, 'paused', w.diagnostics ? (w.diagnostics.paused ? 'yes' : 'no') : '-');
          set(card, 'checkpoint', w.checkpoint ? 'available' : 'none');
          set(card, 'runtime', w.runtimeDir);
          const logs = card.querySelector('[data-field="logs"]');
          logs.textContent = w.logs.join('\\n');
          logs.scrollTop = logs.scrollHeight;
        }
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
    const renderStream = new EventSource('/api/render/stream');
    renderStream.addEventListener('render', event => {
      renderConnected = true;
      renderFrames++;
      latestRenderFrame = JSON.parse(event.data);
    });
    renderStream.onerror = () => {
      renderConnected = false;
    };
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
    async function action(worker, name) {
      await fetch('/api/worker/' + worker + '/' + name, {method:'POST'});
      refresh();
    }
    workersRoot.addEventListener('click', async event => {
      const button = event.target.closest('[data-action]');
      if (!button) return;
      const card = button.closest('[data-worker]');
      const actionName = button.dataset.action;
      if (actionName === 'command') {
        const input = card.querySelector('[data-command]');
        const command = input.value.trim();
        if (!command) return;
        await fetch('/api/worker/' + card.dataset.worker + '/command', {method:'POST', body:command});
        input.value = '';
      } else {
        await action(card.dataset.worker, actionName);
      }
    });
    document.getElementById('start-all').addEventListener('click', async () => { await fetch('/api/start-all',{method:'POST'}); refresh(); });
    document.getElementById('stop-all').addEventListener('click', async () => { await fetch('/api/stop-all',{method:'POST'}); refresh(); });
    refresh();
    setInterval(refresh, 1000);
  </script>
</body>
</html>
""";
    }
}
