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


p=run("--button","menu.offsetXDp=-6","--button","menu.visualSizeDp=30")
assert "ui/layout.json" in p.stdout
run("--button","menu.unknown=4",good=False)
run("--button","missing.offsetXDp=4",good=False)
run("--button","menu.widthDp=30",good=False)

# One signed overlay .pupdate can change independent visual numbers simultaneously.
p=run("--set","overlay.indicatorVisuals.brightness.offsetXDp=46",
      "--set","overlay.indicatorVisuals.brightness.rotationAtMaxDeg=90",
      "--set","overlay.indicatorVisuals.elevation.guideRadiusUnits=68")
assert "modules/overlay/config.json" in p.stdout
run("--set","overlay.indicatorVisuals.brightness.nonexistent=42",good=False)
run("--set","overlay.indicatorVisuals.brightness.offsetXDp=\"bad\"",good=False)

assert "published_baseline()" in TOOL.read_text()
assert "pub.verify(sig,canonical(version,entries)" in TOOL.read_text()
print("PASS fast patch smoke, cumulative baseline, unknown-key rejection, dry-run without key")

# Single signed patch can modify numeric values ACROSS domains, not just lighting.
multiple=run("--set","ui.topReservedWidthDp=190",
             "--set","camera.homeDistance=6.0",
             "--set","camera.brightnessPinchDivisorDp=150",
             "--set","render.iblIntensity=31000",
             "--set","scene.graySkyRed=105",
             "--set","windows.diagnosticsPopup.reportTextSizeSp=12",
             "--set","overlay.indicatorVisuals.brightness.rotationAtMaxDeg=87")
for resource in ("ui/layout.json","modules/camera/config.json",
                 "modules/render/config.json","modules/scene/config.json",
                 "modules/windows/config.json","modules/overlay/config.json"):
    assert resource in multiple.stdout, resource

list_result=subprocess.run([sys.executable,str(TOOL),"--list-numbers"],
                           capture_output=True,text=True,cwd=ROOT,check=True)
for key in ("camera.homeDistance=","render.exposureSensitivity=",
            "scene.graySkyRed=","ui.topReservedWidthDp=",
            "windows.diagnosticsPopup.heightFraction=",
            "overlay.indicatorVisuals.brightness.offsetXDp="):
    assert key in list_result.stdout,key
assert "render.ambientOcclusionEnabled=" not in list_result.stdout

# Generic 0.2.56 numeric paths for the same common renderer on all modes.
p=run("--set","overlay.indicatorVisuals.brightness.glowEffect.primaryStroke.radiusUnits=16",
      "--set","overlay.indicatorVisuals.azimuth.glowEffect.secondaryStroke.opacity=0.21",
      "--set","overlay.indicatorVisuals.elevation.glowEffect.ambient.falloff=2.5")
assert "modules/overlay/config.json" in p.stdout
run("--set","overlay.indicatorVisuals.brightness.glowEffect.primaryStroke.unknown=2",good=False)
run("--set","overlay.indicatorVisuals.brightness.glowEffect.primaryStroke.opacity=\"bad\"",good=False)

# v0.2.57 nested uniform core and global lighting curve are signed numbers.
p=run("--set","overlay.indicatorVisuals.brightness.glowEffect.coreFill.opacity=0.66",
      "--set","overlay.indicatorVisuals.azimuth.glowEffect.coreFill.edgeSoftness=0.28",
      "--set","overlay.indicatorVisuals.elevation.glowEffect.coreFill.radiusUnits=11",
      "--set","render.globalBrightnessExponent=1.25")
assert "modules/overlay/config.json" in p.stdout
assert "modules/render/config.json" in p.stdout
run("--set","overlay.indicatorVisuals.azimuth.glowEffect.coreFill.nonexistent=1",good=False)
run("--set","overlay.indicatorVisuals.brightness.glowEffect.coreFill.opacity=\"bad\"",good=False)
