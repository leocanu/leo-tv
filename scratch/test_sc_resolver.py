import urllib.request, re, ssl, json

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://soundcloud.com', headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req, timeout=5, context=ctx).read().decode('utf-8')
scripts = re.findall(r'src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"', html)
print('Found scripts on soundcloud.com:', len(scripts))

client_id = None
for s in reversed(scripts):
    try:
        sreq = urllib.request.Request(s, headers={'User-Agent': 'Mozilla/5.0'})
        js = urllib.request.urlopen(sreq, timeout=5, context=ctx).read().decode('utf-8')
        m = re.search(r'client_id[:=]"([a-zA-Z0-9]{32})"', js)
        if m:
            client_id = m.group(1)
            print('FOUND LIVE SOUNDCLOUD CLIENT_ID:', client_id)
            break
    except:
        pass

if client_id:
    search_url = f"https://api-v2.soundcloud.com/search/tracks?q=Coldplay&client_id={client_id}&limit=3"
    sreq = urllib.request.Request(search_url, headers={'User-Agent': 'Mozilla/5.0'})
    res = json.loads(urllib.request.urlopen(sreq, timeout=5, context=ctx).read().decode('utf-8'))
    tracks = res.get('collection', [])
    print(f'Search tracks returned: {len(tracks)} tracks!')
    for item in tracks:
        print('  * Track:', item.get('title'), 'id:', item.get('id'))
        media = item.get('media', {}).get('transcodings', [])
        for m in media:
            if m.get('format', {}).get('protocol') == 'progressive':
                # resolve stream
                stream_url = m.get('url') + f"?client_id={client_id}"
                s_res = json.loads(urllib.request.urlopen(urllib.request.Request(stream_url, headers={'User-Agent': 'Mozilla/5.0'}), context=ctx).read().decode('utf-8'))
                print('    -> DIRECT MP3 AUDIO URL:', s_res.get('url')[:100] + '...')
