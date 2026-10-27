# Reusable GlowEffectConfig / CanvasGlowEngine (GlassStudy 0.2.56)

## Scope
One validated, serializable style shared by UI components: paths, rings,
icons, buttons and any drawing that supplies a Canvas Path/center. Physical
Filament materials use a DIFFERENT renderer backend; this class does not
magically create 3D emission or GPU post-processing.

## Signed overlay structure
Three layers with identical bounded numeric fields:

- primaryStroke: bright ring/outlines
- secondaryStroke: fainter sun rays or other fine details
- ambient: outer softly fading haze

Each layer accepts radiusUnits (0..96), opacity (0..1), falloff (1..6),
samples (integer 6..48). Higher falloff changes the intensity curve; no
four-stop hardcoded gradient. Alpha reaches exactly zero at the outer edge.

Example signed changes with the existing fast_patch tool:

    python glassstudy/tools/fast_patch.py --list-numbers

    python glassstudy/tools/fast_patch.py --version 20040 \
      --set overlay.indicatorVisuals.brightness.glowEffect.primaryStroke.radiusUnits=15 \
      --set overlay.indicatorVisuals.brightness.glowEffect.primaryStroke.opacity=0.8 \
      --set overlay.indicatorVisuals.elevation.glowEffect.secondaryStroke.opacity=0.25 \
      --set overlay.indicatorVisuals.azimuth.glowEffect.ambient.falloff=2.0 \
      --key-backup PRIVATE_BACKUP_PATH

The command is illustrative: check the current patch baseline, next version,
original patch signing identity and user-installed version before publishing.

## Architecture
- GlowEffectConfig.kt: typed backend-agnostic policy and bounded JSON parsing
- GlowFalloffMath.kt: purely mathematical monotonic smoothstep+falloff
- CanvasGlowEngine.kt: generic path feathering and radial atmosphere
- LightIndicatorView.kt: only owns the sun-specific two paths, uses the generic
  engine for all three light indicator modes
- Filament future implementation: translate the same typed style into supported
  material/emissive paths and test separately, without unsafe shader injection

Previous signed patches lacking glowEffect remain supported. Fallback styles
derive from the legacy sunGlowUnits/glowOpacity values, preserving existing
configuration. When glowEffect is present its three layers take precedence.
Navigation track glow remains a SEPARATE property and is zero by default.
Brightness remains at 100dp horizontal offset and 90 degrees max sun turn.
The opaque digits / translucent number plates and top-only light mode button
are unchanged.

## Verification
Run project/tests, signed patch test and Android Gradle CI; check the actual
signed APK original certificate and 16KiB JNI ZIP alignment. Pure math test:
    kotlinc app/src/main/java/com/glassstudy/filament/GlowFalloffMath.kt \
      tools/GlowFalloffRegression.kt -include-runtime -d /tmp/glow.jar
    java -jar /tmp/glow.jar

Verify on-device halo appearance for sun ring at 0%, 100%, 200%, and
azimuth/elevation, with guides remaining entirely unlit.
