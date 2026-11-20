import zipfile
import re

with zipfile.ZipFile('kino.apk', 'r') as z:
    names = z.namelist()
    print("Files count in APK:", len(names))
    
    # Check AndroidManifest.xml strings or resources
    for name in names[:30]:
        print(" -", name)
    
    # Check for interesting asset files or dex strings
    interesting = [n for n in names if any(x in n.lower() for x in ['json', 'js', 'html', 'properties', 'xml', 'so'])]
    print("Interesting files:", len(interesting))
    for n in interesting[:20]:
        print("  *", n)
