import urllib.request
import re
import json

url = "https://www.youtube.com/results?search_query=Abismo+de+pasion+capitulo+1+completo"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
try:
    with urllib.request.urlopen(req, timeout=8) as r:
        html = r.read().decode('utf-8', errors='ignore')
        video_ids = re.findall(r'/watch\?v=([a-zA-Z0-9_-]{11})', html)
        print("YouTube video IDs for Abismo de pasion cap 1:", list(dict.fromkeys(video_ids))[:5])
except Exception as e:
    print("YT err:", e)
