import re
import urllib.request
import urllib.parse
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

with open('app/src/main/java/ar/appvideo/player/LiveTvEngine.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern for LiveChannel(id, name, catId, streamUrl, backupUrl, ...)
pattern = r'new LiveChannel\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"'
channels = re.findall(pattern, content)

print(f"Total channels found in LiveTvEngine: {len(channels)}\n")

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://tvf90.com/'
}

results = []

for ch_id, ch_name, cat, url in channels:
    test_url = url
    is_dynamic = False
    if 'tvf90.com' in url or 'r=' in url:
        is_dynamic = True
        # Resolve tvf90
        m_stream = re.search(r'stream=([a-zA-Z0-9_-]+)', url)
        if m_stream:
            stream_name = m_stream.group(1)
            resolve_url = f"https://tvf90.com/5.php?stream={stream_name}"
            try:
                req = urllib.request.Request(resolve_url, headers=headers)
                h = urllib.request.urlopen(req, timeout=5, context=ctx).read().decode('utf-8', errors='ignore')
                m_m3u8 = re.search(r'(https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*)', h)
                if m_m3u8:
                    test_url = m_m3u8.group(1)
                else:
                    test_url = None
            except Exception as e:
                test_url = None
    
    if not test_url:
        print(f"[{cat.upper()}] ❌ {ch_name} ({ch_id}): Failed to resolve dynamic stream ({url})")
        results.append((ch_id, ch_name, cat, url, False, "Resolve failed"))
        continue

    try:
        req_hdrs = dict(headers)
        if 'mitelefe.com' in test_url or 'mdstrm.com' in test_url:
            req_hdrs['Referer'] = 'https://mitelefe.com/'
        elif 'ftlly.com' in test_url:
            req_hdrs['Referer'] = 'https://tvf90.com/'
        
        req = urllib.request.Request(test_url, headers=req_hdrs)
        resp = urllib.request.urlopen(req, timeout=5, context=ctx)
        code = resp.status
        # Read a bit
        sample = resp.read(150)
        is_ok = (code == 200 and (b'#EXTM3U' in sample or len(sample) > 50))
        status_str = "OK" if is_ok else f"Code {code} but bad body"
        print(f"[{cat.upper()}] {'✅' if is_ok else '⚠️'} {ch_name} ({ch_id}): {status_str}")
        results.append((ch_id, ch_name, cat, url, is_ok, status_str))
    except Exception as ex:
        print(f"[{cat.upper()}] ❌ {ch_name} ({ch_id}): {ex}")
        results.append((ch_id, ch_name, cat, url, False, str(ex)))

ok_count = sum(1 for r in results if r[4])
print(f"\nSummary: {ok_count}/{len(results)} working channels.")
