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
assert 'GLASSSTUDY_VERSION=0.2.26' in props and 'GLASSSTUDY_VERSION_CODE=20026' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity and 'BuildConfig.VERSION_CODE' in activity
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'applyButton' not in activity
assert 'private var diagnosticPopup:PopupWindow?' in activity
assert 'popup.showAtLocation(mainRoot' in activity
assert 'if(action=="diagnostics")' in activity
assert 'diagnosticPopup?.dismiss()' in activity
assert 'setTouchModal(false)' in activity
assert 'text="Диагностика"' in activity
assert 'LabIconView(this,"copy")' in activity
assert 'setTextIsSelectable(true)' in activity
assert 'AlertDialog.Builder(this).setTitle(tr("title"))' not in activity
assert '"copy" -> {' in (root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'Применить' not in activity or 'no "Применить"' in activity
assert 'chooseObject.onItemSelectedListener=onSelection' in activity
assert 'chooseLod.onItemSelectedListener=onSelection' in activity
assert 'loadSelected()' in activity
assert 'headerRow.addView(topButtonHost' in activity
assert 'bottomPanel.addView(bottomButtonHost' in activity
assert 'renderUiButtons(ui)' in activity
assert 'topButtonHost.removeAllViews()' in activity
assert 'bottomButtonHost.removeAllViews()' in activity
assert 'createUiButton(spec,ui)' in activity
assert 'UiLayout.parse(updater.readUiConfig(candidate))' in activity
assert 'LabIconView(this,spec.icon)' in activity
assert 'LabLogoView(this)' in activity
assert 'diagnosticButton' not in activity and 'quickObjects' not in activity
assert 'exportScene()' not in activity and 'applyButton' not in activity
icons=(root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'class LabIconView' in icons and 'class LabLogoView' in icons
assert 'canvas.drawRect(-4f,-4f,4f,4f,ink)' in icons
assert 'if(showUnderline) canvas.drawLine' in icons
assert 'if(chipCenterFilled)Paint.Style.FILL' in icons
assert 'glyphScale=ui.topIconScale' in activity
assert 'setSystemBarMode(ui)' in activity
assert 'View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY' in activity
assert 'label.visibility=if(ui.showCameraHint)View.VISIBLE else View.GONE' in activity
assert 'logoMark.showUnderline=ui.showLogoUnderline' in activity
assert 'logoParams.width=d(ui.logoSizeDp.toFloat())' in activity
assert 'logoSizeDp' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()

assert 'exportButton' not in activity
assert 'exportScene()' not in activity
assert 'Экспорт сцены' not in activity
assert 'ACTION_CREATE_DOCUMENT' not in activity
assert 'Экспорт сцены' not in activity[activity.index('private fun openMenu'):activity.index('override fun onCreate')]
assert 'GlassStudy 3D LAB' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert '@drawable/glassstudy_launcher' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert (root/'app/src/main/res/drawable/glassstudy_launcher.webp').stat().st_size>1200
custom=(root/'app/src/main/java/com/google/android/filament/utils/GlassStudyModelViewer.kt').read_text()
assert 'class GlassStudyModelViewer' in custom
assert 'renderedFrames++' in custom
assert 'cameraManipulator.getLookAt(eyePos, target, upward)' not in custom
assert 'viewer = GlassStudyModelViewer(surface, manipulator = null)' in activity
assert 'Реально нарисованных кадров Filament:' in activity
assert 'diagnosticLabels' in activity and 'ui.diagnostics' in activity
assert 'updater.lastConfirmedVersion()' in activity and 'updater.serverPatchVersion' in activity
assert 'tr("pathTracing")' in activity and 'tr(if(hasIbl)' in activity
uic=(root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert 'private val DIAGNOSTIC_DEFAULTS' in uic
assert 'data class UiButtonSpec' in uic and 'uiButtons' in uic
assert 'Unknown button action, location or icon' in uic
assert 'At least one menu or diagnostics control must remain available' in uic
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
assert layout['logoSizeDp']==31
assert layout['logoOffsetYDp']==5
assert layout['topOverlayOpacity']==0
assert layout['topButtonOutlineColor']=="#6DB9CB"
assert 'logoParams.topMargin=d(ui.logoOffsetYDp.toFloat())' in activity
assert 'if(ui.topOverlayOpacity==0) null' in activity
assert 'currentTopOutlineColor=ui.topButtonOutlineColor' in activity
assert layout['logoColor']=="#6DE0DE"
assert layout['buttonTextColor']=="#A3D0D3"
assert layout['format']=='glassstudy-ui-v1' and layout['schema']==1
assert len(layout['uiButtons'])==6
assert [x['action'] for x in layout['uiButtons'] if x['slot']=='top']==['diagnostics','toggleLight','resetCamera','menu']
assert [x['action'] for x in layout['uiButtons'] if x['slot']=='bottom']==['toggleLight','toggleGrid']
assert not any(x['action']=='export' for x in layout['uiButtons'])
assert layout['panelOpacity'] < 1.0
assert layout['buttonTextColor']=="#A3D0D3"
assert layout['showCameraHint'] is False
assert layout['showLogoUnderline'] is False
assert layout['fillChipCenter'] is True
assert layout['immersiveMode'] is True
assert layout['topButtonSizeDp']==35
assert all(b['textColor']=="#A3D0D3" for b in layout['uiButtons'])
assert layout['invertHorizontal'] is True
assert 'invertHorizontal=ui.invertHorizontal' in activity
assert 'yaw += dx * (if(invertHorizontal) -cameraModule.yawSensitivity else cameraModule.yawSensitivity)' in activity
assert 'val invertHorizontal=if(j.has("invertHorizontal"))j.getBoolean("invertHorizontal") else true' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
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
assert 'if(spec.action=="toggleLight")' in activity
assert 'target.setOnLongClickListener' in activity
assert 'resetCameraToHome()' in activity
assert 'viewer.camera.lookAt(x, y, z, 0.0, 1.0, 0.0' in activity
assert 'else distance = (distance * (oldPinch / delta).toDouble().pow(zoomPower)).coerceIn(cameraModule.minDistance, cameraModule.maxDistance)' in activity
assert 'pitch = (pitch + dy * cameraModule.pitchSensitivity)' in activity
assert 'yaw += dx * (if(invertHorizontal) -cameraModule.yawSensitivity else cameraModule.yawSensitivity)' in activity
assert 'lightAz += dx * cameraModule.lightYawSensitivity' in activity
assert 'lightElevation = (lightElevation + dy * cameraModule.lightPitchSensitivity)' in activity
assert 'e.x - oldX' in activity and 'resources.displayMetrics.density' in activity
assert 'Signature.getInstance("SHA256withRSA")' in updater
assert 'signature.verify(' in updater
assert 'reconcileAfterCrash()' in updater and 'confirmFrameRendered()' in updater
assert 'putInt("blockedAfterCrash",pending)' in updater
assert 'feed.patchVersion != blockedVersion' in updater
assert 'remove("blockedAfterCrash")' in updater
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
# Modular source integration tests
src=root/'app/src/main/java/com/glassstudy/filament'
for name in ('PatchResourceRegistry','CameraModuleConfig','RenderModuleConfig',
             'DiagnosticsModuleConfig','MaterialModuleConfig','MaterialOverrides'):
    assert (src/(name+'.kt')).is_file(), name
registry=(src/'PatchResourceRegistry.kt').read_text()
assert 'modules/' in registry and 'PatchResourceRegistry.validate(path, raw)' in updater
assert 'signature.verify(' in updater  # Modules must pass the old signature check.
for module in ('camera','render','diagnostics','materials'):
    config=json.loads((root/'app/src/main/assets/modules'/module/'config.json').read_text())
    assert config['id']==module and config['schema']==1
    assert 'readModuleConfig("'+module+'", candidate)' in activity
assert 'MaterialOverrides.parse(' in activity
assert 'CameraModuleConfig.parse(config)' in updater
assert 'RenderModuleConfig.parse(config)' in updater
assert 'DiagnosticsModuleConfig.parse(config)' in updater
assert 'MaterialOverrides.parse(config)' in updater
assert 'CameraModuleConfig.parse(stagedModule("camera"))' in updater
assert 'RenderModuleConfig.parse(stagedModule("render"))' in updater
assert 'DiagnosticsModuleConfig.parse(stagedModule("diagnostics"))' in updater
assert 'Missing material variant: $path' in updater
assert 'material.hasParameter(name)' in (src/'MaterialOverrides.kt').read_text()
print('PASS: modular resource registry, signed patch integration, module consumers and default JSON')
print('PASS: project identifiers, version, background updater, rollback, signature, gesture, key hygiene, 66 GLBs, IBL')
print('NOTE: Android SDK/Gradle compile and device runtime must be tested separately.')