#!/usr/bin/env python3
"""Regression: only axial holes are capped, all old surface vertices stay intact."""
from collections import Counter
import make_models as m

def test_closed_mesh():
    for quality, (sides, rings) in m.QUALITY.items():
        points, normals, indices, edges = m.lathe_mesh(sides, rings)
        assert len(points) == sides*rings+2
        assert len(indices) == (rings-1)*sides*6+sides*6
        assert len(edges) == sides*(2*rings-1)
        contour = m.smoothly_sample_profile(rings)
        for j, (y,r) in enumerate(contour):
            for k in range(sides):
                a=2*m.math.pi*k/sides
                assert points[j*sides+k] == (r*m.math.cos(a),y,r*m.math.sin(a))
        counts=Counter()
        directions=Counter()
        for i in range(0,len(indices),3):
            tri=indices[i:i+3]
            assert len(set(tri)) == 3
            a,b,c=(points[v] for v in tri)
            u=[b[q]-a[q] for q in range(3)]
            v=[c[q]-a[q] for q in range(3)]
            cross=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            assert sum(x*x for x in cross)>1e-24
            for a,b in zip(tri,tri[1:]+tri[:1]):
                key=tuple(sorted((a,b)))
                counts[key]+=1
                directions[key]+=1 if a<b else -1
            if i >= (rings-1)*sides*6:
                center=tri[0]
                assert cross[1]*normals[center][1]>0
        assert all(n==2 for n in counts.values()), quality
        assert all(n==0 for n in directions.values()), quality
        print(quality+": closed manifold, consistent winding, unchanged original vertices")
if __name__=="__main__":
    test_closed_mesh()
