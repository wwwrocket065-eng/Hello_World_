# GlassStudy 3D LAB 0.2.64 — quadrilateral goblet rebuild

Date: 2026-10-24

## Delivered model

- User-facing object list: **Бокал**, exactly one model.
- Internally named `goblet` so an old, already-installed `wine_*.glb` patch cannot override the new model.
- Full new rotational profile: foot/underside, slender stem, bowl underside and walls, rounded lip and separate inner bowl surface.
- Surface topology is a logical grid of quadrilaterals; Filament/OpenGL necessarily triangulates each quad during rasterization.
- Debug/wireframe GLBs have **no shaded or colored fill surface**: one `KHR_materials_unlit` ribbon primitive follows quad borders and excludes triangulation diagonals. Both sides of the wireframe remain visible.
- All LODs share the same original profile, resampled at different density, so geometry silhouette is consistent while mesh density changes.

| LOD | Radial sections | Profile segments | Quad cells | Quad-grid edges |
|---|---:|---:|---:|---:|
| Low | 14 | 35 | 490 | 994 |
| Medium | 28 | 69 | 1,932 | 3,892 |
| High | 56 | 139 | 7,784 | 15,624 |
| Ultra | 112 | 279 | 31,248 | 62,608 |

## Source and verification

- Main functional source commit: `e8e48f4eda347e3f9d9cd8aa48107424c34fc99d`.
- Three GitHub Actions built from **that SHA**, all **SUCCESS**:
  - Android Build `40066327119`
  - Android Build `40066329777`
  - Filament Android `40066327142`
- GitHub unsigned build artifact `12549546482`.
- APK contains exactly eight `assets/models/goblet_*.glb`, with verified quad counts, debug unlit material and no fill primitive; independent signed-vs-unsigned check shows 40/40 identical ZIP members.
- Original-key signed release `GlassStudy_3D_LAB_v0.2.64_ORIGINAL_KEY.apk`, size **11,590,813 bytes**, SHA-256 **295c6cee50108bf03aecbc034be84493ca053ffeff7886b6c9dc1ee4c1d02336**.
- Official Android apksigner check: `Verifies`, scheme v2 `true`, signing certificate SHA-256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`, **matches original-signed 0.2.63**.
- JNI Filament libraries remain stored, 16 KiB ZIP aligned.
- Existing project patch signing and update flow preserved. New model names avoid stale legacy model overrides.
- New APK can upgrade in-place over 0.2.63 without uninstalling, based on identical package/certificate and increased versionCode (20063 to 20064).

## Pending on-device acceptance

This package has **not** been run on a user's Android phone in this audit. Check model selection (only Бокал), the four LODs including Ultra, FPS/performance on Ultra wireframe, mesh thinness, silhouette and exact resemblance to the image reference. Glass material realism remains limited by physical sky reflection and caustics; this change addresses modeling and mesh topology, not material rendering.
