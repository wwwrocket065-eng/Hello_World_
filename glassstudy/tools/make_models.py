#!/usr/bin/env python3
"""Single realistic procedural goblet, four quad-grid LODs.

The surface is logically a grid of quads around one smooth closed lathe profile.
GLB surface uses triangles because Filament/OpenGL rasterize triangles; debug GLB
contains ONLY unlit thin ribbon strokes along the *quad* edges, never diagonals
or a shaded mesh beneath the grid.
"""
from __future__ import annotations
import json
import math
import pathlib
import struct

ROOT = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/models"
QUALITY = {
    "low": (14, 38),
    "medium": (28, 74),
    "high": (56, 142),
    "ultra": (112, 280),
}

# Python export/debug and Android runtime use ONE physical meridian.
# The only editable source of the shape is the signed catalogs/goblet.json,
# which ParametricGoblet.kt also reads. Never independently sculpt a Python LOD.
CATALOG = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/catalogs/goblet.json"
CONFIG = json.loads(CATALOG.read_text(encoding="utf-8"))
PROFILE = [tuple(point) for point in CONFIG["profile"]]
WIRE_COLOR_RGBA = (0.0, 0.0, 0.0, 1.0)

def check_profile_proportions():
    """Reject massive bowl bottoms, thick walls, deformed stems and open mouths."""
    assert len(PROFILE) in range(24,81)
    rim_index=max(range(len(PROFILE)),key=lambda i:PROFILE[i][0])
    outer=PROFILE[:rim_index+1]
    inside=list(reversed(PROFILE[rim_index+1:]))
    def radius_at_height(points, y):
        for (a,ra),(b,rb) in zip(points,points[1:]):
            if a<=y<=b:
                return ra+(rb-ra)*(y-a)/(b-a)
        raise AssertionError("Missing hollow wine-glass wall at "+str(y))
    for y in (1.85,2.10,2.40,2.70,3.10):
        wall=radius_at_height(outer,y)-radius_at_height(inside,y)
        assert .018 < wall < .085,(y,wall)
    # Bottom strength is intentional, but it must never become a glass brick.
    floor_y=PROFILE[-1][0]
    support_y=next(y for y,r in PROFILE if y>=1.4 and r>=.1)
    assert .07 < floor_y-support_y < .17
    assert max(r for y,r in PROFILE if y<.15) >= .65
    assert .045 < min(r for y,r in PROFILE if .3<y<1.4) < .085
    assert PROFILE[-1][1] < .01
    widest=max(r for y,r in PROFILE if 2.3<y<3.1)
    assert PROFILE[rim_index][1] < widest*.9

def smoothly_sample_profile(count):
    """Cubic Catmull-Rom sampled uniformly in cross-section arc length."""
    dense = []
    def segment_at(j, t):
        a = PROFILE[max(0, j - 1)]
        b = PROFILE[j]
        c = PROFILE[j + 1]
        d = PROFILE[min(len(PROFILE) - 1, j + 2)]
        def cubic(k):
            return .5 * ((2 * b[k]) + (-a[k] + c[k]) * t +
                (2*a[k] - 5*b[k] + 4*c[k] - d[k]) * t*t +
                (-a[k] + 3*b[k] - 3*c[k] + d[k]) * t*t*t)
        return (cubic(0), max(.007, cubic(1)))
    for j in range(len(PROFILE) - 1):
        for sub in range(20):
            dense.append(segment_at(j, sub / 20))
    dense.append(PROFILE[-1])
    lengths = [0.0]
    for a, b in zip(dense, dense[1:]):
        lengths.append(lengths[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    total = lengths[-1]
    out = []
    cursor = 0
    for i in range(count):
        arclen = total * i / (count - 1)
        while cursor + 1 < len(lengths) - 1 and lengths[cursor + 1] < arclen:
            cursor += 1
        length = lengths[cursor + 1] - lengths[cursor]
        t = (arclen - lengths[cursor]) / max(length, 1e-9)
        a, b = dense[cursor], dense[cursor+1]
        out.append((a[0]*(1-t)+b[0]*t, a[1]*(1-t)+b[1]*t))
    return out

def unit(x, y, z):
    length = max(1e-10, math.sqrt(x*x+y*y+z*z))
    return (x/length, y/length, z/length)

def lathe_mesh(sides, rings):
    contour=smoothly_sample_profile(rings)
    positions=[]
    normals=[]
    for j, (y, radius) in enumerate(contour):
        p=contour[max(j-1,0)]
        n=contour[min(j+1,len(contour)-1)]
        delta_y=n[0]-p[0]
        delta_r=n[1]-p[1]
        for k in range(sides):
            a=2*math.pi*k/sides
            co, si=math.cos(a),math.sin(a)
            positions.append((radius*co,y,radius*si))
            normals.append(unit(delta_y*co,-delta_r,delta_y*si))
    triangles=[]
    quad_edge_pairs=set()
    for j in range(rings-1):
        for k in range(sides):
            kn=(k+1)%sides
            a=j*sides+k
            b=j*sides+kn
            c=(j+1)*sides+k
            d=(j+1)*sides+kn
            # GPU triangles from one logical quad (NO triangular debug edges).
            triangles.extend((a,c,b,b,c,d))
            for pair in ((a,b),(a,c)):
                quad_edge_pairs.add(tuple(sorted(pair)))
            if j==rings-2:
                quad_edge_pairs.add(tuple(sorted((c,d))))
    assert len(quad_edge_pairs)==sides*(2*rings-1)
    return positions,normals,triangles,sorted(quad_edge_pairs)

def wireframe_primitive(points,normals,quad_edge_pairs,side_count,profile_count):
    """One narrow, camera-neutral, offset surface ribbon per QUAD grid edge.

    GLTF LINES mode has uneven gltfio hardware support: thin, double-sided,
    KHR_materials_unlit triangle ribbons render reliably with no mesh fill.
    """
    out_p=[]
    out_n=[]
    indices=[]
    thickness=CONFIG["wireThickness"]
    normal_offset=CONFIG["wireOffset"]
    for i,j in quad_edge_pairs:
        a,b=points[i],points[j]
        na,nb=normals[i],normals[j]
        d=unit(*(b[q]-a[q] for q in range(3)))
        av=unit(*(na[q]+nb[q] for q in range(3)))
        tangent=unit(d[1]*av[2]-d[2]*av[1],
                     d[2]*av[0]-d[0]*av[2],
                     d[0]*av[1]-d[1]*av[0])
        if (not all(math.isfinite(v) for v in tangent)) or max(abs(v) for v in tangent)<1e-8:
            continue
        base=len(out_p)
        for pt,norm in ((a,na),(b,nb)):
            for sign in (-1,1):
                out_p.append(tuple(pt[q]+norm[q]*normal_offset+
                                   tangent[q]*thickness*sign for q in range(3)))
                out_n.append(norm)
        indices.extend((base,base+2,base+1,base+1,base+2,base+3))
    return out_p,out_n,indices

def make_glb(sides,rings,debug):
    surface_p,surface_n,surface_idx,quad_edge_pairs=lathe_mesh(sides,rings)
    if debug:
        positions,normals,indices=wireframe_primitive(
            surface_p,surface_n,quad_edge_pairs,sides,rings)
    else:
        positions,normals,indices=surface_p,surface_n,surface_idx
    buffer=bytearray()
    views=[]
    accessors=[]
    def accessor(data,component_type,components,target):
        while len(buffer)%4:buffer.append(0)
        offset=len(buffer)
        fmt="<"+("f" if component_type==5126 else "I")*components
        for value in data:
            if components==1:value=(value,)
            buffer.extend(struct.pack(fmt,*value))
        v=len(views)
        views.append(dict(buffer=0,byteOffset=offset,
                          byteLength=len(buffer)-offset,target=target))
        a=dict(bufferView=v,componentType=component_type,count=len(data),
               type="VEC3" if components==3 else "SCALAR")
        if component_type==5126:
            a["min"]=[min(p[q] for p in data) for q in range(components)]
            a["max"]=[max(p[q] for p in data) for q in range(components)]
        accessors.append(a)
        return len(accessors)-1
    p=accessor(positions,5126,3,34962)
    n=accessor(normals,5126,3,34962)
    indices_accessor=accessor(indices,5125,1,34963)
    if debug:
        material=dict(name="Quad wireframe only (no fill)",doubleSided=True,
            pbrMetallicRoughness=dict(baseColorFactor=list(WIRE_COLOR_RGBA),
                                    metallicFactor=0,roughnessFactor=1),
            extensions={"KHR_materials_unlit":{}})
        extensions=["KHR_materials_unlit"]
    else:
        material=dict(name="Realistic wine glass",doubleSided=True,
            pbrMetallicRoughness=dict(baseColorFactor=[.99,1,1,1],
                                    metallicFactor=0,roughnessFactor=.028),
            extensions={"KHR_materials_transmission":{"transmissionFactor":1.0},
                        "KHR_materials_ior":{"ior":1.52},
                        "KHR_materials_volume":{"thicknessFactor":.05,
                            "attenuationDistance":12.0,
                            "attenuationColor":[.985,.995,1.0]}})
        extensions=list(material["extensions"])
    gltf={
        "asset":{"version":"2.0","generator":"GlassStudy canonical catalog goblet mesh exporter"},
        "extensionsUsed":extensions,
        "scene":0,
        "scenes":[{"nodes":[0]}],
        "nodes":[{"mesh":0}],
        "meshes":[{"name":"Quad grid" if debug else "Goblet",
                   "primitives":[{"attributes":{"POSITION":p,"NORMAL":n},
                                  "indices":indices_accessor,"material":0,"mode":4}]}],
        "materials":[material],
        "bufferViews":views,
        "accessors":accessors,
        "buffers":[{"byteLength":len(buffer)}],
        "extras":{"quadGrid":{"radialSegments":sides,
                              "profileSegments":rings-1,
                              "quads":sides*(rings-1),
                              "visibleEdges":len(quad_edge_pairs),
                              "filled":not debug}}
    }
    raw=json.dumps(gltf,separators=(",",":")).encode("utf-8")
    raw+=b" " * ((-len(raw))%4)
    buffer+=b"\0"*((-len(buffer))%4)
    chunk=struct.pack("<I4s",len(raw),b"JSON")+raw
    chunk+=struct.pack("<I4s",len(buffer),b"BIN\0")+buffer
    return struct.pack("<4sII",b"glTF",2,12+len(chunk))+chunk

def main():
    check_profile_proportions()
    ROOT.mkdir(parents=True,exist_ok=True)
    for old in ROOT.glob("*.glb"):
        old.unlink()
    for model_id in ("goblet",):
        for quality in ("low","medium","high","ultra"):
            sides,rings=QUALITY[quality]
            for debug in (False,True):
                raw=make_glb(sides,rings,debug)
                assert len(raw)<16*1024*1024, (quality,debug,len(raw))
                path=ROOT/f'{model_id}_{quality}{"_debug" if debug else ""}.glb'
                path.write_bytes(raw)
                print(path.name, len(raw), "bytes", sides*(rings-1),"quads")
    assert len(list(ROOT.glob("*.glb")))==8

if __name__=="__main__":
    main()
