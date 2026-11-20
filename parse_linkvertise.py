import re
import json

with open("link_target_8827724.html", "r", encoding="utf-8") as f:
    text = f.read()

# Look for title or target
for m in re.finditer(r'<title>(.*?)</title>', text, re.I):
    print("Linkvertise Title:", m.group(1))

# Check for JSON payloads or target urls
for m in re.finditer(r'\{[^{}]*"target"[^{}]*\}', text):
    print("Target JSON:", m.group(0))

for m in re.finditer(r'\{[^{}]*"title"[^{}]*\}', text):
    print("Title JSON:", m.group(0)[:200])

for m in re.finditer(r'https?://[^\s"\'<>]+', text):
    u = m.group(0)
    if any(x in u for x in ['mediafire', 'mega.nz', 'drive.google', 'dropbox', 'apk', 'download']):
        print("Candidate DL in HTML:", u)
