import os
import shutil
import hashlib
import json
import subprocess

apk_src = r"C:\Users\Pc\Desktop\LeoTV-v0.48.0.apk"
release_dir = r"C:\Users\Pc\Documents\Chain-Reaction-Music-Updates-release\leotv"

os.makedirs(release_dir, exist_ok=True)
apk_dest = os.path.join(release_dir, "LeoTV-v0.48.0.apk")
shutil.copyfile(apk_src, apk_dest)

size = os.path.getsize(apk_src)
with open(apk_src, "rb") as f:
    sha = hashlib.sha256(f.read()).hexdigest().lower()

changelog = (
    "• Sección +18 oculta por completo de la barra lateral (solo visible al desbloquear Control Parental con PIN en Ajustes)\n"
    "• Máxima calidad forzada por defecto (sin pixelación adaptativa a 480p en Tornado y estrenos)\n"
    "• Selector de servidores/fuentes (CineHax 1080p, SoloLatino, Cuevana, CineCalidad, Pelispedia)\n"
    "• Soporte para Reproductor Externo (Abrir con VLC o JustPlayer)\n"
    "• Ficha técnica enriquecida con calidad, audio y servidor\n"
    "• OSD inferior reubicado para control remoto TV"
)

data = {
    "packageName": "ar.appvideo.player",
    "versionCode": 48,
    "versionName": "0.48.0",
    "minSdk": 23,
    "bytes": size,
    "sha256": sha,
    "url": "https://raw.githubusercontent.com/leocanu/Chain-Reaction-Music-Updates/main/leotv/LeoTV-v0.48.0.apk",
    "apkUrl": "https://raw.githubusercontent.com/leocanu/Chain-Reaction-Music-Updates/main/leotv/LeoTV-v0.48.0.apk",
    "changelog": changelog,
    "notes": changelog,
    "mandatory": False
}

for name in ["latest.json", "version.json"]:
    with open(os.path.join(release_dir, name), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)

repo_dir = r"C:\Users\Pc\Documents\Chain-Reaction-Music-Updates-release"
subprocess.run(["git", "add", "leotv/"], cwd=repo_dir, check=True)
res = subprocess.run(["git", "commit", "-m", "release: LeoTV v0.48.0 - Ocultar +18 con control parental, maxima calidad y selector fuentes"], cwd=repo_dir)
subprocess.run(["git", "push", "origin", "main"], cwd=repo_dir, check=True)
print("Git push complete!")

adb = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
p = subprocess.run([adb, "install", "-r", apk_src], capture_output=True, text=True)
print("ADB install:", p.stdout, p.stderr)
