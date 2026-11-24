import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://tvf90.com/'
}

for endpoint in ['https://tvf90.com/1.php?stream=espn', 'https://tvf90.com/5.php?stream=espn', 'https://tvf90.com/5.php?stream=espn2']:
    try:
        req = urllib.request.Request(endpoint, headers=headers)
        html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8', errors='ignore')
        print(f"=== {endpoint} (len={len(html)}) ===")
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html, re.I)
        print("iframes:", iframes)
        m3u8s = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html)
        print("m3u8s:", m3u8s)
        sources = re.findall(r'(?:source|file|playbackURL|src)\s*[:=]\s*["\']([^"\']+)["\']', html)
        print("sources:", sources)
        for s in sources:
            if 'http' in s:
                print(" -> testing stream:", s)
                try:
                    s_req = urllib.request.Request(s, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://tvf90.com/'})
                    res = urllib.request.urlopen(s_req, timeout=5)
                    print("    HTTP Status:", res.status)
                except Exception as ex:
                    print("    Stream error:", ex)
    except Exception as e:
        print(f"Error {endpoint}:", e)
