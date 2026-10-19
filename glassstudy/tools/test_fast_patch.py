#!/usr/bin/env python3
"""Offline regression smoke tests for universal fast asset patch tool."""
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TOOL=ROOT/"tools/fast_patch.py"
def run(*args,good=True):
    p=subprocess.run([sys.executable,str(TOOL),"--version","20030",*args,"--dry-run"],capture_output=True,text=True,cwd=ROOT)
    if good and p.returncode!=0:raise AssertionError(p.stdout+"\n"+p.stderr)
    if not good and p.returncode==0:raise AssertionError("Unsafe patch accepted: "+repr(args))
    return p

# All module families can be patched WITHOUT signing or Gradle.
for alias in ("ui","camera","render","diagnostics","materials","scene","windows","overlay"):
    p=run("--file",( ("ui/layout.json" if alias=="ui" else "modules/"+alias+"/config.json")+
                    "="+str(ROOT/"app/src/main/assets"/("ui/layout.json" if alias=="ui" else "modules/"+alias+"/config.json"))),good=False)
    assert "No changed resources" in p.stderr

p=run("--set","ui.topButtonSizeDp=32","--set","ui.gearInnerRoundness=1.0")
assert "ui/layout.json" in p.stdout
for alias in ("camera","render","diagnostics","materials","scene","windows","overlay"):
    assert alias in TOOL.read_text()
for invalid in (
    ("--set","ui.notASetting=true"),
    ("--set","camera.unknownKey=5"),
    ("--file","../unsafe.json=../unsafe.json"),
):
    run(*invalid,good=False)

assert "published_baseline()" in TOOL.read_text()
assert "pub.verify(sig,canonical(version,entries)" in TOOL.read_text()
print("PASS fast patch smoke, cumulative baseline, unknown-key rejection, dry-run without key")
