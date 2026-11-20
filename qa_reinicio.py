"""Prueba funcional local por ADB. No usa modelos ni APIs de IA."""
import subprocess, pathlib, time, json, re, sys, xml.etree.ElementTree as ET
BASE = pathlib.Path(__file__).resolve().parent
OUT = BASE / ("qa-" + time.strftime("%Y%m%d-%H%M%S"))
OUT.mkdir()
ADB = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
results = []
def adb(*args, timeout=30):
    p = subprocess.run([ADB, "-s", "emulator-5554", *args], capture_output=True, timeout=timeout)
    if p.returncode: raise RuntimeError(p.stderr.decode("utf-8", "replace"))
    return p.stdout
def key(code):
    adb("shell", "input", "keyevent", str(code))
    time.sleep(.4)
def ui(label):
    remote = "/sdcard/appvideo-qa-" + label + "-" + str(time.time_ns()) + ".xml"
    response = adb("shell", "uiautomator", "dump", remote)
    if b"dumped to" not in response:
        raise RuntimeError("UIAutomator no produjo captura nueva: " + response.decode("utf-8","replace"))
    raw = adb("shell", "cat", remote)
    adb("shell", "rm", remote)
    (OUT / (label + ".xml")).write_bytes(raw)
    return ET.fromstring(raw)
def texts(tree):
    return [n.attrib.get("text","") for n in tree.iter("node") if n.attrib.get("text")]
def check(name, ok, detail=""):
    results.append({"test": name, "passed": bool(ok), "detail": detail})
    print(json.dumps(results[-1], ensure_ascii=True), flush=True)
def tap_text(tree, fragment):
    nodes = [n for n in tree.iter("node") if fragment in n.attrib.get("text","") and n.attrib.get("clickable") == "true"]
    if not nodes: raise RuntimeError("No se encuentra boton " + fragment)
    nums = list(map(int, re.findall(r"\d+", nodes[0].attrib["bounds"])))
    adb("shell","input","tap",str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2))
    time.sleep(1)
def screenshot(label):
    adb("shell","screencap","-p","/sdcard/appvideo-qa.png")
    adb("pull","/sdcard/appvideo-qa.png",str(OUT / (label+".png")))
def media_state():
    dump = adb("shell","dumpsys","media_session").decode("utf-8","replace")
    if "package=ar.appvideo.player" not in dump:
        raise RuntimeError("Android no registra la sesion de nuestra app")
    block = dump.split("package=ar.appvideo.player",1)[1]
    match = re.search(r"state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)",block)
    if not match: raise RuntimeError("No se pudo leer el estado multimedia")
    return match.group(1), int(match.group(2))
def home():
    adb("shell","am","force-stop","ar.appvideo.player")
    adb("shell","am","start","-n","ar.appvideo.player/.MainActivity")
    time.sleep(1)
try:
    home()
    tree=ui("home")
    check("inicio", any("Tus canales" in t for t in texts(tree)))
    if "--solo-controles" not in sys.argv:
        screenshot("home")
        key(23)
        tree=ui("demo-channel")
        check("OK abre canal Demo", any("Reproducir muestra" in t for t in texts(tree)))
        key(20); key(23); time.sleep(2)
        tree=ui("demo-playing")
        check("Demo preparada", any("Reproduciendo" in t for t in texts(tree)), " | ".join(texts(tree)))
        screenshot("demo-playing")
        key(4)
        tree=ui("demo-back")
        check("Atras vuelve al canal", any("Reproducir muestra" in t for t in texts(tree)))
        key(4)
        tree=ui("home-back")
        check("Atras vuelve al inicio", any("Tus canales" in t for t in texts(tree)))
    tap_text(tree,"BLENDER OPEN MOVIES")
    tree=ui("blender-channel")
    check("catalogo Blender", any("Elephants Dream" in t for t in texts(tree)))
    tap_text(tree,"Elephants Dream")
    time.sleep(5)
    tree=ui("blender-playing")
    check("Blender preparado", any("Video preparado" in t for t in texts(tree)), " | ".join(texts(tree)))
    screenshot("blender-playing")
    key(85)
    state, pos1 = media_state()
    check("Pausa con control remoto", state == "PAUSED", str((state,pos1)))
    screenshot("blender-paused")
    key(85)
    state, _ = media_state()
    check("Reanudar con control remoto", state == "PLAYING", state)
    time.sleep(3)
    key(127)
    state, pos2 = media_state()
    check("Reloj de video avanza", pos2 > pos1 + 1000, str((pos1,pos2)))
    key(90)
    state, pos3 = media_state()
    check("Avance con control remoto", pos3 >= pos2 + 10000, str((pos2,pos3)))
    key(89)
    state, pos4 = media_state()
    check("Retroceso con control remoto", pos4 < pos3, str((pos3,pos4)))
    key(126)
    key(3)
    time.sleep(1)
    state, _ = media_state()
    check("Pausa al salir de la app", state == "PAUSED", state)
    adb("shell","am","start","--activity-reorder-to-front","-n","ar.appvideo.player/.MainActivity")
    time.sleep(1)
    state, _ = media_state()
    check("Vuelve pausado", state == "PAUSED", state)
    screenshot("blender-return")
    key(4)
    tree=ui("after-controls-back")
    if not any("Elephants Dream (2006)" in t for t in texts(tree)):
        key(4)
    key(4)
    tree=ui("archive-home")
    if "--solo-controles" not in sys.argv:
        tap_text(tree,"INTERNET ARCHIVE")
        tree=ui("archive-channel")
        tap_text(tree,"Big Buck Bunny")
        time.sleep(5)
        tree=ui("archive-playing")
        for attempt in range(3):
            if any("Video preparado" in t or "Error Media3" in t for t in texts(tree)): break
            time.sleep(3)
            tree=ui("archive-wait-"+str(attempt))
        check("Internet Archive preparado", any("Video preparado" in t for t in texts(tree)), " | ".join(texts(tree)))
        screenshot("archive-playing")
        key(4); key(4)
    tree=ui("final-home")
    check("Inicio final", any("Tus canales" in t for t in texts(tree)))
except Exception as e:
    check("ejecucion",False,str(e))
finally:
    (OUT/"results.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
    print("QA_DIR="+str(OUT),flush=True)

sys.exit(1 if any(not r["passed"] for r in results) else 0)
