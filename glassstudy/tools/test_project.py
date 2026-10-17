#!/usr/bin/env python3
"""Offline static regression suite. Not a substitute for Android runtime tests."""
import re,struct,sys,json,hashlib,base64
from pathlib import Path
from xml.etree import ElementTree
root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/com/glassstudy/filament/MainActivity.kt').read_text()
updater=(root/'app/src/main/java/com/glassstudy/filament/PatchManager.kt').read_text()
build=(root/'app/build.gradle').read_text()
manifest=ElementTree.parse(root/'app/src/main/AndroidManifest.xml')
assert "applicationId 'com.glassstudy.filament.stable'" in build
props=(root/'gradle.properties').read_text()
assert 'GLASSSTUDY_VERSION=0.2.10' in props and 'GLASSSTUDY_VERSION_CODE=20010' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity and 'BuildConfig.VERSION_CODE' in activity
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'else distance = (distance * (delta / oldPinch))' in activity
assert 'Signature.getInstance("SHA256withRSA")' in updater
assert 'signature.verify(' in updater
assert 'reconcileAfterCrash()' in updater and 'confirmFrameRendered()' in updater
assert 'if (!prefs.edit().putInt("previous",previous).putInt("active",candidate.version)' in updater
assert 'glasses' not in updater.lower() # no unintended paths
assert 'REQUEST_INSTALL_PACKAGES' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'GLASSSTUDY_KEYSTORE' in build and 'GLASSSTUDY_STORE_PASSWORD' in build
assert not any(p.suffix.lower() in ('.p12','.jks','.pem','.der') for p in root.rglob('*') if p.is_file())
model_dir=root/'app/src/main/assets/models'
models=list(model_dir.glob('*.glb'))
assert len(models)==66,len(models)
for f in models:
    b=f.read_bytes()
    assert b[:4]==b'glTF', f
    assert struct.unpack_from('<I',b,8)[0]==len(b),f
assert (root/'app/src/main/assets/envs/studio_ibl.ktx').stat().st_size>2_000_000
# Compare canonical signing algorithm source against Python test helper.
sys.path.insert(0,str(root/'tools'))
import publish_patch
assert publish_patch.canonical(20010,[{'path':'models/a.glb','sha256':'0123'}]) == b'glassstudy-filament-assets-v2\n20010\nmodels/a.glb\t0123\n'
print('PASS: project identifiers, version, background updater, rollback, signature, gesture, key hygiene, 66 GLBs, IBL')
print('NOTE: Android SDK/Gradle compile and device runtime must be tested separately.')