import zipfile
import re
import os

def analyze_apk(apk_path, name):
    print("=" * 60)
    print(f"ANALIZANDO: {name} ({os.path.basename(apk_path)})")
    print("=" * 60)
    
    with zipfile.ZipFile(apk_path, 'r') as z:
        dex_files = [f for f in z.namelist() if f.endswith('.dex')]
        print(f"Archivos DEX encontrados: {dex_files}")
        
        all_dex_bytes = bytearray()
        for df in dex_files:
            all_dex_bytes.extend(z.read(df))
            
    print(f"Tamano total de DEX desglosado: {len(all_dex_bytes) / (1024*1024):.2f} MB")
    
    # 1. Buscar URLs y dominios clave
    urls = set(re.findall(rb'https?://[a-zA-Z0-9\.\-_]+(?::\d+)?(?:/[a-zA-Z0-9\.\-_/?=&%#~]*)?', all_dex_bytes))
    clean_urls = set()
    for u in urls:
        try:
            s = u.decode('utf-8', errors='ignore')
            if not any(ign in s for ign in ['schema.org', 'schemas.android.com', 'w3.org', 'google.com/recaptcha', 'github.com/google']):
                if len(s) > 10 and '.' in s:
                    clean_urls.add(s)
        except:
            pass
    
    print("\n--- URLs y Endpoints Clave ---")
    for u in sorted(clean_urls)[:35]:
        print("  *", u)
        
    # 2. Buscar clases y patrones relacionados con Reproductor, Fallback, Retry, Servidores
    patterns = [
        (rb'PlayerError|onPlayerError|PlaybackException', "Manejo de Errores de Reproduccion"),
        (rb'fallback|fallbackUrl|backupUrl|alternativeServer|nextServer', "Variables de Fallback / Servidor Alternativo"),
        (rb'retry|retryCount|maxRetry|autoSwitch|switchSource', "Logica de Reintento y Cambio de Fuente"),
        (rb'buffering|STATE_BUFFERING|stall|timeout', "Deteccion de Buffering / Timeout / Inestabilidad"),
        (rb'ExoPlayer|MediaPlayer|IjkMediaPlayer', "Motor de Reproduccion"),
    ]
    
    print("\n--- Patrones de Codigo Detectados ---")
    for pat, label in patterns:
        matches = len(re.findall(pat, all_dex_bytes, re.IGNORECASE))
        print(f"  * {label}: {matches} ocurrencias")
        
    return all_dex_bytes

analyze_apk(r"C:\Users\Pc\Downloads\PlayTV_Premium_20.1.apk", "Play TV Premium")
analyze_apk(r"C:\Users\Pc\Downloads\tvl1.apk", "tvl1 (CloneX)")
