import urllib.request
import json
import base64
import re

url = 'https://api.wqxag.com/diaries.json'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
    'Referer': 'https://futbol-libre.app/'
}

resp = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10)
data = json.loads(resp.read().decode('utf-8'))

for item in data['data'][:5]:
    attr = item['attributes']
    desc = attr.get('diary_description')
    hour = attr.get('diary_hour')
    embeds = attr.get('embeds', {}).get('data', [])
    print(f"\nMatch: {desc} ({hour})")
    for emb in embeds:
        e_name = emb['attributes'].get('embed_name')
        e_iframe = emb['attributes'].get('embed_iframe', '')
        print(f"  Channel: {e_name} -> iframe: {e_iframe}")
        # Decode r parameter
        m = re.search(r'r=([a-zA-Z0-9+=/]+)', e_iframe)
        if m:
            try:
                dec = base64.b64decode(m.group(1)).decode('utf-8').strip()
                print(f"    Decoded: {dec}")
            except Exception as e:
                print(f"    Decode error: {e}")
