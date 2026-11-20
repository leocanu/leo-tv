with open('ar_iptv.m3u', encoding='utf-8', errors='ignore') as f:
    text = f.read()

import re
matches = re.findall(r'#EXTINF:[^\n]+,([^\n]+)\n[^\n]*69393EAE6ADBC65A68F942022362A202/(\d+)\.m3u8', text)
for name, num in matches:
    print(f"Num {num}: {name}")

