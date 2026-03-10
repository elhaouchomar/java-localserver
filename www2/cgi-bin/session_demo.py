#!/usr/bin/env python3
"""
Cookie & Session demo CGI.

COOKIES  — set via query params:
  /cgi-bin/session_demo.py?name=alice&theme=dark
  The server reads those params and sends Set-Cookie headers.
  On the next request the browser sends them back automatically.

SESSION  — keyed by a SESSION_ID cookie (HttpOnly).
  The browser only holds the UUID key; visit count and messages
  live in /tmp/cgi_sessions/<uuid>.json on the server.
"""

import os, json, uuid, time, html
from pathlib import Path
from urllib.parse import unquote_plus

SESSION_DIR     = Path("/tmp/cgi_sessions")
SESSION_TIMEOUT = 30 * 60
SESSION_COOKIE  = "SESSION_ID"

# ── helpers ───────────────────────────────────────────────────────────────────

def parse_cookies(raw):
    out = {}
    for part in (raw or "").split(";"):
        k, _, v = part.strip().partition("=")
        if k:
            out[k.strip()] = v.strip()
    return out

def parse_query(qs):
    out = {}
    for pair in (qs or "").split("&"):
        k, _, v = pair.partition("=")
        if k:
            out[unquote_plus(k)] = unquote_plus(v)
    return out

def load_session(sid):
    if sid:
        p = SESSION_DIR / f"{sid}.json"
        try:
            data = json.loads(p.read_text())
            if time.time() - data["last_seen"] < SESSION_TIMEOUT:
                return data, False
            p.unlink(missing_ok=True)
        except Exception:
            pass
    new_id = str(uuid.uuid4())
    return {"id": new_id, "created_at": time.time(),
            "last_seen": time.time(), "visits": 0}, True

def save_session(data):
    SESSION_DIR.mkdir(parents=True, exist_ok=True)
    data["last_seen"] = time.time()
    (SESSION_DIR / f"{data['id']}.json").write_text(json.dumps(data))

def esc(s): return html.escape(str(s))
def fmt(ts): return time.strftime("%H:%M:%S", time.localtime(ts))

# ── request ───────────────────────────────────────────────────────────────────

cookies = parse_cookies(os.environ.get("HTTP_COOKIE", ""))
params  = parse_query(os.environ.get("QUERY_STRING", ""))

# ── session ───────────────────────────────────────────────────────────────────

session, is_new = load_session(cookies.get(SESSION_COOKIE))
session["visits"] += 1
save_session(session)

# ── response headers ──────────────────────────────────────────────────────────

resp_headers = ["Content-Type: text/html; charset=utf-8"]

if is_new:
    resp_headers.append(
        f"Set-Cookie: {SESSION_COOKIE}={session['id']}; Path=/; HttpOnly; SameSite=Lax"
    )

# Set any cookie the caller asked for via query params
# e.g. ?name=alice&theme=dark  →  Set-Cookie: name=alice ...
ALLOWED_COOKIE_PARAMS = {"name", "theme", "lang"}
for key in ALLOWED_COOKIE_PARAMS:
    if key in params:
        val = params[key][:64]
        resp_headers.append(f"Set-Cookie: {key}={esc(val)}; Path=/; Max-Age=3600; SameSite=Lax")

print("\r\n".join(resp_headers))
print()

# ── build cookie table ────────────────────────────────────────────────────────

cookie_rows = ""
for k, v in cookies.items():
    if k == SESSION_COOKIE:
        desc = "session key — server uses this to load session data"
    else:
        desc = "plain cookie — value lives in browser"
    cookie_rows += f"<tr><td><code>{esc(k)}</code></td><td><code>{esc(v)}</code></td><td>{desc}</td></tr>"

if not cookie_rows:
    cookie_rows = "<tr><td colspan='3'><em>no cookies yet</em></td></tr>"

expires_in = max(0, int(SESSION_TIMEOUT - (time.time() - session["last_seen"])))

# ── html ──────────────────────────────────────────────────────────────────────

base = "/cgi-bin/session_demo.py"

print(f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Cookie &amp; Session Demo</title>
  <style>
    body  {{ font-family: monospace; max-width: 760px; margin: 40px auto; padding: 0 20px; background:#0d1117; color:#c9d1d9; }}
    h1    {{ color:#58a6ff; }}
    h2    {{ color:#58a6ff; font-size:1em; margin:0 0 10px; }}
    .card {{ background:#161b22; border:1px solid #30363d; border-radius:6px; padding:16px; margin:16px 0; }}
    table {{ width:100%; border-collapse:collapse; font-size:.9em; }}
    th    {{ text-align:left; color:#8b949e; padding:4px 8px; border-bottom:1px solid #30363d; }}
    td    {{ padding:5px 8px; vertical-align:top; word-break:break-all; }}
    code  {{ background:rgba(110,118,129,.2); padding:1px 5px; border-radius:3px; }}
    a     {{ color:#58a6ff; }}
    .new  {{ color:#3fb950; }} .old {{ color:#58a6ff; }}
    .hint {{ color:#8b949e; font-size:.82em; margin-top:8px; }}
  </style>
</head>
<body>
<h1>Cookie &amp; Session Demo</h1>

<!-- ── COOKIES ── -->
<div class="card">
  <h2>🍪 Cookies</h2>
  <p>Raw header received: <code>Cookie: {esc(os.environ.get("HTTP_COOKIE","(none)"))}</code></p>
  <table>
    <tr><th>Name</th><th>Value</th><th>What it is</th></tr>
    {cookie_rows}
  </table>
  <p class="hint">
    Set a cookie via query param → server replies with <code>Set-Cookie</code>
    → browser stores it and sends it back on every request:<br>
    <a href="{base}?name=alice">?name=alice</a> &nbsp;
    <a href="{base}?theme=dark">?theme=dark</a> &nbsp;
    <a href="{base}?name=alice&theme=dark&lang=en">?name=alice&amp;theme=dark&amp;lang=en</a>
  </p>
</div>

<!-- ── SESSION ── -->
<div class="card">
  <h2>🗄️ Session</h2>
  <p class="{'new' if is_new else 'old'}">
    {"🆕 New — sent <code>Set-Cookie: SESSION_ID=" + esc(session['id'][:12]) + "…</code>" if is_new
     else "✅ Resumed via cookie <code>SESSION_ID=" + esc(cookies.get(SESSION_COOKIE,"")[:12]) + "…</code>"}
  </p>
  <table>
    <tr><th>Field</th><th>Value</th></tr>
    <tr><td>ID</td><td><code>{esc(session['id'])}</code></td></tr>
    <tr><td>Created</td><td>{esc(fmt(session['created_at']))}</td></tr>
    <tr><td>Visits</td><td><strong>{session['visits']}</strong></td></tr>
    <tr><td>Expires in</td><td>{expires_in}s</td></tr>
  </table>
  <p class="hint">
    Reload this page — the visit counter increases each time.<br>
    Open in a new private window — you get a fresh session with visit count starting at 1.
  </p>
</div>

<p><a href="{base}">↩ Reload</a></p>
</body>
</html>""")
