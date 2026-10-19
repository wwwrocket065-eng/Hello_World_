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
assert 'GLASSSTUDY_VERSION=0.2.33' in props and 'GLASSSTUDY_VERSION_CODE=20033' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity and 'BuildConfig.VERSION_CODE' in activity
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'applyButton' not in activity
assert 'private var diagnosticPopup:PopupWindow?' in activity
assert 'popup.showAtLocation(mainRoot' in activity
assert 'if (action == "diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'diagnosticPopup?.dismiss()' in activity
assert 'setTouchModal(false)' in (root/'app/src/main/java/com/glassstudy/filament/GlassDialogFactory.kt').read_text()
assert 'theme.titles.getValue("diagnostics")' in activity
assert 'LabIconView(this,"copy")' in activity
assert '"diagnostics" -> diagnosticPopup?.isShowing==true' in activity
assert 'updateButtonHighlights()' in activity[activity.index('GlassDialogFactory.popup(this,outer,availableWidth)'):activity.index('copy.setOnClickListener')]
assert 'skin(this,false,currentUiColor,currentTopOutlineColor)' in activity
assert 'iconColor=currentTopIconColor' in activity
assert 'copySize=d(currentTopButtonSizeDp.toFloat())' in activity
assert 'setTextIsSelectable(true)' in activity
assert 'AlertDialog.Builder(this).setTitle(tr("title"))' not in activity
assert '"copy" -> {' in (root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'Применить' not in activity or 'no "Применить"' in activity
assert 'chooseObject.onItemSelectedListener=onSelection' in activity
assert 'chooseLod.onItemSelectedListener=onSelection' in activity
assert 'loadSelected()' in activity
assert 'headerRow.addView(topButtonHost' in activity
assert 'bottomPanel.addView(bottomButtonHost' in activity
toolbar=(root/'app/src/main/java/com/glassstudy/filament/UiToolbarController.kt').read_text()
assert 'toolbarController.render(ui,uiAnimations)' in activity
assert 'topHost.removeAllViews()' in toolbar
assert 'bottomHost.removeAllViews()' in toolbar
assert 'private fun button(spec:UiButtonSpec' in toolbar
bundle=(root/'app/src/main/java/com/glassstudy/filament/PatchUiBundle.kt').read_text()
assert 'PatchUiBundle.read(updater, candidate)' in activity
assert 'UiLayout.parse(updater.readUiConfig(candidate))' in bundle
assert all(('readModuleConfig("'+name+'", candidate)') in bundle for name in
           ('camera','render','diagnostics','materials','scene','windows','overlay'))
assert activity.index('val bundle = PatchUiBundle.read(updater, candidate)') < activity.index('patchWindows = ui.windows')
assert 'LabIconView(activity,spec.icon)' in toolbar
assert 'LabLogoView(this)' in activity
assert 'diagnosticButton' not in activity and 'quickObjects' not in activity
assert 'exportScene()' not in activity and 'applyButton' not in activity
icons=(root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'class LabIconView' in icons and 'class LabLogoView' in icons
assert 'canvas.drawRect(-4f,-4f,4f,4f,ink)' in icons
assert 'if(showUnderline) canvas.drawLine' in icons
assert 'if(chipCenterFilled)Paint.Style.FILL' in icons
assert 'glyphScale=ui.topIconScale' in toolbar
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
assert 'installedFiles.size <= MAX_ENTRIES' in updater
assert 'candidate.staging.parentFile?.canonicalFile != patchRoot.canonicalFile' in updater
assert 'candidate.staging.name.startsWith(expectedPrefix)' in updater
assert 'installedFiles.sumOf { it.length() } <= MAX_PATCH_BYTES' in updater
assert 'UiLayout.parse(stagedUi.readBytes())' in updater
assert 'PatchUiBundle.read(this, candidate)' in updater
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
assert [x['action'] for x in layout['uiButtons'] if x['slot']=='top']==['diagnostics','toggleLight','openScene','menu']
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
assert 'yaw+=dx*(if(invertHorizontal)-cameraModule.yawSensitivity else cameraModule.yawSensitivity)' in activity
assert 'val invertHorizontal=if(j.has("invertHorizontal"))j.getBoolean("invertHorizontal") else true' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert len(layout['diagnostics'])>=50
assert layout['diagnosticSections']==['updates','performance','memory','scene','renderer','camera','device']
instrumentation=(root/'app/src/main/java/com/glassstudy/filament/FrameProfiler.kt').read_text()
assert 'Process.getElapsedCpuTime()' in instrumentation
assert 'Debug.getMemoryInfo(memory)' in instrumentation
assert 'GlbMetrics.inspect' in activity
assert 'ByteBuffer.wrap(raw)' in instrumentation
assert 'profiler.frame(frameTimeNanos,submissionMs)' in activity
assert 'val framesBefore = viewer.renderedFrames' in activity
assert 'val frameWasRendered = viewer.renderedFrames > framesBefore' in activity
assert 'if (frameWasRendered && ++stableFrames == 4) updater.confirmFrameRendered()' in activity
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
assert 'if(spec.action=="toggleLight")target.setOnLongClickListener' in toolbar
assert 'host.onLightLongPress()' in toolbar
assert 'resetCameraToHome()' in activity
assert 'viewer.camera.lookAt(x, y, z, 0.0, 1.0, 0.0' in activity
assert 'distance=(distance*(oldPinch/span).toDouble().pow(zoomPower))' in activity
assert 'pitch=(pitch+dy*cameraModule.pitchSensitivity)' in activity
assert 'yaw+=dx*(if(invertHorizontal)-cameraModule.yawSensitivity else cameraModule.yawSensitivity)' in activity
assert 'LightGestureController.Axis.AZIMUTH' in activity and 'lightGesture.move(dx,dy)' in activity
assert 'LightGestureController.Axis.ELEVATION' in activity and '.coerceIn(0.0,Math.PI)' in activity
assert 'e.x-oldX' in activity and 'resources.displayMetrics.density' in activity
assert 'Signature.getInstance("SHA256withRSA")' in updater
assert 'signature.verify(' in updater
assert 'reconcileAfterCrash()' in updater and 'confirmFrameRendered()' in updater
assert 'putInt("blockedAfterCrash",pending)' in updater
assert 'val restored = prefs.edit().putInt("active",previous)' in updater
assert 'if (!restored)' in updater
assert 'Could not persist rollback of unconfirmed patch' in updater
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
assert 'GLB magic invalid:' in registry and 'GLB header invalid:' in registry
assert 'GLB first chunk is not JSON:' in registry
assert 'signature.verify(' in updater  # Modules must pass the old signature check.
for module in ('camera','render','diagnostics','materials'):
    config=json.loads((root/'app/src/main/assets/modules'/module/'config.json').read_text())
    assert config['id']==module and config['schema']==1
    assert 'readModuleConfig("'+module+'", candidate)' in bundle
assert 'MaterialOverrides.parse(' in activity
assert '"openScene" -> openSceneMenu()' in activity
assert 'sceneSettings.show()' in activity
assert 'sceneEnvironment.applyConfiguration(bundle.scene)' in activity
assert 'sceneEnvironment.applySkybox()' in activity
assert 'SceneSettingsPanel(this,sceneEnvironment)' in activity
environment=(src/'SceneEnvironmentController.kt').read_text()
panel=(src/'SceneSettingsPanel.kt').read_text()
assert 'SceneFloorGlb.create(true,module.floorWidth' in environment and 'SceneFloorGlb.create(false,module.floorWidth' in environment
assert 'fun setFloorGlb(buffer: Buffer?)' in custom
assert 'floorAsset?.let { populateScene(it) }' in custom
assert '"scene" -> {' in icons
assert '"openScene"' in uic
assert layout['uiButtons'][2]['icon']=='scene'
assert 'CameraModuleConfig.parse(config)' in updater
assert 'RenderModuleConfig.parse(config)' in updater
assert 'DiagnosticsModuleConfig.parse(config)' in updater
assert 'MaterialOverrides.parse(config)' in updater
assert 'CameraModuleConfig.parse(stagedModule("camera"))' in updater
assert 'RenderModuleConfig.parse(stagedModule("render"))' in updater
assert 'DiagnosticsModuleConfig.parse(stagedModule("diagnostics"))' in updater
assert 'Missing material variant: $path' in updater
assert 'material.hasParameter(name)' in (src/'MaterialOverrides.kt').read_text()
# Reject accidental default changes that would alter the installed app even without a patch.
camera=json.loads((root/'app/src/main/assets/modules/camera/config.json').read_text())
render=json.loads((root/'app/src/main/assets/modules/render/config.json').read_text())
diagnostics=json.loads((root/'app/src/main/assets/modules/diagnostics/config.json').read_text())
materials=json.loads((root/'app/src/main/assets/modules/materials/config.json').read_text())
assert camera['fieldOfView']==45 and camera['minDistance']==2.7 and camera['maxDistance']==60
assert camera['yawSensitivity']==0.008 and camera['pitchSensitivity']==0.005
assert render['iblIntensity']==30000 and render['fxaa'] is True
assert render['exposureAperture']==16 and render['exposureSensitivity']==100
assert diagnostics['includeModuleStatus'] is False and diagnostics['includeRenderParameters'] is False
assert diagnostics['includeCameraParameters'] is False
assert materials['variants']=={} and materials['overrides']=={}
# The failure path must keep the previous active patch and restore its UI/model.
assert 'updater.reject(candidate)' in activity and 'applyUiLayout()' in activity and 'loadSelected()' in activity
assert 'blockedAfterCrash' in updater and 'feed.patchVersion != blockedVersion' in updater
print('PASS: modular resource registry, signed patch integration, module consumers and default JSON')
print('PASS: project identifiers, version, background updater, rollback, signature, gesture, key hygiene, 66 GLBs, IBL')
print('NOTE: Android SDK/Gradle compile and device runtime must be tested separately.')
# Scene style and module regression — standalone white/checker floors share width.
scene_config=json.loads((root/'app/src/main/assets/modules/scene/config.json').read_text())
scene_src=(root/'app/src/main/java/com/glassstudy/filament/SceneFloorGlb.kt').read_text()
scene_module=(root/'app/src/main/java/com/glassstudy/filament/SceneModuleConfig.kt').read_text()
assert scene_config['floorWidth']==12 and scene_config['tileWidth']==0.6
assert scene_config['darkLevel']<=0.01 and scene_config['whiteLevel']>=0.95
assert 'val cells = if (checker) (width / tileWidth)' in scene_src
assert 'number("floorWidth"' in scene_module and 'number("whiteLevel"' in scene_module
assert 'SceneFloorGlb.create(true,module.floorWidth,module.tileWidth' in environment
assert 'SceneFloorGlb.create(false,module.floorWidth,module.tileWidth' in environment
assert 'SceneModuleConfig.parse(updater.readModuleConfig("scene", candidate))' in bundle
assert '"modules/scene/config.json" -> SceneModuleConfig.parse(config)' in updater
assert 'SceneModuleConfig.parse(stagedModule("scene"))' in updater
assert 'theme.titles.getValue("scene")' in panel
assert '"white" to "Белая платформа"' in panel and 'heading("SKYBOX")' in panel

# Shared window presentation and signed scene-order controls.
scene_module=(root/'app/src/main/java/com/glassstudy/filament/SceneModuleConfig.kt').read_text()
dialog_factory=(root/'app/src/main/java/com/glassstudy/filament/GlassDialogFactory.kt').read_text()
assert 'state.module.sectionOrder.forEach' in panel
assert 'state.module.skyboxOrder.forEach' in panel
assert 'state.module.floorOrder.forEach' in panel
assert 'order("sectionOrder",listOf("skybox","floor"))' in scene_module
assert 'GlassDialogFactory.menu(this,GlassDialogFactory.theme.titles' in activity
assert 'theme.titles.getValue("patchRejected")' in activity
assert 'fun menu(activity:Activity' in dialog_factory
assert 'fun panel(activity:Activity' in dialog_factory
assert 'fun option(activity:Activity' in dialog_factory
assert 'fun message(activity:Activity' in dialog_factory
assert scene_config.get('sectionOrder')==['skybox','floor']

# All window creation uses the shared factory, including non-modal diagnostics.
assert 'AlertDialog.Builder(this)' not in activity
assert 'PopupWindow(outer,availableWidth' not in activity
assert 'GlassDialogFactory.popup(this,outer,availableWidth)' in activity
assert 'theme.titles.getValue("patchError")' in activity

# Shared window theme is signed, validated and consumed in all dialogs.
window_theme=json.loads((root/'app/src/main/assets/modules/windows/config.json').read_text())
assert window_theme['id']=='windows' and window_theme['schema']==1
assert 'WindowStyleConfig.parse(stagedModule("windows"))' in updater
assert '"modules/windows/config.json" -> WindowStyleConfig.parse(config)' in updater
assert 'GlassDialogFactory.applyTheme(bundle.windows)' in activity
assert 'GlassDialogFactory.theme.selected' in activity
assert 'var theme:WindowStyleConfig=WindowStyleConfig()' in dialog_factory

assert 'GlassDialogFactory.theme.diagnosticHeaderOrder.forEach' in activity
assert 'val diagnosticHeaderOrder:List<String>' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()
assert window_theme['diagnosticHeaderOrder']==['title','copy']
assert window_theme['menuOrder']==['indicatorStyle']

assert 'titles' in window_theme and window_theme['titles']['scene']=='СЦЕНА'
assert 'Unknown window title key' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

assert window_theme['diagnosticsClickMode']=='toggle'
assert 'host.windowMode("diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'diagnosticDismissedAtMs=SystemClock.uptimeMillis()' in activity
assert 'elapsed !in 0L..280L' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'setOf("toggle","openOnly","closeOnly")' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

assert window_theme['windowClickModes']=={'diagnostics':'toggle','openScene':'toggle','menu':'toggle'}
assert 'host.isWindowOpen(action)' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'GlassDialogFactory.closeCurrent()' in activity
assert 'key="openScene"' in panel
assert 'key="skyColor"' in panel
assert 'Unknown window behavior key' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

# Signed contextual overlay with safe live bindings and controlled appearance.
overlay_config=json.loads((root/'app/src/main/assets/modules/overlay/config.json').read_text())
assert overlay_config['showWith']=='light'
assert 'OverlayModuleConfig.parse(stagedModule("overlay"))' in updater
assert '"modules/overlay/config.json" -> OverlayModuleConfig.parse(config)' in updater
assert 'private fun updateContextualOverlay()' in activity
# Observable user changes pass through the common binding rather than a hardcoded renderer call.
assert 'showLightIndicator(LightIndicatorView.Mode.BRIGHTNESS' in activity
assert 'showLightIndicator(LightIndicatorView.Mode.AZIMUTH' in activity
assert 'showLightIndicator(LightIndicatorView.Mode.ELEVATION' in activity

assert 'showWith' in overlay_config and 'enabled' in overlay_config
assert 'LightIndicatorView(this)' in activity

# Rich window schema is declarative and wired into the real UI, not just docs.
window_schema=(root/'app/src/main/java/com/glassstudy/filament/DeclarativeWindows.kt').read_text()
rich_renderer=(root/'app/src/main/java/com/glassstudy/filament/PatchWindowRenderer.kt').read_text()
example=json.loads((root/'PROJECT_RULES/RICH_WINDOW_EXAMPLE.json').read_text())
assert 'PatchWindowRenderer.show(' in activity and 'this, spec, uiAnimations[id]' in activity
assert 'key = "patch:${window.id}"' in rich_renderer
assert 'patchWindows.keys.firstOrNull' in activity and 'patchToRestore ->' not in activity
assert 'patchToRestore != null -> openPatchWindow(patchToRestore)' in activity
assert all(('"' + kind + '"') in window_schema for kind in ('heading','text','button','switch','slider'))
assert 'Exactly one of buttons or elements is required' in window_schema
assert 'Unknown window element field' in window_schema
assert 'Unresolved window target' in window_schema
assert 'readBinding(binding)' in rich_renderer and 'writeBinding(binding,' in rich_renderer
assert 'brightnessPercent' in activity and 'brightnessPercent' in window_schema
assert 'GlassDialogFactory.show(activity, scroll, fraction' in rich_renderer
assert len(example['windows'])==1
assert example['windows'][0]['elements'][3]['type']=='slider'
assert example['windows'][0]['elements'][3]['binding']=='brightnessPercent'
assert example['windows'][0]['landscapeWidthFraction'] < example['windows'][0]['widthFraction']

# Central controller: native buttons, patch window controls and overlays share
# the same validated action registry and side-effect layer.
interaction=(root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'object UiInteractionRegistry' in interaction
assert 'class UiInteractionController' in interaction
assert 'UiInteractionRegistry.acceptsAction(action)' in interaction
assert 'fun acceptsBinding(type: String, binding: String)' in interaction
toolbar=(root/'app/src/main/java/com/glassstudy/filament/UiToolbarController.kt').read_text()
assert 'class UiToolbarController(' in toolbar
assert 'UiToolbarController(this,topButtonHost,bottomButtonHost,topPanel,' in activity
assert 'toolbarController.render(ui,uiAnimations)' in activity
assert 'host.dispatch(spec.action)' in toolbar
assert 'uiController.dispatch(action)' in activity
assert 'readBinding = uiController::read' in activity
assert 'writeBinding = uiController::write' in activity
assert 'performAction = uiController::dispatch' in activity
assert 'UiInteractionRegistry.acceptsAction(action)' in uic
assert 'UiInteractionRegistry.acceptsBinding(type, binding)' in window_schema
assert 'overlayBrightnessSlider?.progress =' not in activity
assert 'onTouchEvent(event:android.view.MotionEvent?):Boolean=false' in (root/'app/src/main/java/com/glassstudy/filament/LightIndicatorView.kt').read_text()

# Release signing is a separate, explicit gate, never an unsigned APK renamed
# to a usable build or a fallback key with a different certificate.
signer=(root/'tools/sign_original_apk.py').read_text()
assert 'ORIGINAL_APK_CERT_SHA256' in signer
assert 'inspect_zip_alignment(unsigned)' in signer
assert 'verify_payload_unchanged(unsigned, output)' in signer
assert '"--v2-signing-enabled", "true"' in signer
assert '"--v3-signing-enabled", "false"' in signer
assert 'output.unlink(missing_ok=True)' in signer

# Input arbiter is decoupled from graphics: first dominant axis stays locked.
gesture_code=(root/'app/src/main/java/com/glassstudy/filament/LightGestureController.kt').read_text()
assert 'class LightGestureController' in gesture_code
assert 'if (lockedAxis == null)' in gesture_code
assert 'Axis.ELEVATION else Axis.AZIMUTH' in gesture_code
assert 'lightGesture.reset()' in activity
assert 'brightness+(span-oldPinch)/density/160.0' in activity

# Modular toolbar and settings-only gear. Old signed menu is migrated safely.
assert any(b['action']=='menu' and b['icon']=='gear' for b in json.loads((root/'app/src/main/assets/ui/layout.json').read_text())['uiButtons'])
assert 'button.copy(icon = "gear", label = "")' in (root/'app/src/main/java/com/glassstudy/filament/PatchUiBundle.kt').read_text()
assert 'val legacy=setOf("model","camera","diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()
assert 'LightIndicatorSettingsPanel(this)' in activity
assert 'LightingIndicatorStyles.save(activity,mode,variants[index].id)' in (root/'app/src/main/java/com/glassstudy/filament/LightIndicatorSettingsPanel.kt').read_text()
assert 'LightingIndicatorStyles.saveColor(activity,mode,' in (root/'app/src/main/java/com/glassstudy/filament/LightIndicatorSettingsPanel.kt').read_text()

# Modular renderer owns actual state/interaction wiring; MainActivity remains host.
assert 'fun updateHighlights()' in toolbar
assert 'fun render(ui:UiLayout,animations:Map<String,UiAnimationSpec>)' in toolbar
assert 'host.dispatch(spec.action)' in toolbar
assert 'host.isActive(spec.action)' in toolbar
assert 'host.decorate(' in toolbar
