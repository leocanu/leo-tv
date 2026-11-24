import subprocess
import xml.etree.ElementTree as ET

adb = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
subprocess.run([adb, "shell", "uiautomator", "dump", "/sdcard/dump.xml"])
subprocess.run([adb, "pull", "/sdcard/dump.xml", r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\dump.xml"])

tree = ET.parse(r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\dump.xml")
root = tree.getroot()
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    bounds = node.attrib.get('bounds', '')
    if any(k in text.lower() for k in ['tornado', 'reproducir', 'ficha', 'ajustes']):
        print(text, bounds)
