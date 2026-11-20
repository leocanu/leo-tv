with open('xuper_plugin.js', 'r', encoding='utf-8') as f:
    text = f.read()

import re

for term in ['vegetatvv2', 'futbol-libre', 'cablevisionhd', 'streamtp99a', 'middleware.ditu']:
    print(f"\n================ Searches for {term} ================")
    matches = [m.start() for m in re.finditer(re.escape(term), text)]
    for idx in matches[:3]:
        start = max(0, idx - 150)
        end = min(len(text), idx + 250)
        print("...", text[start:end], "...\n")
