# Hollow-glass optical reference (offline only)

This is an independent numerical reference for the unresolved Android glass
problem. It does not modify or replace Filament and is not a release APK.

Run with Python 3, NumPy, SciPy and Pillow:

```sh
python3 -m unittest discover -s glassstudy/tools -p test_glass_optical_reference.py -v
python3 glassstudy/tools/glass_optical_reference.py --output /tmp/glass-reference.png
```

The image has checker floor on the left and white floor on the right. Both use
the same glass, camera, sun and exposure. The source of the shape is the actual
`catalogs/goblet.json`, sampled with the Catmull-Rom construction used by
`ParametricGoblet.sample`, at high-quality 142 rings and zero adaptation.
Intersection surfaces are analytic conical frustums between those rings. The
two tiny axial openings are closed for a well-defined dielectric volume;
this reference-only closure has not changed the Android mesh.

## What is calculated

- Rays intersect the outer and inner wall independently, and can cross glass,
  cavity air, glass again and outside air. The normal chooses the incident
  medium. No single sphere or fixed global thickness is substituted.
- Snell refraction and unpolarized dielectric Fresnel reflection use IOR 1.52.
  Total internal reflection keeps all energy in the reflected path.
- Forward sunlight paths split into reflected and transmitted branches.
  Their impact energies are accumulated into the floor's direct irradiance.
  Checker reflectance multiplies that irradiance, rather than being replaced
  by an unlit white rectangle or a transparent receiver mesh.
- Camera paths split into reflections and transmission too. A finite solar
  disc and a directional sky produce view-dependent reflected highlights.
- Photon energy counters include receiver hits, outside-receiver hits, rays
  escaping to the sky, and energy discarded at the threshold/bounce limit.
  A numerical test verifies that these sum to the launched energy.

## Limits and Android integration gate

This is a finite-resolution, geometric-optics reference. It does not include
spectral dispersion, absorption, polarization tracking, microfacet roughness,
multiple diffuse bounces, atmospheric radiative transfer, or finite-area-sun
soft caustics. The sky is a simple reference environment, not the app's
physical atmosphere. Photon density is filtered; camera paths have finite
depth and a minimum-energy threshold, whose discarded energy is reported.
The image is not proof of complete physical correctness or phone performance.

Before a new APK: port the verified intersections and dielectric kernel to a
mobile rendering pass; use the app's actual world transform, light and camera
state; compare both floors and at least two light directions against this
reference; measure GPU time/frame cost; retain wireframe/LOD/adaptivity and
all existing UI. Android build, original signing and a real-device comparison
remain mandatory. Do not attach this CPU renderer to the per-frame UI loop or
publish a source-only result as an installable fix.
