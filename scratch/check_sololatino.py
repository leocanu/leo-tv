import urllib.request
import re
from bs4 import BeautifulSoup

# Let's inspect SoloLatino HTML search
url = 'https://sololatino.net/series?q=abismo+de+pasion'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        html = r.read().decode('utf-8', errors='ignore')
        soup = BeautifulSoup(html, 'html.parser')
        cards = soup.select('.item, .poster, article, a[href*="/serie/"], a[href*="/pelicula/"]')
        print('Total elements found:', len(cards))
        for c in cards[:10]:
            print(c.get('href'), c.text.strip()[:60])
except Exception as e:
    print('Error:', e)
