package com.Shaurya.miniredis;

/**
 * Holds the static frontend for the browser-based terminal. Kept as a plain
 * Java string (rather than a resource file) so the whole project stays a
 * single self-contained set of .java files with no build-path surprises.
 */
public class Frontend {
    public static final String INDEX_HTML = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>Mini Redis — Live Demo</title>
        <style>
          :root {
            --bg: #0d1117;
            --panel: #161b22;
            --border: #30363d;
            --text: #c9d1d9;
            --accent: #58a6ff;
            --green: #3fb950;
            --red: #f85149;
            --dim: #8b949e;
          }
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background: var(--bg);
            color: var(--text);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            min-height: 100vh;
            padding: 32px 16px;
          }
          .wrap { width: 100%; max-width: 760px; }
          h1 {
            font-size: 1.6rem;
            margin: 0 0 4px 0;
            display: flex;
            align-items: center;
            gap: 10px;
          }
          h1 .dot { color: var(--red); }
          .subtitle { color: var(--dim); margin: 0 0 20px 0; font-size: 0.95rem; }
          .status {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 0.85rem;
            margin-bottom: 16px;
            color: var(--dim);
          }
          .status .indicator {
            width: 8px; height: 8px; border-radius: 50%;
            background: var(--dim);
          }
          .status.connected .indicator { background: var(--green); }
          .status.disconnected .indicator { background: var(--red); }

          .terminal {
            background: var(--panel);
            border: 1px solid var(--border);
            border-radius: 8px;
            overflow: hidden;
          }
          .terminal-header {
            background: #1f2530;
            padding: 8px 12px;
            display: flex;
            gap: 6px;
            border-bottom: 1px solid var(--border);
          }
          .terminal-header span {
            width: 11px; height: 11px; border-radius: 50%;
          }
          .terminal-header span:nth-child(1) { background: #ff5f56; }
          .terminal-header span:nth-child(2) { background: #ffbd2e; }
          .terminal-header span:nth-child(3) { background: #27c93f; }

          #output {
            height: 360px;
            overflow-y: auto;
            padding: 14px;
            font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
            font-size: 0.88rem;
            line-height: 1.5;
            white-space: pre-wrap;
            word-break: break-word;
          }
          #output .line-cmd { color: var(--accent); }
          #output .line-resp { color: var(--text); }
          #output .line-err { color: var(--red); }
          #output .line-sys { color: var(--dim); font-style: italic; }

          .input-row {
            display: flex;
            align-items: center;
            border-top: 1px solid var(--border);
            padding: 10px 14px;
            gap: 8px;
          }
          .prompt { color: var(--green); font-family: monospace; font-size: 0.9rem; }
          #cmd {
            flex: 1;
            background: transparent;
            border: none;
            outline: none;
            color: var(--text);
            font-family: monospace;
            font-size: 0.9rem;
          }

          .examples {
            margin-top: 18px;
            font-size: 0.85rem;
            color: var(--dim);
          }
          .examples code {
            background: var(--panel);
            border: 1px solid var(--border);
            border-radius: 4px;
            padding: 1px 6px;
            color: var(--accent);
            cursor: pointer;
          }
          .examples code:hover { border-color: var(--accent); }

          footer {
            margin-top: 24px;
            font-size: 0.8rem;
            color: var(--dim);
            text-align: center;
          }
          footer a { color: var(--accent); text-decoration: none; }
        </style>
        </head>
        <body>
        <div class="wrap">
          <h1><span class="dot">●</span> Mini Redis</h1>
          <p class="subtitle">An in-memory key-value store built from scratch in Java — sockets, concurrency, persistence and TTLs, no Redis library used. Try real commands below.</p>

          <div class="status disconnected" id="status">
            <span class="indicator"></span>
            <span id="statusText">Connecting…</span>
          </div>

          <div class="terminal">
            <div class="terminal-header"><span></span><span></span><span></span></div>
            <div id="output"></div>
            <div class="input-row">
              <span class="prompt">mini-redis&gt;</span>
              <input id="cmd" type="text" autocomplete="off" autofocus placeholder="SET name gemini" />
            </div>
          </div>

          <div class="examples">
            Try: <code>SET name gemini</code> <code>GET name</code> <code>LPUSH fruits apple</code>
            <code>LRANGE fruits 0 10</code> <code>HSET user:1 age 30</code> <code>EXPIRE name 10</code>
            <code>TTL name</code> <code>DEL name</code>
          </div>

          <footer>
            Source and design notes on <a href="#" id="repoLink" target="_blank" rel="noopener">GitHub</a>.
          </footer>
        </div>

        <script>
          const output = document.getElementById('output');
          const input = document.getElementById('cmd');
          const statusEl = document.getElementById('status');
          const statusText = document.getElementById('statusText');

          function log(text, cls) {
            const div = document.createElement('div');
            div.className = cls;
            div.textContent = text;
            output.appendChild(div);
            output.scrollTop = output.scrollHeight;
          }

          function setStatus(connected, text) {
            statusEl.className = 'status ' + (connected ? 'connected' : 'disconnected');
            statusText.textContent = text;
          }

          const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
          const ws = new WebSocket(protocol + window.location.host + '/ws');

          ws.onopen = () => {
            setStatus(true, 'Connected');
            log('Connected to Mini Redis. Type a command and press Enter.', 'line-sys');
          };
          ws.onclose = () => setStatus(false, 'Disconnected — refresh to reconnect');
          ws.onerror = () => setStatus(false, 'Connection error');

          ws.onmessage = (event) => {
            const isError = event.data.startsWith('ERR');
            log(event.data, isError ? 'line-err' : 'line-resp');
          };

          input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && input.value.trim() !== '') {
              const command = input.value;
              log('mini-redis> ' + command, 'line-cmd');
              if (ws.readyState === WebSocket.OPEN) {
                ws.send(command);
              } else {
                log('Not connected.', 'line-err');
              }
              input.value = '';
            }
          });

          document.querySelectorAll('.examples code').forEach(el => {
            el.addEventListener('click', () => {
              input.value = el.textContent;
              input.focus();
            });
          });
        </script>
        </body>
        </html>
        """;
}
