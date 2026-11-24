import json, re

with open(r'c:\workspace\app-video-propia-android-v1\link_center.html', 'r', encoding='utf-8') as f:
    text = f.read()

title = re.findall(r'<title>([^<]+)</title>', text)
print('Title:', title)

found_urls = re.findall(r'https?://[^\s"\'<>]+(?:mediafire|mega\.nz|drive\.google|dropbox|github|\.apk)[^\s"\'<>]*', text)
print('Found target URLs:', found_urls)

links = re.findall(r'"target":\s*"([^"]+)"', text)
print('Target field:', links)

names = re.findall(r'"title":\s*"([^"]+)"', text)
print('Title fields:', names[:5])

# Print anything mentioning android, app, apk, tv
for line in text.splitlines():
    if any(k in line.lower() for k in ['apk', 'mediafire', 'stremio', 'tele', 'magis', 'play', 'tv']):
        if len(line.strip()) < 200:
            print('Line:', line.strip())
