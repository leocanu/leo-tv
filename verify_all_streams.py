import urllib.request
import re

for stream_name in ['nba01', 'premiere2', 'espn5']:
    url = f'https://tvf90.com/5.php?stream={stream_name}'
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)',
        'Referer': f'https://tvf90.com/1.php?stream={stream_name}'
    }
    try:
        html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10).read().decode('utf-8', errors='ignore')
        m = re.search(r'(https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*)', html)
        if m:
            print(f"Success for {stream_name}:", m.group(1)[:80], "...")
        else:
            print(f"No m3u8 found for {stream_name}, html len: {len(html)}")
    except Exception as e:
        print(f"Error for {stream_name}:", e)
