import zipfile

with zipfile.ZipFile('kino.apk', 'r') as z:
    if 'assets/plugin-catalog-seed.json' in z.namelist():
        data = z.read('assets/plugin-catalog-seed.json').decode('utf-8', errors='ignore')
        print("plugin-catalog-seed.json length:", len(data))
        print("Preview:\n", data[:1500])

    # Check app-metadata.properties
    if 'META-INF/com/android/build/gradle/app-metadata.properties' in z.namelist():
        print("\napp-metadata:")
        print(z.read('META-INF/com/android/build/gradle/app-metadata.properties').decode('utf-8', errors='ignore'))
