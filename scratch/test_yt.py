import urllib.request
import json

q = urllib.parse.quote('abismo de pasion capitulo 1')
url = f'https://www.youtube.com/results?search_query={q}'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    import re
    video_ids = re.findall(r'"videoId":"([a-zA-Z0-9_-]{11})"', html)
    titles = re.findall(r'"title":\{"runs":\[\{"text":"([^"]+)"\}', html)
    print('Found videos count:', len(video_ids))
    for i in range(min(5, len(video_ids), len(titles))):
        print(f'{i+1}. {titles[i]} -> https://www.youtube.com/watch?v={video_ids[i]}')
