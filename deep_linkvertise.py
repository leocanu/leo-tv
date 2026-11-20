import re
import json

with open("link_target_8827724.html", "r", encoding="utf-8") as f:
    text = f.read()

# Look for script tags
scripts = re.findall(r'<script[^>]*>(.*?)</script>', text, re.S)
print("Found scripts:", len(scripts))
for i, s in enumerate(scripts):
    if len(s) > 100:
        print(f"Script {i} len {len(s)}: {s[:300]}...")

# Look for window.__INITIAL_STATE__ or similar
for m in re.finditer(r'window\.[a-zA-Z0-9_]+\s*=\s*(\{.*?\});', text):
    print("Window var:", m.group(0)[:300])
