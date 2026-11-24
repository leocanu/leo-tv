import urllib.request, re

url = 'https://p2.vimeos.zip/hls2/03/00006/tcz5ywtrvuaw_h/master.m3u8?t=PJ8oKoSH_bUHCRyI168M8rRa4InALsR5JO_VUvgPoik&s=1795477023&e=43200&v=449977677&srv=s12&i=0.0&sp=0&fr=tcz5ywtrvuaw&r=e'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36', 'Referer': 'https://vimeos.net/'})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        m3u8_text = resp.read().decode('utf-8')
        print('Master m3u8:\n', m3u8_text)
except Exception as e:
    print('Error loading m3u8:', e)
