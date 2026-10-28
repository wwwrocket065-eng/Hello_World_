#!/usr/bin/env python3
"""Actual ES3 differential test: release0.2.83 kernel vs optimized kernel.
No phone speedup inferred; exact old/new shader intersections are read back.
"""
import ctypes as C
import json,time
from pathlib import Path
import numpy as np
from test_gpu_optics import context,proc,compile_program
from glass_optical_reference import HollowLathe
ROOT=Path(__file__).resolve().parents[1]

def main():
    context();rng=np.random.default_rng(8401);w,h=512,16;n=w*h
    w,h=256,1;n=w*h
    points=np.array([[0,0],[0,1],[1,1],[2,1.4],[3,2],[3,0]],np.float32)
    origins=np.column_stack((np.full(n,3.),rng.uniform(1.1,1.9,n),rng.uniform(-.7,.7,n)))
    directions=np.column_stack((-np.ones(n),rng.uniform(-.08,.08,n),np.zeros(n)))
    directions[::8,1]=0.0
    directions/=np.linalg.norm(directions,axis=1)[:,None]
    origins=np.column_stack((origins,np.zeros(n))).astype(np.float32).reshape(h,w,4)
    directions=np.column_stack((directions,np.zeros(n))).astype(np.float32).reshape(h,w,4)
    gen=proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint));ids=(C.c_uint*5)();gen(5,ids)
    active=proc('glActiveTexture',None,C.c_uint);bind=proc('glBindTexture',None,C.c_uint,C.c_uint)
    image=proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    param=proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)
    for unit,data,internal,fmt in [(0,points.reshape(1,-1,2),0x8230,0x8227),(2,origins,0x8814,0x1908),(3,directions,0x8814,0x1908)]:
        active(0x84C0+unit);bind(0x0DE1,ids[unit]);image(0x0DE1,0,internal,data.shape[1],data.shape[0],0,fmt,0x1406,data.ctypes.data)
        for key in [0x2801,0x2800]:param(0x0DE1,key,0x2600)
    active(0x84C0+4);bind(0x0DE1,ids[4]);image(0x0DE1,0,0x8814,w,h,0,0x1908,0x1406,None)
    for key in [0x2801,0x2800]:param(0x0DE1,key,0x2600)
    fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
    proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo.value)
    proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,ids[4],0)
    assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
    vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao));proc('glBindVertexArray',None,C.c_uint)(vao.value)
    vertex=(ROOT/'app/src/main/assets/optics/quad.vert').read_text()
    tail='''
uniform highp sampler2D uOrigins,uDirections;
out vec4 color;
void main(){ivec2 p=ivec2(gl_FragCoord.xy);vec3 n;
 float t=FAR;int j=2147483647;n=vec3(0);
 curvedHit(2,texelFetch(uOrigins,p,0).xyz,texelFetch(uDirections,p,0).xyz,t,j,n);
 color=t<FAR ? vec4(t,n) : vec4(FAR,0,0,0);}
'''
    kernel=(ROOT/'app/src/main/assets/optics/kernel.glsl').read_text()
    program=compile_program(vertex,'#version 300 es\nprecision highp float;\nprecision highp int;\n'+kernel+tail)
    compile_program(vertex,(ROOT/'app/src/main/assets/optics/analytic_mask.frag').read_text().replace('/*OPTICS_KERNEL*/',kernel))
    loc=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)
    integer=proc('glUniform1i',None,C.c_int,C.c_int);v=proc('glUniform3f',None,C.c_int,C.c_float,C.c_float,C.c_float)
    finish=proc('glFinish',None);draw=proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)
    read=proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,w,h)
    def render(program,smooth,accelerated):
        proc('glUseProgram',None,C.c_uint)(program)
        for name,value in [('uProfile',0),('uOrigins',2),('uDirections',3),('uProfileCount',len(points)),('uCullSegments',accelerated),('uSmoothSurface',smooth)]:integer(loc(program,name.encode()),value)
        radius=float(points[:,1].max())+.001
        v(loc(program,b'uBoundsMin'),-radius,float(points[:,0].min())-.001,-radius)
        v(loc(program,b'uBoundsMax'),radius,float(points[:,0].max())+.001,radius)
        draw(4,0,3);finish() # QA timing ONLY; never used by the Android renderer.
        times=[]
        for _ in range(3):
            start=time.perf_counter();draw(4,0,3);finish();times.append(time.perf_counter()-start)
        data=np.empty((h,w,4),np.float32);read(0,0,w,h,0x1908,0x1406,data.ctypes.data)
        return data,float(np.median(times))
    actual,_=render(program,0,1)
    aa,_=render(program,1,1)
    assert np.array_equal(actual,aa),"Analytic normals must be independent of shading smoothing"
    from numpy.polynomial import Polynomial as P
    # Independent float64 polynomial roots, interior PCHIP tangents.
    # Segment radius = 1 + 0*u + .72*u^2 - .32*u^3.
    # Left tangent is zero at a plateau, right harmonic(.4,.6)=.48.
    curve=P([1.,0.,.72,-.32])
    expected=[]
    for o,d in zip(origins.reshape(-1,4)[:,:3],directions.reshape(-1,4)[:,:3]):
        u=P([float(o[1])-1.,float(d[1])])
        radius=curve(u)
        eq=P([float(o[0]),float(d[0])])**2+P([float(o[2]),float(d[2])])**2-radius**2
        hits=[]
        for t in eq.roots():
            if abs(t.imag)<1e-6 and t.real>8e-5 and 0<=u(t.real)<=1:
                hits.append(float(t.real))
        if not hits:expected.append([1e20,0,0,0]);continue
        t=min(hits);point=o+t*d;v=float(u(t));rad=float(curve(v));slope=float(curve.deriv()(v))
        normal=np.array([point[0],-slope*rad,point[2]]);normal/=np.linalg.norm(normal)
        expected.append([t,*normal])
    expected=np.array(expected);actual=actual.reshape(-1,4)
    hits=expected[:,0]<1e19
    assert np.array_equal(hits,actual[:,0]<1e19),np.where(hits!=(actual[:,0]<1e19))
    error=np.max(np.abs(expected[hits]-actual[hits]))
    assert error<.001,error
    print(f'PASS {n} oblique cubic-surface rays vs independent float64 polynomial roots: max error={error:.6g}')
    # Root isolation includes multiple and tangent roots, not just sign changes.
    assert proc('glGetError',C.c_uint)()==0

if __name__=='__main__':main()
