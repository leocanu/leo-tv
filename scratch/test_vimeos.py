import urllib.request, re

embed_url = 'https://vimeos.net/embed-n1heuxm4500w.html?cf=eyJhbGciOiJIUzI1NiIsImtpZCI6Im1haW4iLCJ0eXAiOiJWSURBUEktQ0YifQ.eyJhZHMiOjAsImFvIjpbImh0dHBzOi8vdmlkZW9hcHAubW92IiwiaHR0cHM6Ly9jaW5laGF4Lm9yZyJdLCJhcCI6ImRlZmF1bHQiLCJjIjp7ImFjY2VudCI6IiNmNTllMGIiLCJpY29uIjoiI2ZmZmZmZiIsInNlY29uZGFyeSI6IiMxMTE4MjcifSwiY2siOiJtb3ZpZSIsImN0IjozMCwiY3YiOjgsImRtIjoxLCJlZSI6Imh0dHBzOi8vdmlkZW9hcHAubW92L18vciIsImVyIjp0cnVlLCJleHAiOjE3OTU0Njc1NzgsImZnIjp7ImFuIjpmYWxzZSwiYXAiOmZhbHNlLCJoYyI6ZmFsc2UsImh0IjpmYWxzZSwicXMiOnRydWUsInJzIjp0cnVlLCJzYSI6MCwic2kiOmZhbHNlLCJzdCI6dHJ1ZSwidGgiOmZhbHNlfSwiaWF0IjoxNzk1NDQ1OTc4LCJraWQiOiJtYWluIiwibSI6InZpcCIsIm1pIjp7ImNhdGFsb2dfa2luZCI6Im1vdmllIiwiZXBpc29kZSI6bnVsbCwibWVkaWFfdHlwZSI6Im1vdmllIiwic2Vhc29uIjpudWxsLCJ0aXRsZSI6Ik1hdHJpeCIsInRtZGIiOjYwMywidHlwZSI6Im1vdmllIn0sIm10IjoibW92aWUiLCJvIjozLCJvcmlnaW4iOiJodHRwczovL2NpbmVoYXgub3JnIiwicCI6Ijk3MjNhNGY4LTQ0NjAtNGJhZC1iZmNjLTI4ZmUzNWJlMzlmMiIsInBpIjowLCJwbSI6WyJodHRwczovL3ZpZGVvYXBwLm1vdiIsImh0dHBzOi8vY2luZWhheC5vcmciXSwicHVibGljX3Bvc3RfbWVzc2FnZSI6ZmFsc2UsInJyIjowLCJzaWQiOiJwc184YjBiNzI3NWEzNzY0ZGY1YmE5ZTFlY2UwZWNhOTMxYiIsInNrIjoiY2luZW1hdGljIiwidCI6Ik1hdHJpeCIsInRtZGIiOjYwMywidiI6MSwidmlwIjoxLCJ4YyI6MH0.hxuyROfyfjtbkb6bFuI6bg6AarI5yaP4P6XvJD4_YXg'

req = urllib.request.Request(embed_url, headers={
    'User-Agent': 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://videoapp.mov/'
})
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        html = r.read().decode('utf-8', errors='ignore')
        print('Embed length:', len(html))
        # Look for m3u8
        m3u8_links = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html)
        print('M3U8 links:', m3u8_links)
        # Look for packer or jwplayer or sources
        for line in html.splitlines():
            if any(k in line.lower() for k in ['eval', 'sources:', 'file:', 'm3u8', 'master', 'hls']):
                print('Line:', line.strip()[:150])
except Exception as e:
    print('Embed error:', e)
