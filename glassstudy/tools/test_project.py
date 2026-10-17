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
assert 'GLASSSTUDY_VERSION=0.2.18' in props and 'GLASSSTUDY_VERSION_CODE=20018' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity and 'BuildConfig.VERSION_CODE' in activity
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'diagnosticLabels' in activity and 'ui.diagnostics' in activity
assert 'updater.lastConfirmedVersion()' in activity and 'updater.serverPatchVersion' in activity
assert 'tr("pathTracing")' in activity and 'tr(if(hasIbl)' in activity
assert 'private val DIAGNOSTIC_DEFAULTS' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert 'putInt("lastConfirmed",pending)' in updater
assert 'Патч $pending установлен и подтверждён отрисовкой' in updater
assert 'fun reject(candidate: Candidate)' in updater
assert 'updateStatus="Патч ${candidate.version} установлен, ожидает подтверждения отрисовкой"' in updater
assert 'handleIncomingPatchIntent(intent)' in activity
assert 'override fun onNewIntent' in activity
assert 'updater.installFromUri' in activity
assert 'onCandidate(candidate)' in updater and 'fun installFromUri(' in updater
assert 'android.intent.action.VIEW' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android.intent.action.SEND' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'REQUEST_INSTALL_PACKAGES' not in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'root.setOnApplyWindowInsetsListener' in activity
assert 'applyUiLayout(candidate)' in activity
assert 'ui/layout.json' in updater and 'UiLayout.parse(raw)' in updater
assert 'source.copyTo(target,overwrite=false)' in updater
layout=json.loads((root/'app/src/main/assets/ui/layout.json').read_text())
assert layout['format']=='glassstudy-ui-v1' and layout['schema']==1
assert len(layout['diagnostics'])>=50
assert layout['diagnosticSections']==['updates','performance','memory','scene','renderer','camera','device']
instrumentation=(root/'app/src/main/java/com/glassstudy/filament/FrameProfiler.kt').read_text()
assert 'Process.getElapsedCpuTime()' in instrumentation
assert 'Debug.getMemoryInfo(memory)' in instrumentation
assert 'GlbMetrics.inspect' in activity
assert 'ByteBuffer.wrap(raw)' in instrumentation
assert 'profiler.frame(frameTimeNanos,submissionMs)' in activity
assert 'val samePatch=server==active.toString() && confirmed==active' in activity
assert 'appendLine("${tr("compactPatches")}: $patchSummary")' in activity
assert 'FPS — ритм кадров' in layout['diagnostics']['measurementNote']
assert 'FPS: ${num(perf.fps)}' in activity
assert 'appendLine("GlassStudy ${BuildConfig.VERSION_NAME}' in activity
assert 'appendLine("${tr("appVersion")}' not in activity
assert layout['diagnostics']['installedPatch']=='Установленный патч ресурсов'
assert layout['diagnostics']['pathTracingValue']=='В этой бета-версии не реализована'
assert set(layout['topOrder'])=={'status','selectors','actions','grid'}
assert set(layout['bottomOrder'])=={'export','diagnostics'}
assert 'private var distance = 5.5' in activity
assert 'private var pitch = .34' in activity
assert 'cameraButton.setOnLongClickListener' in activity
assert 'resetCameraToHome()' in activity
assert 'viewer.camera.lookAt(x, y, z, 0.0, 1.0, 0.0' in activity
assert 'else distance = (distance * (oldPinch / delta).toDouble().pow(zoomPower))' in activity
assert 'pitch = (pitch + dy * .005).coerceIn(-.05,1.23)' in activity
assert 'yaw += dx * .008' in activity
assert 'lightAz += dx * .009' in activity
assert 'lightElevation = (lightElevation + dy * .006).coerceIn(.06,1.5)' in activity
assert 'e.x - oldX' in activity and 'resources.displayMetrics.density' in activity
assert 'Signature.getInstance("SHA256withRSA")' in updater
assert 'signature.verify(' in updater
assert 'reconcileAfterCrash()' in updater and 'confirmFrameRendered()' in updater
assert 'if (!prefs.edit().putInt("previous",current).putInt("active",candidate.version)' in updater
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