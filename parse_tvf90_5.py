import urllib.request
import re

url = 'https://tvf90.com/5.php?stream=espn2'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://tvf90.com/1.php?stream=espn2'
}

html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10).read().decode('utf-8', errors='ignore')
print("HTML length:", len(html))

# Search for source or m3u8
for m in re.finditer(r'source:\s*["\']([^"\']+)["\']', html):
    print("Source:", m.group(1))

for m in re.finditer(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html):
    print("M3U8:", m.group(0))

for m in re.finditer(r'playbackURL\s*=\s*["\']([^"\']+)["\']', html):
    print("playbackURL:", m.group(1))

print("HTML preview:\n", html[:1200])
