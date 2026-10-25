#!/usr/bin/env python3
"""Offline static regression suite. Not a substitute for Android runtime tests."""
import re,struct,sys,json,hashlib,base64
from pathlib import Path
from xml.etree import ElementTree
root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/com/glassstudy/filament/MainActivity.kt').read_text()
local_patch=(root/'app/src/main/java/com/glassstudy/filament/LocalPatchDirectory.kt').read_text()
chrome_src=(root/'app/src/main/java/com/glassstudy/filament/UiChromeController.kt').read_text()
camera_light=(root/'app/src/main/java/com/glassstudy/filament/CameraLightController.kt').read_text()
render_env=(root/'app/src/main/java/com/glassstudy/filament/RenderEnvironmentController.kt').read_text()
updater=(root/'app/src/main/java/com/glassstudy/filament/PatchManager.kt').read_text()
build=(root/'app/build.gradle').read_text()
manifest=ElementTree.parse(root/'app/src/main/AndroidManifest.xml')
assert "applicationId 'com.glassstudy.filament.stable'" in build
props=(root/'gradle.properties').read_text()
assert 'GLASSSTUDY_VERSION=0.2.66' in props and 'GLASSSTUDY_VERSION_CODE=20066' in props
assert "project.property('GLASSSTUDY_VERSION_CODE')" in build and "project.property('GLASSSTUDY_VERSION')" in build
assert 'BuildConfig.VERSION_NAME' in activity
assert 'BuildConfig.VERSION_NAME' in (root/'app/src/main/java/com/glassstudy/filament/DiagnosticsReportService.kt').read_text()
assert 'BuildConfig.VERSION_CODE' in (root/'app/src/main/java/com/glassstudy/filament/DiagnosticsReportService.kt').read_text()
assert 'button("Обновления' not in activity and 'button("Обновить' not in activity
assert 'updater.checkOnStart(' in activity
assert 'applyButton' not in activity
popup_src=(root/'app/src/main/java/com/glassstudy/filament/DiagnosticsPopupController.kt').read_text()
report_src=(root/'app/src/main/java/com/glassstudy/filament/DiagnosticsReportService.kt').read_text()
assert 'class DiagnosticsPopupController(' in popup_src and 'class DiagnosticsReportService(' in report_src
assert 'private val diagnosticsPopup by lazy' in activity
assert 'shown.showAtLocation(anchor' in popup_src
assert 'if (action == "diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'diagnosticsPopup.dismiss()' in activity
assert 'setTouchModal(false)' in (root/'app/src/main/java/com/glassstudy/filament/GlassDialogFactory.kt').read_text()
assert 'theme.titles.getValue("diagnostics")' in popup_src
assert 'LabIconView(activity,"copy")' in popup_src
assert '"diagnostics" -> diagnosticsPopup.isShowing' in activity
assert 'DiagnosticsPopupController(this,mainRoot) { updateButtonHighlights() }' in activity
assert 'windowBackground(activity,style.fill,style.border' in popup_src
assert 'iconColor=iconColor' in chrome_src
assert 'val size=dp(style.iconSizeDp.toFloat())' in popup_src
assert 'setTextIsSelectable(true)' in popup_src
assert 'AlertDialog.Builder(this).setTitle(tr("title"))' not in activity
assert '"copy" -> {' in (root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'Применить' not in activity or 'no "Применить"' in activity
assert 'chooseObject.onItemSelectedListener=onSelection' in activity
assert 'chooseLod.onItemSelectedListener=onSelection' in activity
assert 'loadSelected()' in activity
assert 'modelAssets.load(requested,debug,candidate)' in activity
assert 'modelAssets.configure(bundle.materials)' in activity
assert 'headerRow.addView(topButtonHost' in activity
assert 'bottomPanel.addView(bottomButtonHost' in activity
toolbar=(root/'app/src/main/java/com/glassstudy/filament/UiToolbarController.kt').read_text()
assert 'v.toolbar.render(ui,animations)' in chrome_src
assert 'topHost.removeAllViews()' in toolbar
assert 'bottomHost.removeAllViews()' in toolbar
assert 'private fun button(spec:UiButtonSpec' in toolbar
bundle=(root/'app/src/main/java/com/glassstudy/filament/PatchUiBundle.kt').read_text()
assert 'PatchUiBundle.read(updater, candidate)' in activity
assert 'UiLayout.parse(updater.readUiConfig(candidate))' in bundle
assert all(('readModuleConfig("'+name+'", candidate)') in bundle for name in
           ('camera','render','diagnostics','materials','scene','windows','overlay'))
assert 'val bundle = PatchUiBundle.read(updater, candidate)' in activity
assert 'uiChrome.apply(ui,bundle.windows,bundle.overlay)' in activity
assert 'windows=ui.windows' in chrome_src
assert 'LabIconView(activity,spec.icon)' in toolbar
assert 'LabLogoView(this)' in activity
assert 'diagnosticButton' not in activity and 'quickObjects' not in activity
assert 'exportScene()' not in activity and 'applyButton' not in activity
icons=(root/'app/src/main/java/com/glassstudy/filament/LabUiIcons.kt').read_text()
assert 'class LabIconView' in icons and 'class LabLogoView' in icons
assert 'canvas.drawRect(-4f,-4f,4f,4f,ink)' in icons
assert 'if(showUnderline) canvas.drawLine' in icons
assert 'if(chipCenterFilled)Paint.Style.FILL' in icons
assert 'glyphScale=spec.iconScale ?: ui.topIconScale' in toolbar
assert 'setSystemBarMode(ui,v.root)' in chrome_src
assert 'View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY' in chrome_src
assert 'v.hint.visibility=if(ui.showCameraHint)View.VISIBLE else View.GONE' in chrome_src
assert 'v.logo.showUnderline=ui.showLogoUnderline' in chrome_src
assert 'params.width=dp(ui.logoSizeDp.toFloat())' in chrome_src
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
assert 'Реально нарисованных кадров Filament:' in report_src
assert 'diagnosticLabels=ui.diagnostics' in chrome_src
assert 'diagnosticLabels=uiChrome.diagnosticLabels' in activity
assert 'updater.lastConfirmedVersion()' in report_src and 'updater.serverPatchVersion' in report_src
assert 'tr("pathTracing")' in report_src
assert 'lightingMode:LightEnvironmentMode' in report_src
assert 'PhysicalAtmosphereModel.skyIlluminance(' in report_src
assert 'Студийные HDRI: отключены' in report_src
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
assert 'params.topMargin=dp(ui.logoOffsetYDp.toFloat())' in chrome_src
assert 'if(ui.topOverlayOpacity==0)null' in chrome_src
assert 'topOutlineColor=ui.topButtonOutlineColor' in chrome_src
assert layout['logoColor']=="#6DE0DE"
assert layout['buttonTextColor']=="#A3D0D3"
assert layout['format']=='glassstudy-ui-v1' and layout['schema']==1
assert len(layout['uiButtons'])==5
assert [x['action'] for x in layout['uiButtons'] if x['slot']=='top']==['diagnostics','toggleLight','openScene','menu']
assert [x['action'] for x in layout['uiButtons'] if x['slot']=='bottom']==['toggleGrid']
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
assert 'cameraLight.configure(bundle.camera,ui.zoomPower,ui.invertHorizontal)' in activity
assert 'invertHorizontal=inverted' in camera_light
assert 'yaw+=dx*(if(invertHorizontal)-config.yawSensitivity else config.yawSensitivity)' in camera_light
assert 'val invertHorizontal=if(j.has("invertHorizontal"))j.getBoolean("invertHorizontal") else true' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert len(layout['diagnostics'])>=50
assert layout['diagnosticSections']==['updates','performance','memory','scene','renderer','camera','device']
instrumentation=(root/'app/src/main/java/com/glassstudy/filament/FrameProfiler.kt').read_text()
assert 'Process.getElapsedCpuTime()' in instrumentation
assert 'Debug.getMemoryInfo(memory)' in instrumentation
assert 'GlbMetrics.inspect' in report_src
assert 'ByteBuffer.wrap(raw)' in instrumentation
assert 'profiler.frame(frameTimeNanos,submissionMs)' in activity
assert 'val framesBefore = viewer.renderedFrames' in activity
assert 'val frameWasRendered = viewer.renderedFrames > framesBefore' in activity
assert 'if (frameWasRendered && ++stableFrames == 4) updater.confirmFrameRendered()' in activity
assert 'val samePatch=server==active.toString() && confirmed==active' in report_src
assert 'appendLine("${tr("compactPatches")}: $patchSummary")' in report_src
assert 'FPS — ритм кадров' in layout['diagnostics']['measurementNote']
assert 'FPS: ${num(perf.fps)}' in report_src
assert 'appendLine("GlassStudy ${BuildConfig.VERSION_NAME}' in report_src
assert 'appendLine("${tr("appVersion")}' not in report_src
assert layout['diagnostics']['installedPatch']=='Установленный патч ресурсов'
assert layout['diagnostics']['pathTracingValue']=='В этой бета-версии не реализована'
assert set(layout['topOrder'])=={'status','selectors','actions','grid'}
assert set(layout['bottomOrder'])=={'export','diagnostics'}
assert 'var distance=5.5' in camera_light
assert 'var pitch=.34' in camera_light
assert 'if(spec.action=="toggleLight")target.setOnLongClickListener' in toolbar
assert 'host.onLightLongPress()' in toolbar
assert 'resetCameraToHome()' in activity
assert 'viewer.camera.lookAt(x,y,z,0.0,1.0,0.0' in camera_light
assert 'distance=(distance*(oldPinch/span).toDouble().pow(zoomPower))' in camera_light
assert 'pitch=(pitch+dy*config.pitchSensitivity)' in camera_light
assert 'yaw+=dx*(if(invertHorizontal)-config.yawSensitivity else config.yawSensitivity)' in camera_light
assert 'LightGestureController.Axis.AZIMUTH' in camera_light and 'lightGesture.move(dx,dy)' in camera_light
assert 'LightGestureController.Axis.ELEVATION' in camera_light and '.coerceIn(0.0,Math.PI)' in camera_light
assert 'e.x-oldX' in camera_light and 'resources.displayMetrics.density' in activity
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
# One canonical goblet profile and one runtime mesh generator.
profile=json.loads((root/'app/src/main/assets/catalogs/goblet.json').read_text())
assert profile['schema']==1 and len(profile['profile'])>=30
assert 0.0003<=profile['wireThickness']<=0.01
assert max(r for y,r in profile['profile'] if 2.5<y<2.9) > max(r for y,r in profile['profile'] if y>3.30)
assert profile['profile'][-1][0]-1.43 < 0.2
geometry=(root/'app/src/main/java/com/glassstudy/filament/ParametricGoblet.kt').read_text()
assert 'object ParametricGoblet' in geometry and 'fun create(' in geometry
assert all('"'+lod+'"' in geometry for lod in ('low','medium','high','ultra'))
assert 'KHR_materials_unlit' in geometry and 'KHR_materials_transmission' in geometry
assert 'if(j<rings-1)append(a,a+sides)' in geometry
assert 'if(grid)array(0,0,0,1)' in geometry
loader=(root/'app/src/main/java/com/glassstudy/filament/ModelMaterialController.kt').read_text()
assert 'ParametricGoblet.create(profile,selected.lod,debug)' in loader
assert 'if(override!=null) return readCheckedResource(override,candidate)' in loader
assert 'if (path == ParametricGoblet.PATH) ParametricGoblet.validate(raw)' in updater
assert 'GlbMetrics.inspect(ParametricGoblet.create(' in report_src
# No pre-baked models are necessary, including during native CI compilation.
assert not list((root/'app/src/main/assets/models').glob('*.glb'))
# The old studio environment/color picker is intentionally not a lighting option.
# Compare canonical signing algorithm source against Python test helper.
sys.path.insert(0,str(root/'tools'))
import publish_patch
import make_models
# Geometry is one authored meridian for every LOD; verify its physical bowl
# proportions as well as the actual exported GLB meshes above.
make_models.check_profile_proportions()
assert len(make_models.PROFILE) >= 40
assert make_models.PROFILE[-1][0] < 1.65  # no thick floor at y=1.84
assert make_models.PROFILE[-1][0] - make_models.PROFILE[17][0] < .12
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
model_loader=(root/'app/src/main/java/com/glassstudy/filament/ModelMaterialController.kt').read_text()
model_catalog=(root/'app/src/main/java/com/glassstudy/filament/ModelCatalog.kt').read_text()
assert 'MaterialOverrides.parse(materialJson)' in model_loader
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
assert render['exposureAperture']==16 and render['exposureSensitivity']==100
assert diagnostics['includeModuleStatus'] is False and diagnostics['includeRenderParameters'] is False
assert diagnostics['includeCameraParameters'] is False
assert materials['variants']=={} and materials['overrides']=={}
# The failure path must keep the previous active patch and restore its UI/model.
coordinator=(src/'PatchActivationCoordinator.kt').read_text()
assert 'updater.reject(candidate)' in coordinator
assert 'runCatching { applyUiLayout() }' in activity
assert 'runCatching { loadSelected() }' in activity
assert 'blockedAfterCrash' in updater and 'feed.patchVersion != blockedVersion' in updater
print('PASS: modular resource registry, signed patch integration, module consumers and default JSON')
print('PASS: project identifiers, version, background updater, rollback, signature, gesture, key hygiene, one adaptive profile, physical lighting')
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

# Shared window presentation and signed scene-order controls.
scene_module=(root/'app/src/main/java/com/glassstudy/filament/SceneModuleConfig.kt').read_text()
dialog_factory=(root/'app/src/main/java/com/glassstudy/filament/GlassDialogFactory.kt').read_text()
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
assert 'GlassDialogFactory.popup(activity,outer,width)' in popup_src
assert 'theme.titles.getValue("patchError")' in activity

# Shared window theme is signed, validated and consumed in all dialogs.
window_theme=json.loads((root/'app/src/main/assets/modules/windows/config.json').read_text())
assert window_theme['id']=='windows' and window_theme['schema']==1
assert 'WindowStyleConfig.parse(stagedModule("windows"))' in updater
assert '"modules/windows/config.json" -> WindowStyleConfig.parse(config)' in updater
assert 'GlassDialogFactory.applyTheme(windowConfig)' in chrome_src
assert 'theme.selected' in dialog_factory
assert 'GlassDialogFactory.option(activity,label,selected)' in panel
assert 'var theme:WindowStyleConfig=WindowStyleConfig()' in dialog_factory

assert 'theme.diagnosticHeaderOrder.forEach' in popup_src
assert 'val diagnosticHeaderOrder:List<String>' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()
assert window_theme['diagnosticHeaderOrder']==['title','copy']
assert window_theme['menuOrder']==['localPatches']

assert 'titles' in window_theme and window_theme['titles']['scene']=='СЦЕНА'
assert 'Unknown window title key' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

assert window_theme['diagnosticsClickMode']=='toggle'
assert 'host.windowMode("diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'dismissedAtMs=SystemClock.uptimeMillis()' in popup_src
assert 'elapsed !in 0L..280L' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'setOf("toggle","openOnly","closeOnly")' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

assert window_theme['windowClickModes']=={'diagnostics':'toggle','openScene':'toggle','menu':'toggle'}
assert 'host.isWindowOpen(action)' in (root/'app/src/main/java/com/glassstudy/filament/UiInteractionController.kt').read_text()
assert 'GlassDialogFactory.closeCurrent()' in activity
assert 'key="openScene"' in panel
# The old studio environment/color picker is intentionally not a lighting option.
assert 'Unknown window behavior key' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()

# Signed contextual overlay with safe live bindings and controlled appearance.
overlay_config=json.loads((root/'app/src/main/assets/modules/overlay/config.json').read_text())
assert overlay_config['showWith']=='light'
assert 'OverlayModuleConfig.parse(stagedModule("overlay"))' in updater
assert '"modules/overlay/config.json" -> OverlayModuleConfig.parse(config)' in updater
assert 'fun updateIndicatorVisibility(lightMode:Boolean)' in chrome_src
# Observable user changes pass through the common binding rather than a hardcoded renderer call.
assert 'showIndicator(LightIndicatorView.Mode.BRIGHTNESS' in camera_light
assert 'showIndicator(LightIndicatorView.Mode.AZIMUTH' in camera_light
assert 'showIndicator(LightIndicatorView.Mode.ELEVATION' in camera_light

assert 'showWith' in overlay_config and 'enabled' in overlay_config
assert 'LightIndicatorView(this)' in activity
assert 'uiChrome.showIndicator(mode,value,cameraLight.lightMode,' in activity

# Rich window schema is declarative and wired into the real UI, not just docs.
window_schema=(root/'app/src/main/java/com/glassstudy/filament/DeclarativeWindows.kt').read_text()
rich_renderer=(root/'app/src/main/java/com/glassstudy/filament/PatchWindowRenderer.kt').read_text()
example=json.loads((root/'PROJECT_RULES/RICH_WINDOW_EXAMPLE.json').read_text())
assert 'PatchWindowRenderer.show(' in activity and 'this, spec, uiChrome.animations[id]' in activity
assert 'key = "patch:${window.id}"' in rich_renderer
assert 'uiChrome.windows.keys.firstOrNull' in activity and 'patchToRestore ->' not in activity
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
assert 'v.toolbar.render(ui,animations)' in chrome_src
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
assert 'lightGesture.reset()' in camera_light
assert 'brightness+(span-oldPinch)/dp/config.brightnessPinchDivisorDp' in camera_light

# Modular toolbar and settings-only gear. Old signed menu is migrated safely.
assert any(b['action']=='menu' and b['icon']=='gear' for b in json.loads((root/'app/src/main/assets/ui/layout.json').read_text())['uiButtons'])
assert 'button.copy(icon = "gear", label = "")' in (root/'app/src/main/java/com/glassstudy/filament/PatchUiBundle.kt').read_text()
assert 'val legacy=setOf("model","camera","diagnostics")' in (root/'app/src/main/java/com/glassstudy/filament/WindowStyleConfig.kt').read_text()
assert 'LightIndicatorSettingsPanel(' not in activity
assert 'LightingIndicatorStyles' not in activity
assert 'Стиль индикаторов освещения' not in activity
assert 'Папка локальных патчей' in activity
assert 'names==listOf("indicatorStyle")' in (src/'WindowStyleConfig.kt').read_text()
assert not (src/'LightIndicatorSettingsPanel.kt').exists()
assert not (src/'LightingIndicatorStyles.kt').exists()

# Modular renderer owns actual state/interaction wiring; MainActivity remains host.
assert 'fun updateHighlights()' in toolbar
assert 'fun render(ui:UiLayout,animations:Map<String,UiAnimationSpec>)' in toolbar
assert 'host.dispatch(spec.action)' in toolbar
assert 'host.isActive(spec.action)' in toolbar
assert 'host.decorate(' in toolbar

# Modular diagnostics: report data, popup and action lifecycle are separate.
assert 'diagnosticsReports.create(DiagnosticsReportState(' in activity
assert 'diagnosticsPopup.show(report,uiChrome.popupStyle())' in activity
assert 'fun create(state:DiagnosticsReportState):String' in report_src
assert 'GlbMetrics.inspect(updater.readModel(path))' in report_src
assert 'MemoryMetrics.capture(context)' in report_src
assert 'profiler.metrics()' in report_src
assert 'tr("notMeasured")' in report_src
assert 'setTextIsSelectable(true)' in popup_src
assert 'copy.setOnClickListener' in popup_src
assert 'isShowing:Boolean get()=popup?.isShowing==true' in popup_src
assert 'setTouchModal(false)' in dialog_factory
assert 'windowBackground(activity,theme.background,theme.border)' in popup_src
assert 'diagnosticsPopup.dismissedAtMs' in activity

# Camera/light/render separate ownership and signed resource boundary.
assert 'class CameraLightController(' in camera_light
assert 'class RenderEnvironmentController(' in render_env
assert 'surface.setOnTouchListener { _, event -> cameraLight.onTouch(event) }' in activity
assert 'cameraLight.configure(bundle.camera,ui.zoomPower,ui.invertHorizontal)' in activity
assert 'renderEnvironment.apply(bundle.render)' in activity
assert 'cameraLight.applyToFilament()' in activity
assert 'cameraLight.setBrightnessPercent(value)' in activity
assert 'cameraLight.resetHome()' in activity
assert 'val direct=LightingResponseCurve.intensity(brightness,config.directBrightnessExponent)' in camera_light
assert 'viewer.camera.setExposure(' in render_env
assert 'viewer.view.antiAliasing=' in render_env
assert 'LightGestureController()' in camera_light
assert 'diagnosticsReports.create(DiagnosticsReportState(' in activity

# Model selection and material variants share one validated, rollback-aware module.
assert 'class ModelMaterialController(' in model_loader
assert 'object ModelCatalog' in model_catalog
assert 'val ids=listOf("goblet")' in model_catalog
assert 'val names=listOf("Бокал")' in model_catalog
assert 'val qualities=listOf("low","medium","high","ultra")' in model_catalog
assert 'ModelCatalog.qualityNames.toTypedArray()' in activity
assert 'fun load(' in model_loader
assert 'MaterialModuleConfig.parse(materialJson)' in model_loader
assert 'MaterialOverrides.parse(materialJson)' in model_loader
assert 'PatchResourceRegistry.validate(path,bytes)' in model_loader
assert 'updater.readResourceOrNull(path,candidate)' in model_loader
assert 'viewer.loadModelGlb(direct(bytes))' in model_loader
assert 'require(viewer.asset!=null)' in model_loader
assert 'restore(previous)' in model_loader
assert 'updater.readModuleConfig("materials")' in model_loader
assert 'modelAssets.hasLoaded' in activity
assert 'if(loadSelected(debugOverride=enabled))' in activity
assert '.putBoolean("wireframe",enabled).apply()' in activity
assert 'chooseObject.setSelection(ModelCatalog.indexOfId(saved.modelId))' in activity
assert 'chooseLod.setSelection(ModelCatalog.indexOfQuality(saved.lod))' in activity
assert 'modelId=modelAssets.selection.modelId' in activity
assert 'modelName=ModelCatalog.displayName(modelAssets.selection.modelId)' in activity
assert 'private fun modelData(' not in activity
assert 'MaterialOverrides.parse(' not in activity

# One signed UI chrome renderer owns the toolbar colors, status, controls and orientation.
assert 'class UiChromeController(' in chrome_src
assert 'data class UiChromeViews(' in chrome_src
assert 'uiChrome.bind(UiChromeViews(' in activity
assert 'uiChrome.apply(ui,bundle.windows,bundle.overlay)' in activity
assert 'GlassDialogFactory.applyTheme(windowConfig)' in chrome_src
assert 'v.toolbar.render(ui,animations)' in chrome_src
assert 'selectorColor=ui.selectorTextColor' in chrome_src
assert 'topOutlineColor=ui.topButtonOutlineColor' in chrome_src
assert 'v.hint.text=if(lightMode)' in chrome_src
assert 'uiChrome.popupStyle()' in activity
assert 'uiChrome.updateStatus(cameraLight.lightMode)' in activity
assert 'uiChrome.updateIndicatorVisibility(cameraLight.lightMode)' in activity
assert 'v.indicator.hide()' in chrome_src
assert 'LightingIndicatorStyles.selected(activity,mode)' not in chrome_src
assert 'private fun selectionAdapter(' not in activity
assert 'private fun setSystemBarMode(' not in activity
assert 'private fun skin(' not in activity
assert 'uiChrome.selectionAdapter(' in activity

# Prevent the v0.2.37 broken extraction from silently reaching the Kotlin compiler.
assert '/** Safe dynamic layout:    /**' not in activity
assert activity.count('private fun applyUiLayout(')==1
for required in ('private fun applyUiLayout(','private fun loadSelected(',
                 'private fun resetCameraToHome(', 'private fun status(',
                 'private fun showDiagnostic('):
    assert required in activity, 'Lost lifecycle method: '+required
assert 'uiChrome.apply(ui,bundle.windows,bundle.overlay)' in activity

# Version 0.2.38: signed patch state machine & explicit recovery
assert 'PatchActivationCoordinator(updater,object:PatchActivationCoordinator.Host' in activity
assert 'PatchUiBundle.read(updater,candidate)' in activity
assert 'val outcome=patchActivation.apply(candidate)' in activity
assert 'stableFrames=0' in activity
assert 'if(outcome.installed)' in activity
assert 'updater.activate(candidate)' in coordinator
assert coordinator.index('host.validate(candidate)') < coordinator.index('host.previewModel(candidate)') < coordinator.index('host.previewUi(candidate)') < coordinator.index('updater.activate(candidate)')
assert 'updater.reject(candidate)' in coordinator
assert 'host.restoreActive()' in coordinator
assert 'updater.reportActivationFailure(candidate.version,message)' in coordinator
assert 'val pending=prefs.getInt("pending",0)' in updater
assert 'if(pending!=0)' in updater[updater.index('fun activate(candidate: Candidate)'):updater.index('fun reject(candidate: Candidate)')]
assert updater.index('if(pending!=0)',updater.index('fun activate(candidate: Candidate)')) < updater.index('val current=installedPatchVersion()',updater.index('fun activate(candidate: Candidate)'))
assert 'if (!prefs.edit().putInt("previous",current).putInt("active",candidate.version)' in updater
assert 'fun reportActivationFailure(version:Int,detail:String)' in updater
assert 'val ui=runCatching { applyUiLayout() }' in activity
assert 'val model=runCatching { loadSelected() }' in activity

# Release-grade offline RSA test and unsigned APK identity/16 KiB alignment preflight.
test_patch=(root/'tools/test_patch_integrity.py').read_text()
test_apk=(root/'tools/test_apk_preflight.py').read_text()
assert 'verify_rsa(public_der, signature, message)' in test_patch
assert 'live_public_key()' in test_patch
assert 'feed.get("patchSha256") == hashlib.sha256(patch_bytes).hexdigest()' in test_patch
assert 'PASS rejected tamper:' in test_patch
assert 'zipalign' in test_apk and '16384' in test_apk
assert '"apksigner", "verify"' in test_apk
assert 'GLASSSTUDY_VERSION_CODE' in test_apk
assert 'require(prefs.getInt("pending",0)==0)' in updater

# Compact visual buttons preserve accessible 44dp hit zones. Gear valleys are signed-style patchable.
assert 'topVisualSizeDp=ui.topButtonSizeDp' in chrome_src
assert 'InsetDrawable(surface,inset)' in chrome_src
assert 'val hitPx=view.layoutParams?.width' in chrome_src
assert 'gearInnerRoundness=ui.gearInnerRoundness' in toolbar
assert 'val gearInnerRoundness:Float' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert 'number("gearInnerRoundness",0.80,0.30,1.0)' in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert 'gear.quadTo(valley.first,valley.second,next.first,next.second)' in icons
assert 'for(i in 0 until 8)' in icons
assert 'gearInnerRoundness:Float=.80f' in icons

# No fixed toolbar gaps: independently signed, parsed and validated controls.
for field in ('topButtonGapDp','topButtonLeadingDp','bottomButtonGapDp','bottomRowGapDp'):
    assert field in layout and ('val '+field+':Float') in uic
assert 'ui.topButtonLeadingDp else ui.topButtonGapDp' in toolbar
assert 'ui.bottomButtonGapDp' in toolbar
assert 'dp(ui.bottomRowGapDp)' in toolbar
assert 'leftMargin=dp(4f)' not in toolbar
assert 'leftMargin=dp(8f)' not in toolbar
assert 'topMargin=dp(8f)' not in toolbar

# Per-button signed dimensions and positions for BOTH top and bottom actions.
for field in ('widthDp','visualSizeDp','iconScale','offsetXDp','offsetYDp','marginStartDp','marginTopDp'):
    assert ('val '+field+':') in uic or ('val '+field+':') in toolbar or field in (root/'app/src/main/java/com/glassstudy/filament/UiLayout.kt').read_text()
assert 'spec.visualSizeDp' in toolbar
assert 'target.translationX=dp(spec.offsetXDp).toFloat()' in toolbar
assert 'target.translationY=dp(spec.offsetYDp).toFloat()' in toolbar
assert 'spec.widthDp?.let' in toolbar
assert 'spec.marginStartDp ?:' in toolbar
assert 'view.visualSizeOverrideDp ?: topVisualSizeDp' in chrome_src

# Android 16 scoped-storage offline patch folder, persistent read access and same RSA stage.
local_folder=(root/'app/src/main/java/com/glassstudy/filament/LocalPatchDirectory.kt').read_text()
assert 'Intent.ACTION_OPEN_DOCUMENT_TREE' in local_folder
assert 'takePersistableUriPermission' in local_folder
assert 'DocumentsContract.buildChildDocumentsUriUsingTree' in local_folder
assert 'Regex(' in local_folder and '.pupdate' in local_folder
assert 'fun newestAbove(installed:Int):Uri?' in local_folder
assert 'private fun checkLocalThenRemote()' in activity
assert 'localPatchFolder.candidatesAbove(updater.installedPatchVersion())' in activity
assert 'updater.installFromUri(candidates[index]' in activity
assert 'updater.checkOnStart(' in activity
assert 'localPatchFolder.requestFolder(patchFolderRequest)' in activity
assert 'Папка локальных патчей' in activity
assert 'android.permission.MANAGE_EXTERNAL_STORAGE' not in (root/'app/src/main/AndroidManifest.xml').read_text()

# Android 16 offline updates require a one-time scoped folder grant; no broad file permission.
assert 'Intent.ACTION_OPEN_DOCUMENT_TREE' in local_patch
assert 'Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION' in local_patch
assert 'takePersistableUriPermission' in local_patch
assert 'Выбрать Downloads/GlassStudy_Patches (один раз)' in activity
assert 'localPatchFolder.candidatesAbove(updater.installedPatchVersion())' in activity
assert 'version != prefs.getInt("blockedAfterCrash",0)' in updater

# Resume checks for locally delivered patches without a GitHub publication.
assert 'lastPatchFolderScanUptimeMs' in activity
assert '!localPatchBusy' in activity
assert 'checkLocalThenRemote()' in activity[activity.index('override fun onResume()'):activity.index('override fun onBackPressed()')]

assert 'fun reportLocalScan(message: String)' in updater
assert 'updater.reportLocalScan("Локальная папка проверена:' in activity

# Offline scan regression: preserve grants on transient provider failures and
# try lower-ranked (but still newer) files after signature/hash rejection.
assert 'fun candidatesAbove(installed:Int):List<Uri>' in local_patch
assert 'matches.sortedByDescending { it.first }' in local_patch
assert 'lastScanError=' in local_patch
assert 'prefs.edit().remove(key)' not in local_patch
assert 'fun attempt(index:Int, failures:List<String>)' in activity
assert 'attempt(index+1,failures +' in activity
assert 'Ошибка чтения локальной папки:' in activity
assert 'Локальные патчи отклонены:' in activity
assert 'localPatchBusy=false' in activity

# v0.2.65: never request sensitive all-files access; 1x SAF persisted folder.
assert 'Environment.isExternalStorageManager()' not in local_patch
assert 'Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION' not in local_patch
assert 'Environment.DIRECTORY_DOWNLOADS' not in local_patch
assert 'Uri.fromFile(file)' not in local_patch
assert 'fun requestDownloadsAccess()' not in local_patch
assert 'fun acceptGrant(data:Intent?):Boolean' in local_patch
assert 'private val patchFolderPromptedKey="localPatchPromptedV4"' in activity
assert 'if(!localPatchFolder.configured &&' in activity
assert 'takePersistableUriPermission' in local_patch

# One signed numeric system for all lighting indicators, not one-off Kotlin constants.
visuals=overlay_config['indicatorVisuals']
assert set(visuals)=={'brightness','azimuth','elevation'}
assert visuals['brightness']['rotationAtMaxDeg']==90
assert visuals['brightness']['offsetXDp']>0
assert len(visuals['brightness'])>=18
assert all(set(v)==set(visuals['brightness']) for v in visuals.values())
view_src=(root/'app/src/main/java/com/glassstudy/filament/LightIndicatorView.kt').read_text()
visual_src=(root/'app/src/main/java/com/glassstudy/filament/IndicatorVisualConfig.kt').read_text()
overlay_src=(root/'app/src/main/java/com/glassstudy/filament/OverlayModuleConfig.kt').read_text()
assert 'fun configureVisuals(next:Map<Mode,IndicatorVisualConfig>)' in view_src
assert 'rotation=pct*spec.rotationAtMaxDeg/200f' in view_src
assert 'translationX=spec.offsetXDp*density' in view_src
assert 'translationY=spec.offsetYDp*density' in view_src
assert 'canvas.translate(spec.offsetXDp' not in view_src
assert '"offsetXDp" to (-160.0..160.0)' in (src/'IndicatorVisualConfig.kt').read_text()
assert 'val visuals=j.optJSONObject("indicatorVisuals")' in overlay_src
assert 'IndicatorVisualConfig.parse(visuals?.optJSONObject' in overlay_src
assert 'v.indicator.configureVisuals(newOverlay.indicatorVisuals)' in chrome_src
assert 'require(value.isFinite() && value in requireNotNull(ranges[key]))' in visual_src
assert 'Unknown light indicator numeric property' in visual_src

# Cross-module one-number signed-patch coverage (source and real consumer wiring).
camera_json=json.loads((root/'app/src/main/assets/modules/camera/config.json').read_text())
camera_config_code=(src/'CameraModuleConfig.kt').read_text()
for key in ('homeYawRad','homePitchRad','homeDistance','homeLightAzRad',
            'homeLightElevationRad','minOrbitPitchRad','maxOrbitPitchRad',
            'brightnessPinchDivisorDp','lightAxisLockThresholdDp'):
    assert key in camera_json and ('"'+key+'"') in camera_config_code
assert 'config.brightnessPinchDivisorDp' in camera_light
assert 'config.minOrbitPitchRad,config.maxOrbitPitchRad' in camera_light
assert 'lightGesture.lockThresholdDp=next.lightAxisLockThresholdDp' in camera_light

render_module=(src/'RenderModuleConfig.kt').read_text()
render_consumer=(src/'RenderEnvironmentController.kt').read_text()
render_json=json.loads((root/'app/src/main/assets/modules/render/config.json').read_text())
assert render_json['ambientOcclusionEnabled'] is True
assert '"ambientOcclusionEnabled"' in render_module and 'enabled=next.ambientOcclusionEnabled' in render_consumer

sky_json=json.loads((root/'app/src/main/assets/modules/scene/config.json').read_text())
scene_module_code=(src/'SceneModuleConfig.kt').read_text()
for name in ('gray','white','dark'):
    for channel in ('Red','Green','Blue'):
        field=name+'Sky'+channel
        assert type(sky_json[field])==int and ('"'+field+'"') in scene_module_code

popup_numeric=window_theme.get('diagnosticsPopup')
assert popup_numeric and len(popup_numeric)==12
window_style_code=(src/'WindowStyleConfig.kt').read_text()
for key in popup_numeric:
    assert ('"'+key+'"') in window_style_code
    assert ('nums.'+key) in popup_src
for key in ('topReservedWidthDp','topMinButtonWidthDp'):
    assert key in layout and ('"'+key+'"') in (src/'UiLayout.kt').read_text()
    assert ('ui.'+key) in toolbar

assert 'def numeric_catalog():' in (root/'tools/fast_patch.py').read_text()

# Shared UI chrome geometry also flows through the same signed numeric API.
visual_defaults=layout['visualNumbers']
assert len(visual_defaults)==9
visual_parser=(src/'UiVisualNumbers.kt').read_text()
for setting in visual_defaults:
    assert ('"'+setting+'"') in visual_parser
    assert ('visualNumbers.'+setting) in chrome_src
assert 'UiVisualNumbers.parse(j.optJSONObject("visualNumbers"))' in (src/'UiLayout.kt').read_text()

# Persistent lighting HUD: pointer-up must NOT hide, a stationary single tap does.
touch_src=(src/'LightTouchSession.kt').read_text()
assert 'class LightTouchSession' in touch_src
assert 'fun begin(x:Float,y:Float)' in touch_src
assert 'fun onMultiplePointers()' in touch_src
assert 'fun onMove(x:Float,y:Float,density:Float)' in touch_src
assert 'fun finish():Boolean' in touch_src
assert 'fun cancel()' in touch_src
assert 'var hadMultiplePointers=false' in touch_src
assert 'val wasTap=lightTouch.finish()' in camera_light
assert 'if(lightMode && wasTap)hideIndicator()' in camera_light
assert 'if(!lightTouch.hadMultiplePointers)' in camera_light
assert 'lightTouch.tapSlopDp=next.lightTapSlopDp' in camera_light
assert 'lightTapSlopDp' in camera_config_code and camera_json['lightTapSlopDp']==6
assert 'MotionEvent.ACTION_POINTER_UP -> {' in camera_light
assert 'MotionEvent.ACTION_CANCEL -> {' in camera_light
assert 'MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()' not in camera_light
pointer_up_block=camera_light.split('MotionEvent.ACTION_POINTER_UP -> {')[1].split('MotionEvent.ACTION_UP -> {')[0]
assert 'resetGesture()' not in pointer_up_block
assert 'hideIndicator()' not in pointer_up_block
up_block=camera_light.split('MotionEvent.ACTION_UP -> {')[1].split('MotionEvent.ACTION_CANCEL -> {')[0]
assert up_block.count('hideIndicator()')==1
assert (root/'tools/LightTouchSessionRegression.kt').is_file()

# ACTION_UP coordinates must be checked, even when a quick swipe has no MOVE event.
assert 'lightTouch.onMove(e.x,e.y,density())' in camera_light

# Technical light visuals share one sun tint/glow, with no in-app style or RGB editor.
projection=(src/'LightGuideProjection.kt').read_text()
indicator=(src/'LightIndicatorView.kt').read_text()
overlay_module=(src/'OverlayModuleConfig.kt').read_text()
assert 'val options=listOf(' not in indicator
assert 'private var style=' not in indicator
assert 'glowEngine.drawRadial(canvas,0f,0f,tint,glow.ambient)' in indicator
assert 'glowEngine.drawStroke(canvas,rayPath,tint,spec.sunStrokeUnits,glow.secondaryStroke)' in indicator
assert 'glowEngine.drawStroke(canvas,ringPath,tint,spec.sunStrokeUnits,glow.primaryStroke)' in indicator
assert 'drawSunGlyph(canvas)' in indicator
assert 'pen.setShadowLayer(spec.sunGlowUnits' not in indicator
assert 'setShadowLayer(spec.guideGlowUnits' in indicator
assert 'canvas.drawOval(' not in indicator
assert 'canvas.drawLine(0f,0f,x' not in indicator
assert 'canvas.drawLine(0f,-4f,x' not in indicator
assert 'private fun drawSunGlyph(' in indicator
assert 'ringPath.addCircle(0f,0f,spec.sunRadiusUnits,Path.Direction.CW)' in indicator
assert 'canvas.drawPath(ringPath,pen)' in indicator
assert 'if(kind==Mode.BRIGHTNESS)canvas.rotate(rotation)' in indicator
assert 'drawOrbit(canvas,kind,spec,x,y)' in indicator
assert 'GuideGapClipMath.visibleSegments(' in indicator
assert 'private fun nearSun(' not in indicator
assert 'private var fadeOutPending=false' in indicator
assert 'mode=null;animate().cancel()' not in indicator
assert (root/'tools/GuideGapClipRegression.kt').is_file()
assert 'LightGuideProjection.azimuth(' in indicator
assert 'LightGuideProjection.azimuthOrbit(theta,spec.guideRadiusUnits,' in indicator
assert 'fun azimuthOrbit(' in projection
assert 'LightDirectionMath.azimuthMarkerOnHorizon(' in projection
assert 'fun azimuthMarkerOnHorizon(' in (root/'app/src/main/java/com/glassstudy/filament/LightDirectionMath.kt').read_text()
assert 'LightDirectionMath.source(angleRad,0.0)' in projection
assert 'LightGuideProjection.elevation(' in indicator
assert 'fun azimuth(' in projection and 'fun elevation(' in projection
assert 'return Point(' in projection
assert 'fun indicatorColor(mode:LightIndicatorView.Mode):Int=brightnessColor' in overlay_module
assert 'RotationAtMaxDeg' not in projection
assert 'guideSamples' in visual_src and 'guideGlowUnits' in visual_src
assert 'sunGlowUnits' in visual_src and 'glowOpacity' in visual_src
assert set(visuals['brightness'])==set(visuals['azimuth'])==set(visuals['elevation'])
assert all(visuals[name]['rotationAtMaxDeg']==0 for name in ('azimuth','elevation'))
assert all(visuals[name]['sunGlowUnits']==10 for name in visuals)
assert all(visuals[name]['guideSamples']==180 for name in visuals)
assert all(visuals[name]['rayLengthMaxUnits']==12 for name in visuals)
assert (root/'tools/LightGuideProjectionRegression.kt').is_file()

# v0.2.54 regression: the physical source, Filament rays and projected
# indicator always agree. Test the seamless 0/360 crossing and labels.
light_math=(src/'LightDirectionMath.kt').read_text()
projection=(src/'LightGuideProjection.kt').read_text()
camera=(src/'CameraLightController.kt').read_text()
view=(src/'LightIndicatorView.kt').read_text()
assert 'LightDirectionMath.incoming(lightAz,lightElevation)' in camera
assert 'mgr.setDirection(instance,rays.x,rays.y,rays.z)' in camera
assert 'LightDirectionMath.source(lightAzimuthRad,angleRad)' in projection
assert 'fun shortestAzimuthDelta(' in light_math
assert 'LightDirectionMath.shortestAzimuthDelta(target,value)' in view
assert 'fun elevationLabel(' in light_math
assert 'LightDirectionMath.elevationLabel(deg)' in view
assert 'private var brightnessPercent=100f' in view
assert 'brightnessPercent*(spec.rayLengthMaxUnits-spec.rayLengthMinUnits)/200f' in view
assert 'lightElevationRad:Double,currentBrightnessPercent:Float' in view
assert '(cameraLight.brightness*100).toFloat()' in activity
assert 'val rays=LightDirectionMath.incoming(' in camera
assert 'canvas.drawRoundRect(left,top,right,bottom,' in view
assert 'captionPlate.color=Color.argb(' in view
assert 'captionBoxPaddingXUnits' in visual_src and 'captionBoxCornerUnits' in visual_src
assert 'captionBoxOpacity' in visual_src and 'captionBoxPaddingYUnits' in visual_src
assert 'overlay.backgroundColor ?: Color.rgb(17,35,49)' in chrome_src
for kind in ('azimuth','brightness','elevation'):
    spec=visuals[kind]
    assert 0.15 < spec['captionBoxOpacity'] <= 0.55
    assert spec['captionBoxCornerUnits'] > 0
    assert spec['captionBoxPaddingXUnits'] > 0
    assert spec['captionBoxPaddingYUnits'] > 0
assert (root/'tools/LightDirectionMathRegression.kt').is_file()

# Translucent caption plates reveal the scene beneath; never cover it opaquely.
assert 'val captionBoxOpacity:Float=.45f' in visual_src
assert all(abs(visuals[m]['captionBoxOpacity']-0.45)<0.001 for m in visuals)

# The dark caption PLATE is translucent, but the number and %/° glyphs stay opaque.
assert 'caption.alpha=255' in view
assert 'caption.color=Color.rgb(Color.red(tint),Color.green(tint),Color.blue(tint))' in view
assert view.index('captionPlate.color=Color.argb(') < view.index('caption.alpha=255')

# 20039 patch-only: no duplicated bottom light/camera control; original top sun
# remains functional and one shared soft halo applies to all three indicator suns.
assert not any(x['id']=='light_bottom' for x in layout['uiButtons'])
assert any(x['slot']=='top' and x['action']=='toggleLight' for x in layout['uiButtons'])
assert [x['id'] for x in layout['uiButtons'] if x['slot']=='bottom']==['grid']
assert overlay_config['indicatorVisuals']['brightness']['offsetXDp']==100
for kind in ('azimuth','elevation','brightness'):
    glow=overlay_config['indicatorVisuals'][kind]
    assert glow['guideGlowUnits']==0
    assert glow['sunGlowUnits']==10
    assert abs(glow['glowOpacity']-0.42)<.0001
    assert abs(glow['captionBoxOpacity']-0.45)<.0001
assert 'if(spec.guideGlowUnits>0f && spec.glowOpacity>0f)' in view_src
assert 'val glow=spec.glowEffect' in view_src
assert 'GlowEffectConfig.parse(j.optJSONObject("glowEffect")' in visual_src
assert 'legacyGlowRadius=n("sunGlowUnits",d.sunGlowUnits)' in visual_src
assert 'legacyGlowOpacity=n("glowOpacity",d.glowOpacity)' in visual_src

# 0.2.56: one generic Canvas effect engine rather than per-widget glow code.
engine=(src/'CanvasGlowEngine.kt').read_text()
glow_cfg=(src/'GlowEffectConfig.kt').read_text()
glow_math=(src/'GlowFalloffMath.kt').read_text()
assert 'class CanvasGlowEngine' in engine
assert 'fun drawStroke(canvas:Canvas,path:Path,' in engine
assert 'fun drawRadial(canvas:Canvas,cx:Float,cy:Float,' in engine
assert 'GlowFalloffMath.alphaAt(' in engine
assert 'floatArrayOf(0f,.26f,.66f,1f)' not in view_src
assert 'sunAura.shader' not in view_src
assert 'setShadowLayer(spec.sunGlowUnits' not in view_src
assert 'data class GlowLayerConfig(' in glow_cfg
assert 'data class GlowEffectConfig(' in glow_cfg
assert 'fun parse(json:JSONObject?,fallback:GlowEffectConfig)' in glow_cfg
assert 'Unknown visual glow layer property' in glow_cfg
assert 'Glow sample count must be integer' in glow_cfg
assert 'fun alphaAt(' in glow_math
for mode in ('azimuth','brightness','elevation'):
    profile=visuals[mode]['glowEffect']
    assert set(profile)=={'primaryStroke','secondaryStroke','ambient','coreFill'}
    for key in ('primaryStroke','secondaryStroke','ambient'):
        layer=profile[key]
        assert set(layer)=={'radiusUnits','opacity','falloff','samples'}
        assert 0<=layer['opacity']<=1 and 0<=layer['radiusUnits']<=96
        assert 6<=layer['samples']<=48
    assert profile['primaryStroke']['opacity']>profile['secondaryStroke']['opacity']
    assert profile['ambient']['radiusUnits']>profile['primaryStroke']['radiusUnits']
assert 'canvas.drawPath(ringPath,pen)' in view_src
assert (root/'tools/GlowFalloffRegression.kt').is_file()

# v0.2.57 GLOBAL illumination: zero both direct and indirect IBL, not one.
render_env=(src/'RenderEnvironmentController.kt').read_text()
scene_env=(src/'SceneEnvironmentController.kt').read_text()
render_module=(src/'RenderModuleConfig.kt').read_text()
render_json=__import__('json').loads((root/'app/src/main/assets/modules/render/config.json').read_text())
assert render_json['globalBrightnessExponent']==1.5
assert 'globalBrightnessExponent' in render_module
assert 'LightingResponseCurve.intensity(globalBrightness,' in render_env
assert 'configuration.globalBrightnessExponent.toDouble()' in render_env
assert 'blackSkybox()' not in scene_env
assert 'brightnessDispatched!=brightness' in camera_light
# Uniform INNER glow fills the disk, not merely its outline.
core=(src/'GlowCoreFillConfig.kt').read_text()
core_math=(src/'GlowCoreFillMath.kt').read_text()
engine=(src/'CanvasGlowEngine.kt').read_text()
view=(src/'LightIndicatorView.kt').read_text()
glow_cfg=(src/'GlowEffectConfig.kt').read_text()
assert 'data class GlowCoreFillConfig(' in core
assert 'Unknown glow core fill property' in core
assert 'Core fill samples must be integer' in core
assert 'fun alphaAt(fraction:Float,opacity:Float,edgeSoftness:Float)' in core_math
assert 'if(t<=start)return opacity' in core_math
assert 'fun drawCoreFill(canvas:Canvas,cx:Float,cy:Float,' in engine
assert 'GlowCoreFillMath.alphaAt(fraction,fill.opacity,fill.edgeSoftness)' in engine
assert 'glowEngine.drawCoreFill(canvas,0f,0f,tint,glow.coreFill)' in view
assert 'GlowCoreFillConfig.parse(child("coreFill"),fallback.coreFill)' in glow_cfg
for mode in ('azimuth','elevation','brightness'):
    profile=visuals[mode]['glowEffect']
    assert set(profile)=={'primaryStroke','secondaryStroke','ambient','coreFill'}
    fill=profile['coreFill']
    assert set(fill)=={'radiusUnits','opacity','edgeSoftness','samples'}
    assert .1<=fill['opacity']<=1 and .03<=fill['edgeSoftness']<=.95
    assert 6<=fill['samples']<=64
assert (root/'tools/GlowCoreFillRegression.kt').is_file()

# v0.2.58: continuous perceived lighting (including at 0%) and background.
response=(src/'LightingResponseCurve.kt').read_text()
camera_cfg=(src/'CameraModuleConfig.kt').read_text()
scene_cfg=(src/'SceneModuleConfig.kt').read_text()
scene_json=__import__('json').loads((root/'app/src/main/assets/modules/scene/config.json').read_text())
camera_json=__import__('json').loads((root/'app/src/main/assets/modules/camera/config.json').read_text())
assert 'object LightingResponseCurve' in response
assert 'fun intensity(factor:Double,exponent:Double):Double' in response
assert 'fun sky(factor:Double,exponent:Double,ceiling:Double):Double' in response
assert camera_json['directBrightnessExponent']==1.5
assert scene_json['skyBrightnessExponent']==1.4
assert scene_json['skyBrightnessCeiling']==1.25
assert 'directBrightnessExponent' in camera_cfg and 'skyBrightnessExponent' in scene_cfg
assert 'skyBrightnessCeiling' in scene_cfg
assert 'if(globalBrightness==0.0)' not in scene_env
assert 'studioBackgroundDimmer.isClickable=true' not in activity
assert 'val direct=LightingResponseCurve.intensity(' in camera_light
assert 'canvas.drawPath(ringPath,pen)' in view_src  # SUN marker stays unchanged
assert (root/'tools/LightingResponseRegression.kt').is_file()

# GlassStudy 0.2.61 keeps the sun attached to the fixed azimuth orbit.
assert 'heading("РЕЖИМ ОСВЕЩЕНИЯ")' in panel
assert 'heading("SKYBOX")' not in panel
assert '"Вакуум · только солнечные лучи"' in panel
assert '"Атмосфера · солнце и рассеянный свет неба"' in panel
physics=(src/'PhysicalAtmosphereModel.kt').read_text()
renderer=(src/'RenderEnvironmentController.kt').read_text()
scene=(src/'SceneEnvironmentController.kt').read_text()
proj=(src/'LightGuideProjection.kt').read_text()
view=(src/'LightIndicatorView.kt').read_text()
assert 'enum class LightEnvironmentMode' in physics
assert 'VACUUM("vacuum")' in physics and 'ATMOSPHERE("atmosphere")' in physics
assert 'fun skyRadianceSh(' in physics
assert 'fun skyIlluminance(' in physics
assert 'fun solarTransmission(' in physics
assert 'fun sunlightRgb(' in physics
assert 'context.assets.open("envs/studio_ibl.ktx")' not in renderer
assert 'viewer.scene.indirectLight=null' in renderer
assert 'IndirectLight.Builder().radiance(3,sh)' in renderer
assert 'PhysicalAtmosphereModel.skyIlluminance(' in renderer
assert 'fun chooseLightingMode(mode:LightEnvironmentMode)' in scene
assert 'preferences.edit().putString("physicalLightingMode",mode.key).apply()' in scene
assert 'studio_ibl.ktx' not in scene
assert 'SceneSettingsPanel(this,sceneEnvironment)' in activity
assert 'state.chooseLightingMode(LightEnvironmentMode.VACUUM)' in panel
assert 'state.chooseLightingMode(LightEnvironmentMode.ATMOSPHERE)' in panel
assert 'PhysicalAtmosphereModel.backgroundRgb(lightingMode,' in scene
assert 'Skybox.Builder().color(0f,0f,0f,1f)' in scene
assert 'onGlobalBrightnessChanged(brightness,lightElevation,lightAz)' in camera_light
assert 'mgr.setIntensity(instance,(config.lightIntensity*transmitted).toFloat())' in camera_light
assert 'val transmitted=direct*directTransmission(lightElevation)' in camera_light
assert 'mgr.setColor(instance,sunRgb[0],sunRgb[1],sunRgb[2])' in camera_light
assert 'renderEnvironment.setLightingEnvironment(sceneEnvironment.lightingMode,' in activity
assert 'sceneEnvironment.setLightState(factor,elevation)' in activity
assert 'studioBackgroundDimmer' not in activity
assert 'LightDirectionMath.azimuthMarkerOnHorizon(' in proj
assert 'sunElevationRad:Double=0.0' in proj
assert 'lightElevationRad:Double' in view
assert 'lightElevation)' in view
assert 'cameraLight.lightElevation,(cameraLight.brightness*100).toFloat()' in activity
assert (root/'tools/PhysicalAtmosphereRegression.kt').is_file()
assert render['atmosphereExtinction']>0 and render['atmosphereSkyIlluminance']>0
assert scene_config['defaultLightingMode'] in ('vacuum','atmosphere')

# 0.2.63 physical camera adaptation: sky + ground-facing sunlight, no fill.
exposure_src=(root/'app/src/main/java/com/glassstudy/filament/PhysicalExposureModel.kt').read_text()
assert 'fun sensitivity(' in exposure_src
assert 'if(mode==LightEnvironmentMode.VACUUM)return baseIso.toFloat()' in exposure_src
assert 'PhysicalAtmosphereModel.skyIlluminance(' in exposure_src
assert 'PhysicalAtmosphereModel.solarTransmission(' in exposure_src
assert 'private fun updateExposure()' in render_env
assert 'renderEnvironment.configureNoonSunLux(bundle.camera.lightIntensity)' in activity
assert 'configuration.exposureShutter,iso' in render_env
assert 'PhysicalExposureModel.sensitivity(' in report_src
assert (root/'tools/PhysicalExposureRegression.kt').is_file()
