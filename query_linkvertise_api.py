import urllib.request
import json

api_url = 'https://publisher.linkvertise.com/api/v1/redirect/link/static/8724813/rQzossF9Fe6V'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'application/json'
}

req = urllib.request.Request(api_url, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=10)
    data = json.loads(resp.read().decode('utf-8'))
    print("API Response keys:", data.keys())
    print("Link data:\n", json.dumps(data, indent=2))
except Exception as e:
    print("API Error:", e)
