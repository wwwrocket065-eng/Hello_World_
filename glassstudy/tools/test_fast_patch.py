#!/usr/bin/env python3
"""Offline regression smoke tests for universal fast asset patch tool."""
import subprocess
import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TOOL=ROOT/"tools/fast_patch.py"
PUBLISHED=json.loads((ROOT/"updates/v2/latest.json").read_text())["patchVersion"]
NEXT_VERSION=PUBLISHED+1
def run(*args,good=True):
    p=subprocess.run([sys.executable,str(TOOL),"--version",str(NEXT_VERSION),*args,"--dry-run"],capture_output=True,text=True,cwd=ROOT)
    if good and p.returncode!=0:raise AssertionError(p.stdout+"\n"+p.stderr)
    if not good and p.returncode==0:raise AssertionError("Unsafe patch accepted: "+repr(args))
    return p

# Every module family has one canonical signed resource path; no fake values used.
for alias in ("ui","camera","render","diagnostics","materials","scene","windows","overlay"):
    resource="ui/layout.json" if alias=="ui" else "modules/"+alias+"/config.json"
    assert (ROOT/"app/src/main/assets"/resource).is_file()

p=run("--set","ui.topButtonSizeDp=30","--set","ui.gearInnerRoundness=0.9")
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
