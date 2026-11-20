import zipfile

with zipfile.ZipFile('kino_1069032.apk', 'r') as z:
    names = z.namelist()
    print("Files in kino_1069032.apk:", len(names))
    if 'assets/plugin-catalog-seed.json' in names:
        seed = z.read('assets/plugin-catalog-seed.json').decode('utf-8', errors='ignore')
        print("plugin-catalog-seed.json:\n", seed[:1000])

    # Check for extra assets or plugins bundled
    plugins = [n for n in names if 'plugin' in n.lower() or 'xuper' in n.lower()]
    print("Plugin-related files:", plugins)
