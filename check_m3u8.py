import urllib.request
import re

html = urllib.request.urlopen(urllib.request.Request('https://tvf90.com/5.php?stream=espn', headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://tvf90.com/'})).read().decode('utf-8')
m = re.search(r'(https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*)', html)
if m:
    m3u8_url = m.group(1)
    print('m3u8_url:', m3u8_url)
    playlist = urllib.request.urlopen(urllib.request.Request(m3u8_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://tvf90.com/'})).read().decode('utf-8')
    print('PLAYLIST:\n', playlist)
    # Check first TS url
    lines = [l.strip() for l in playlist.split('\n') if l.strip() and not l.startswith('#')]
    if lines:
        print('First TS line:', lines[0])
        # Try full url with urllib.parse.urljoin
        import urllib.parse
        full_ts = urllib.parse.urljoin(m3u8_url, lines[0])
        print('Full TS url:', full_ts)
        try:
            ts_res = urllib.request.urlopen(urllib.request.Request(full_ts, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://tvf90.com/'}), timeout=5)
            print('TS Status:', ts_res.status, 'len:', len(ts_res.read(100)))
        except Exception as e:
            print('TS Error:', e)
