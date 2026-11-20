import urllib.request
import base64
import re

b64 = "aHR0cHM6Ly90dmY5MC5jb20vMS5waHA/c3RyZWFtPWVzcG4y"
decoded_url = base64.b64decode(b64).decode('utf-8')
print("Decoded URL:", decoded_url)

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://futbol-libre.app/'
}

try:
    req = urllib.request.Request(decoded_url, headers=headers)
    html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
    print("HTML length:", len(html))
    
    # Search for m3u8 or player
    for m in re.finditer(r'(https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*)', html):
        print("Found m3u8:", m.group(1))
    
    # Search for Clappr / source / file / var
    for m in re.finditer(r'source:\s*["\']([^"\']+)["\']', html):
        print("Clappr source:", m.group(1))

    for m in re.finditer(r'file:\s*["\']([^"\']+)["\']', html):
        print("JWPlayer file:", m.group(1))

    for m in re.finditer(r'var\s+playbackURL\s*=\s*["\']([^"\']+)["\']', html):
        print("playbackURL:", m.group(1))
        
    print("\nPreview HTML snippet:\n", html[:1200])
except Exception as e:
    print("Error:", e)

