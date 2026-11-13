#!/usr/bin/env python3
"""Small emulator driver; only operates the isolated Ondo package and QA files."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import sys
import time
import xml.etree.ElementTree as E

ADB = os.environ.get("ADB", shutil.which("adb") or str(Path(os.environ.get("ANDROID_SDK_ROOT", Path.home() / "android-sdk")) / "platform-tools/adb"))
DEVICE = os.environ.get("ONDO_QA_DEVICE", "emulator-5554")
if not DEVICE.startswith("emulator-"):
    raise RuntimeError("This QA driver is restricted to an Android emulator")
ROOT = Path(os.environ.get("ONDO_QA_DIR", str(Path(tempfile.gettempdir()) / "ondo-qa")))
ROOT.mkdir(parents=True, exist_ok=True)
PACKAGE = "com.nickshortnct.ondo"
def adb(*args, capture=True):
    result = subprocess.run([ADB,'-s',DEVICE,*args],capture_output=capture,check=True)
    return result.stdout if capture else b''

def hierarchy():
    for _ in range(3):
        adb('shell','uiautomator','dump','--compressed','/data/local/tmp/ondo-ui.xml')
        try:
            adb('pull','/data/local/tmp/ondo-ui.xml',str(ROOT/'ui.xml'))
            return E.parse(ROOT/'ui.xml')
        except Exception:
            time.sleep(.5)
    raise RuntimeError('No Ondo UI hierarchy available')

def require_ondo():
    activity = adb("shell", "dumpsys", "activity", "activities").decode()
    resumed = [line for line in activity.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line]
    if not any(PACKAGE in line for line in resumed):
        raise RuntimeError("Bring the isolated Ondo QA app to the foreground first")

cmd = sys.argv[1]
require_ondo()
if cmd == 'ui':
    for node in hierarchy().iter('node'):
        text = node.get('text') or node.get('content-desc')
        if text: print(text, node.get('bounds'))
elif cmd == 'tap':
    wanted = sys.argv[2]
    matches = [n for n in hierarchy().iter('node') if n.get('package') == PACKAGE and (n.get('text') == wanted or n.get('content-desc') == wanted)]
    node = min(matches, key=lambda n: int(re.findall(r'\d+',n.get('bounds'))[1])) if matches else None
    if node is None: raise RuntimeError('Missing visible UI target: '+wanted)
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    print('Tapped',wanted)
elif cmd == 'shot':
    path = ROOT/(sys.argv[2]+'.png')
    path.write_bytes(adb('exec-out','screencap','-p'))
    print(path)
elif cmd == 'input':
    adb('shell','input','text',sys.argv[2].replace(' ','%s'))
elif cmd == 'back':
    adb('shell','input','keyevent','4')
elif cmd == 'enter':
    adb('shell','input','keyevent','66')
elif cmd == 'xy':
    adb('shell','input','tap',sys.argv[2],sys.argv[3])
elif cmd == 'swipe':
    adb('shell','input','swipe',*sys.argv[2:])
else:
    raise RuntimeError('Unknown command '+cmd)
