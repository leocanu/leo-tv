import urllib.request, re

embed = 'https://vimeos.net/embed-3mmvyf5cw8rq.html?cf=eyJhbGciOiJIUzI1NiIsImtpZCI6Im1haW4iLCJ0eXAiOiJWSURBUEktQ0YifQ.eyJhZHMiOjAsImFvIjpbImh0dHBzOi8vdmlkZW9hcHAubW92IiwiaHR0cHM6Ly9jaW5laGF4Lm9yZyJdLCJhcCI6ImRlZmF1bHQiLCJjIjp7ImFjY2VudCI6IiNmNTllMGIiLCJpY29uIjoiI2ZmZmZmZiIsInNlY29uZGFyeSI6IiMxMTE4MjcifSwiY2siOiJtb3ZpZSIsImN0IjozMCwiY3YiOjgsImRtIjoxLCJlZSI6Imh0dHBzOi8vdmlkZW9hcHAubW92L18vciIsImVyIjp0cnVlLCJleHAiOjE3OTU0OTg2MjIsImZnIjp7ImFuIjpmYWxzZSwiYXAiOmZhbHNlLCJoYyI6ZmFsc2UsImh0IjpmYWxzZSwicXMiOnRydWUsInJzIjp0cnVlLCJzYSI6MCwic2kiOmZhbHNlLCJzdCI6dHJ1ZSwidGgiOmZhbHNlfSwiaWF0IjoxNzk1NDc3MDIyLCJraWQiOiJtYWluIiwibSI6InZpcCIsIm1pIjp7ImNhdGFsb2dfa2luZCI6Im1vdmllIiwiZXBpc29kZSI6bnVsbCwibWVkaWFfdHlwZSI6Im1vdmllIiwic2Vhc29uIjpudWxsLCJ0aXRsZSI6IlRvcm5hZG8iLCJ0bWRiIjo2NjQsInR5cGUiOiJtb3ZpZSJ9LCJtdCI6Im1vdmllIiwibyI6Mywib3JpZ2luIjoiaHR0cHM6Ly9jaW5laGF4Lm9yZyIsInAiOiI5NzIzYTRmOC00NDYwLTRiYWQtYmZjYy0yOGZlMzViZTM5ZjIiLCJwaSI6MCwicG0iOlsiaHR0cHM6Ly92aWRlb2FwcC5tb3YiLCJodHRwczovL2NpbmVoYXgub3JnIl0sInB1YmxpY19wb3N0X21lc3NhZ2UiOmZhbHNlLCJyciI6MCwic2lkIjoicHNfMjgxZDQxMzY3OTU5NDlhZmIzN2Q0ZWYzZmMzZDliZDgiLCJzayI6ImNpbmVtYXRpYyIsInQiOiJUb3JuYWRvIiwidG1kYiI6NjY0LCJ2IjoxLCJ2aXAiOjEsInhjIjowfQ.OH7fWVtkGg4wKPGR1mZmDHbI5iEVRFSRzRo6OX-In7A'

LINKS = re.compile(r'https://[^"\'\s<>\\]+\.m3u8[^"\'\s<>\\]*', re.IGNORECASE)

req = urllib.request.Request(embed, headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36', 'Referer': 'https://videoapp.mov/'})
resp = urllib.request.urlopen(req)
html = resp.read().decode('utf-8')

candidates = []
for m in LINKS.finditer(html):
    cand = m.group(0)
    print('Found m3u8 candidate:', cand[:80])
    # check supports:
    # URL host in vimeos.net or vimeos.zip
    import urllib.parse
    host = urllib.parse.urlparse(cand).netloc.lower()
    print('Host:', host)
    is_vimeos = host.endswith('vimeos.net') or host.endswith('vimeos.zip') or host == 'vimeos.net' or host == 'vimeos.zip'
    print('supports(cand)?', is_vimeos)
