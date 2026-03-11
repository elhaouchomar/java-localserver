#!/usr/bin/env python3
import os
import sys
import time

CGI_VARS = [
    "REQUEST_METHOD",
    "QUERY_STRING",
    "CONTENT_LENGTH",
    "CONTENT_TYPE",
    "PATH_INFO",
    "SCRIPT_FILENAME",
    "HTTP_HOST",
    "HTTP_USER_AGENT",
]

time.sleep(10)

method = os.environ.get("REQUEST_METHOD", "GET")
query  = os.environ.get("QUERY_STRING", "")
host   = os.environ.get("HTTP_HOST", "unknown")
clen   = os.environ.get("CONTENT_LENGTH", "0")

body = ""
if method == "POST" and int(clen) > 0:
    body = sys.stdin.read(int(clen))

rows = ""
for key in CGI_VARS:
    val = os.environ.get(key, "(not set)")
    rows += f"<tr><td>{key}</td><td>{val}</td></tr>\n"

print("Content-Type: text/html")
print("Status: 200 OK")
print()
print(f"""<!DOCTYPE html>
<html>
<head><title>CGI Hello</title></head>
<body>
  <h1>CGI Hello from {host}</h1>
  <p><b>Method:</b> {method}</p>
  <p><b>Query:</b> {query if query else '(none)'}</p>
  <p><b>Body:</b> {body if body else '(none)'}</p>
  <h2>CGI Environment</h2>
  <table border="1" cellpadding="4">
    <tr><th>Variable</th><th>Value</th></tr>
    {rows}
  </table>
</body>
</html>""")
