import urllib.request

stream_url = "https://6.ftlly.com:443/espn2/mono.m3u8?token=129974460c308f1338b6caa695aa0d1868f7acc0-90-1795161480-1795143480"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://tvf90.com/'
}

try:
    req = urllib.request.Request(stream_url, headers=headers)
    resp = urllib.request.urlopen(req, timeout=10)
    print("Status code:", resp.getcode())
    print("Content-Type:", resp.headers.get('Content-Type'))
    content = resp.read().decode('utf-8')
    print("M3U8 preview:\n", content[:500])
except Exception as e:
    print("Error:", e)
