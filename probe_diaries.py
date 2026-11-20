import urllib.request
import json

url = 'https://api.wqxag.com/diaries.json'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://futbol-libre.app/'
}

print("Fetching diaries.json...")
try:
    req = urllib.request.Request(url, headers=headers)
    resp = urllib.request.urlopen(req, timeout=10)
    data = json.loads(resp.read().decode('utf-8'))
    print("Type of data:", type(data))
    if isinstance(data, list):
        print(f"Total entries: {len(data)}")
        for item in data[:5]:
            print(json.dumps(item, indent=2, ensure_ascii=False))
    elif isinstance(data, dict):
        print("Keys:", data.keys())
        for k, v in list(data.items())[:3]:
            print(f"Key {k}: {v}")
except Exception as e:
    print("Error fetching diaries.json:", e)
