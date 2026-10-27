#!/usr/bin/env python3
"""Offline optical reference, NOT the Android renderer.

Intersects the canonical hollow lathe profile, including both wall interfaces.
Dielectric paths use Snell refraction, Fresnel reflection and total internal
reflection. Direct receiver irradiance comes from photons, multiplied into the
floor's reflectance rather than drawn as an alpha overlay. Linear radiance is
tone mapped only at output. Scene units are canonical profile units.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import numpy as np
from scipy.ndimage import gaussian_filter
from PIL import Image

EPS = 2e-5


def unit(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-15)


def dielectric(direction, normal, ior=1.52):
    """Outward normal; the sign determines entry/exit, never a guessed depth."""
    entering = np.sum(direction * normal, axis=-1) < 0
    n = np.where(entering[..., None], normal, -normal)
    cosine = np.clip(-np.sum(direction * n, axis=-1), 0, 1)
    eta = np.where(entering, 1 / ior, ior)
    k = 1 - eta * eta * (1 - cosine * cosine)
    ct = np.sqrt(np.maximum(k, 0))
    ni = np.where(entering, 1., ior)
    nt = np.where(entering, ior, 1.)
    rs = ((ni * cosine - nt * ct) / np.maximum(ni * cosine + nt * ct, 1e-15)) ** 2
    rp = ((nt * cosine - ni * ct) / np.maximum(nt * cosine + ni * ct, 1e-15)) ** 2
    fresnel = np.where(k < 0, 1., (rs + rp) * .5)
    reflected = unit(direction + 2 * cosine[..., None] * n)
    refracted = unit(eta[..., None] * direction +
                     (eta * cosine - ct)[..., None] * n)
    return reflected, refracted, fresnel, k < 0


class HollowLathe:
    def __init__(self, profile, smooth=True):
        p = np.asarray(profile, dtype=float)
        if smooth:
            # Same Catmull-Rom construction as ParametricGoblet.sample().
            dense = []
            for j in range(len(p)-1):
                a, b, c, d = p[max(j-1, 0)], p[j], p[j+1], p[min(j+2, len(p)-1)]
                for t in np.arange(16) / 16:
                    point = .5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t)
                    point[1] = max(.001, point[1])
                    dense.append(point)
            dense = np.vstack((dense, p[-1]))
            distance = np.r_[0, np.cumsum(np.linalg.norm(np.diff(dense, axis=0), axis=1))]
            target = np.linspace(0, distance[-1], 142)
            p = np.column_stack([np.interp(target, distance, dense[:, i]) for i in range(2)])
        # Close the sub-pixel axial openings for a well-defined glass volume.
        # The original silhouette and inner/outer profile remain unchanged.
        self.profile = np.vstack(([p[0, 0], 0], p, [p[-1, 0], 0]))

    def intersect(self, origin, direction):
        count = len(origin)
        nearest = np.full(count, np.inf)
        normals = np.zeros_like(origin)
        for a, b in zip(self.profile[:-1], self.profile[1:]):
            dy, dr = b - a
            if abs(dy) < 1e-10:
                denom = direction[:, 1]
                t = np.divide(a[0] - origin[:, 1], denom,
                              out=np.full(count, np.inf), where=abs(denom) > 1e-12)
                safe_t = np.where(np.isfinite(t), t, 0)
                point = origin + safe_t[:, None] * direction
                radius = np.linalg.norm(point[:, (0, 2)], axis=1)
                valid = ((t > EPS) & (t < nearest) &
                         (radius >= min(a[1], b[1]) - EPS) &
                         (radius <= max(a[1], b[1]) + EPS))
                nearest[valid] = t[valid]
                normals[valid] = (0, -np.sign(dr), 0)
                continue
            slope = dr / dy
            radius = a[1] + slope * (origin[:, 1] - a[0])
            rd = slope * direction[:, 1]
            aa = direction[:, 0] ** 2 + direction[:, 2] ** 2 - rd ** 2
            bb = 2 * (origin[:, 0] * direction[:, 0] + origin[:, 2] * direction[:, 2] - radius * rd)
            cc = origin[:, 0] ** 2 + origin[:, 2] ** 2 - radius ** 2
            discriminant = bb * bb - 4 * aa * cc
            root = np.sqrt(np.maximum(discriminant, 0))
            for sign in (-1, 1):
                t = np.divide(-bb + sign * root, 2 * aa,
                              out=np.full(count, np.inf), where=abs(aa) > 1e-12)
                linear = (abs(aa) <= 1e-12) & (abs(bb) > 1e-12)
                t[linear] = -cc[linear] / bb[linear]
                safe_t = np.where(np.isfinite(t), t, 0)
                point = origin + safe_t[:, None] * direction
                valid = ((discriminant >= 0) & (t > EPS) & (t < nearest) &
                         (point[:, 1] >= min(a[0], b[0]) - EPS) &
                         (point[:, 1] <= max(a[0], b[0]) + EPS))
                n = np.column_stack((point[:, 0], -slope *
                                     (a[1] + slope * (point[:, 1] - a[0])), point[:, 2]))
                n = unit(n) * np.sign(dy)
                nearest[valid] = t[valid]
                normals[valid] = n[valid]
        return nearest, normals


class ReferenceScene:
    def __init__(self, profile, sun=(.5, .8, .35), ior=1.52):
        self.glass = HollowLathe(profile)
        self.sun = unit(np.asarray(sun, dtype=float))
        self.ior = ior
        self.receiver = None
        self.extent = 3.5
        self.stats = {}

    def photons(self, size=320, samples=3, max_interfaces=16):
        """Uniform incident flux, accumulated after actual surface crossings."""
        rng = np.random.default_rng(42)
        resolution = size * samples
        z, x = np.mgrid[:resolution, :resolution]
        x = ((x.ravel() + rng.random(x.size)) / resolution * 2 - 1) * self.extent
        z = ((z.ravel() + rng.random(z.size)) / resolution * 2 - 1) * self.extent
        floor = np.column_stack((x, np.zeros_like(x), z))
        origin = floor + self.sun * (4.0 / self.sun[1])
        direction = np.broadcast_to(-self.sun, origin.shape).copy()
        weight = np.ones(len(origin))
        receiver = np.zeros((size, size))
        launched = len(origin)
        deposited = 0.
        escaped = 0.
        discarded = 0.
        outside = 0.
        work = [(origin, direction, weight, 0)]
        while work:
            batch, work = work, []
            origin = np.concatenate([b[0] for b in batch])
            direction = np.concatenate([b[1] for b in batch])
            weight = np.concatenate([b[2] for b in batch])
            depth = batch[0][3]
            t, normal = self.glass.intersect(origin, direction)
            tf = np.divide(-origin[:, 1], direction[:, 1],
                           out=np.full(len(origin), np.inf), where=direction[:, 1] < -1e-10)
            hit_floor = (tf > EPS) & (tf < t)
            point = origin[hit_floor] + tf[hit_floor, None] * direction[hit_floor]
            xx = np.floor((point[:, 0] / self.extent + 1) * size / 2).astype(int)
            zz = np.floor((point[:, 2] / self.extent + 1) * size / 2).astype(int)
            inside = (xx >= 0) & (xx < size) & (zz >= 0) & (zz < size)
            np.add.at(receiver, (zz[inside], xx[inside]), weight[hit_floor][inside])
            deposited += weight[hit_floor][inside].sum()
            outside += weight[hit_floor][~inside].sum()
            hit_glass = np.isfinite(t) & ~hit_floor
            escaped += weight[~hit_glass & ~hit_floor].sum()
            point = origin[hit_glass] + t[hit_glass, None] * direction[hit_glass]
            reflected, refracted, f, tir = dielectric(direction[hit_glass], normal[hit_glass], self.ior)
            for outgoing, fraction in ((reflected, f), (refracted, 1-f)):
                strength = weight[hit_glass] * fraction
                if depth >= max_interfaces:
                    discarded += strength.sum()
                    continue
                active = strength > .001
                discarded += strength[~active].sum()
                if active.any():
                    work.append((point[active]+outgoing[active]*EPS*4,
                                 outgoing[active], strength[active], depth+1))
        self.receiver = gaussian_filter(receiver / samples ** 2, .8)
        self.stats = dict(launched=launched, deposited=float(deposited),
                          escaped=float(escaped), outside=float(outside),
                          discarded=float(discarded))
        return self.receiver

    def sky(self, direction):
        h = np.clip(direction[:, 1], 0, 1)
        color = (1-h[:, None]) * np.array([.5, .62, .75]) + h[:, None] * [.14, .3, .6]
        sun = np.clip(direction @ self.sun, 0, 1)
        # Finite solar disc supplies view-dependent glints on curved glass.
        color += (sun > np.cos(np.deg2rad(.5)))[:, None] * np.array([120, 110, 90])
        return color

    def floor(self, point, checker):
        parity = (np.floor(point[:, 0] / .32) + np.floor(point[:, 2] / .32)).astype(int) % 2
        albedo = np.where((parity == 0) & checker, .025, .8)
        flux = np.ones(len(point))
        if self.receiver is not None:
            size = len(self.receiver)
            ix = np.floor((point[:, 0] / self.extent + 1) * size / 2).astype(int)
            iz = np.floor((point[:, 2] / self.extent + 1) * size / 2).astype(int)
            ok = (ix >= 0) & (ix < size) & (iz >= 0) & (iz < size)
            flux[ok] = self.receiver[iz[ok], ix[ok]]
        # Sky diffuse remains separate from direct irradiance; neither causes
        # the bright rectangular alpha replacement rejected on Android.
        return albedo[:, None] * (.13 + .85 * self.sun[1] * flux[:, None]) * np.array([1., .97, .91])

    def render(self, width=384, height=512, checker=True, max_interfaces=14):
        eye = np.array([4.1, 3.2, 6.6])
        target = np.array([0., 1.65, 0.])
        forward = unit(target-eye)
        right = unit(np.cross(forward, [0., 1., 0.]))
        up = np.cross(right, forward)
        yy, xx = np.mgrid[:height, :width]
        x = (2 * (xx.ravel()+.5) / width - 1) * width / height * np.tan(np.deg2rad(19))
        y = (1 - 2 * (yy.ravel()+.5) / height) * np.tan(np.deg2rad(19))
        direction = unit(forward + x[:, None]*right + y[:, None]*up)
        origin = np.broadcast_to(eye, direction.shape).copy()
        indices = np.arange(len(origin))
        energy = np.ones(len(origin))
        image = np.zeros_like(origin)
        work = [(origin, direction, indices, energy, 0)]
        discarded = 0.
        while work:
            batch, work = work, []
            origin = np.concatenate([b[0] for b in batch])
            direction = np.concatenate([b[1] for b in batch])
            indices = np.concatenate([b[2] for b in batch])
            energy = np.concatenate([b[3] for b in batch])
            depth = batch[0][4]
            t, normal = self.glass.intersect(origin, direction)
            tf = np.divide(-origin[:, 1], direction[:, 1], out=np.full(len(origin), np.inf),
                           where=direction[:, 1] < -1e-10)
            floor = (tf > EPS) & (tf < t)
            if floor.any():
                color = self.floor(origin[floor] + tf[floor, None]*direction[floor], checker)
                np.add.at(image, indices[floor], energy[floor, None]*color)
            sky = ~np.isfinite(t) & ~floor
            if sky.any():
                np.add.at(image, indices[sky], energy[sky, None]*self.sky(direction[sky]))
            hit = np.isfinite(t) & ~floor
            if not hit.any():
                continue
            point = origin[hit] + t[hit, None]*direction[hit]
            refl, refr, f, tir = dielectric(direction[hit], normal[hit], self.ior)
            for outgoing, fraction in ((refl, f), (refr, 1-f)):
                strength = energy[hit] * fraction
                active = strength > .001
                if depth >= max_interfaces:
                    discarded += strength.sum()
                    continue
                discarded += strength[~active].sum()
                if active.any():
                    work.append((point[active]+outgoing[active]*EPS*4, outgoing[active],
                                 indices[hit][active], strength[active], depth+1))
        self.stats['camera_truncated_energy_per_pixel'] = discarded / len(image)
        color = image / (1 + image)  # fixed exposure, no contrast painted onto glass
        color = np.where(color <= .0031308, 12.92*color, 1.055*color**(1/2.4)-.055)
        return Image.fromarray(np.uint8(np.clip(color.reshape(height, width, 3), 0, 1)*255))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--width', type=int, default=384)
    parser.add_argument('--height', type=int, default=512)
    parser.add_argument('--supersample', type=int, default=2)
    args = parser.parse_args()
    profile_path = Path(__file__).parents[1] / 'app/src/main/assets/catalogs/goblet.json'
    scene = ReferenceScene(json.loads(profile_path.read_text())['profile'])
    scene.photons()
    print('Receiver photon map complete', flush=True)
    checker = scene.render(args.width*args.supersample, args.height*args.supersample, True)
    print('Checker-floor comparison complete', flush=True)
    white = scene.render(args.width*args.supersample, args.height*args.supersample, False)
    checker = checker.resize((args.width, args.height), Image.Resampling.LANCZOS)
    white = white.resize((args.width, args.height), Image.Resampling.LANCZOS)
    result = Image.new('RGB', (args.width*2, args.height))
    result.paste(checker, (0, 0)); result.paste(white, (args.width, 0))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    result.save(args.output)
    print(json.dumps(scene.stats, indent=2))


if __name__ == '__main__':
    main()
