# GlassStudy 0.2.18 - camera controls matched to v0.1.22 source

Reference examined: original /GlassStudy_v0.1.22_Source.zip (assets/engine.js).
Original app default: angle=.56 radians, elevation=.34 radians, distance=5.5, camera target y=1; vertical drag increases elevation; clamp elevation to [-.05,1.23]; zoom on pinch uses old-span/new-span with distance bounds [2.7,60].

Corrections applied to Filament MainActivity:
* initial orbit = yaw .56, pitch .34, radius5.5; lookAt target y=1.
* vertical drag sign restored; altitude restricted near object midline to stop perspective from beneath the floor.
* inverse oldPinch/delta ratio restores intuitive pinch in/out; preserve existing zoomPower signed UI override.
* cameraButton LONG PRESS resets camera to v0.1.22-style home without adding any new patchable buttons.

No changes to RSA patch manager, model loading, permissions, package identity, app key, device memory diagnostics or GLB generator. Installed signed patch v20021 is left intact. Opaque material diagnostic test remains valid.
CAUTION: this is a camera regression correction verified by compile, not a guarantee that Filament render output is physically correct. User should share a screenshot and diagnostic camera line after testing.
