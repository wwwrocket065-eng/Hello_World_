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
assert 'GLASSSTUDY_VERSION=0.2.13' in props and 'GLASSSTUDY_VERSION_CODE=20013' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity and 'BuildConfig.VERSION_CODE' in activity
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'root.setOnApplyWindowInsetsListener' in activity
assert 'applyUiLayout(candidate)' in activity
assert 'ui/layout.json' in updater and 'UiLayout.parse(raw)' in updater
layout=json.loads((root/'app/src/main/assets/ui/layout.json').read_text())
assert layout['format']=='glassstudy-ui-v1' and layout['schema']==1
assert set(layout['topOrder'])=={'status','selectors','actions','grid'}
assert set(layout['bottomOrder'])=={'export','diagnostics'}
assert 'private var distance = 7.4' in activity
assert 'else distance = (distance * (delta / oldPinch).toDouble().pow(zoomPower))' in activity
assert 'Signature.getInstance("SHA256withRSA")' in updater
assert 'signature.verify(' in updater
assert 'reconcileAfterCrash()' in updater and 'confirmFrameRendered()' in updater
assert 'if (!prefs.edit().putInt("previous",previous).putInt("active",candidate.version)' in updater
assert 'glasses' not in updater.lower() # no unintended paths
assert 'REQUEST_INSTALL_PACKAGES' not in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'requestApkInstall' not in activity and 'downloadApk(' not in updater
assert 'FileProvider' not in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'GLASSSTUDY_KEYSTORE' in build and 'GLASSSTUDY_STORE_PASSWORD' in build
assert not any(p.suffix.lower() in ('.p12','.jks','.pem','.der') for p in root.rglob('*') if p.is_file())
model_dir=root/'app/src/main/assets/models'
models=list(model_dir.glob('*.glb'))
assert len(models)==66,len(models)
for f in models:
    b=f.read_bytes()
    assert b[:4]==b'glTF', f
    assert struct.unpack_from('<I',b,8)[0]==len(b),f
    json_len=struct.unpack_from('<I',b,12)[0]
    mesh=json.loads(b[20:20+json_len])
    bin_len=struct.unpack_from('<I',b,20+json_len)[0]
    assert mesh['buffers'][0]['byteLength']==bin_len,f
    assert all(0<=prim.get('material',0)<len(mesh['materials'])
               for group in mesh['meshes'] for prim in group['primitives']),f
assert (root/'app/src/main/assets/envs/studio_ibl.ktx').stat().st_size>2_000_000
# Compare canonical signing algorithm source against Python test helper.
sys.path.insert(0,str(root/'tools'))
import publish_patch
assert publish_patch.canonical(20010,[{'path':'models/a.glb','sha256':'0123'}]) == b'glassstudy-filament-assets-v2\n20010\nmodels/a.glb\t0123\n'
print('PASS: project identifiers, version, background updater, rollback, signature, gesture, key hygiene, 66 GLBs, IBL')
print('NOTE: Android SDK/Gradle compile and device runtime must be tested separately.')