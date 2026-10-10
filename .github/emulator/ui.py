"""Нажать на элемент экрана с текстом или проверить, что он есть: ui.py tap|check "текст"."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def dump():
    for _ in range(5):
        r = subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, text=True)
        if "dumped" in r.stdout:
            return subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
        time.sleep(1.5)
    raise SystemExit("uiautomator dump не получился")


mode, text = sys.argv[1], sys.argv[2]
def close_anr(root):
    """Системное окно «… isn't responding» на медленном эмуляторе — нажать «Wait»."""
    for n in root.iter("node"):
        if (n.get("text") or "") in ("Wait", "Подождать"):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
            print("Закрыто системное окно «не отвечает»")
            time.sleep(2)
            return True
    return False


for attempt in range(6):
    root = ET.fromstring(dump())
    if close_anr(root):
        continue
    for n in root.iter("node"):
        t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
        if text in t:
            if mode == "check":
                print(f"OK: на экране есть «{text}»")
                sys.exit(0)
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
            print(f"Нажато: «{text}»")
            sys.exit(0)
    time.sleep(2)
print(f"::error::На экране нет «{text}»")
sys.exit(1)
