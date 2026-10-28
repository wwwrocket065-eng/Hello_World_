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
    profile=json.loads((ROOT/'app/src/main/assets/catalogs/goblet.json').read_text())['profile']
    coarse=np.array(HollowLathe(profile).profile,np.float32)
    # Full282-point stress case, retaining axial caps and inner/outer contour.
    arc=np.r_[0,np.cumsum(np.linalg.norm(np.diff(coarse[1:-1],axis=0),axis=1))]
    t=np.linspace(0,arc[-1],280)
    points=np.vstack((coarse[:1],np.column_stack([np.interp(t,arc,coarse[1:-1,i]) for i in range(2)]),coarse[-1:])).astype(np.float32)
    origins=rng.uniform([-4,-1,-4],[4,5,4],(n,3))
    origins[n//2:]=rng.uniform([-.9,0,-.9],[.9,3.4,.9],(n//2,3))
    targets=rng.uniform([-.8,0,-.8],[.8,3.4,.8],(n,3))
    directions=targets-origins
    directions[n//2:]=rng.normal(size=(n//2,3))
    # Axis-parallel and near-axis paths, cap/rim and inside-volume rays.
    for axis in range(3):directions[axis*32:(axis+1)*32]=np.eye(3)[axis]
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
 float t=wallHit(texelFetch(uOrigins,p,0).xyz,texelFetch(uDirections,p,0).xyz,n);
 color=t<FAR ? vec4(t,n) : vec4(FAR,0,0,0);}
'''
    old=(ROOT/'tools/fixtures/optics_kernel_0_2_83.glsl').read_text();new=(ROOT/'app/src/main/assets/optics/kernel.glsl').read_text()
    programs=[compile_program(vertex,'#version 300 es\nprecision highp float;\nprecision highp int;\n'+k+tail) for k in [old,new]]
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
    for smooth in [0,1]:
        reference,base=render(programs[0],smooth,0)
        for accelerated in [0,1]:
            actual,duration=render(programs[1],smooth,accelerated)
            hits=reference[:,:,0]<1e19
            assert np.array_equal(hits,actual[:,:,0]<1e19),'Hit/miss changed'
            delta=np.abs(reference[hits]-actual[hits])
            if np.max(delta)>=2e-4:
                flat=np.max(np.abs(reference-actual),axis=2).ravel()
                for index in np.argsort(flat)[-8:]:
                    y,x=divmod(int(index),w)
                    print('DIFF',index,'origin',origins[y,x,:3],'direction',directions[y,x,:3], 'old',reference[y,x], 'new',actual[y,x],flush=True)
                np.savez('/workspace/scratch/33ed4fedc7d1/optics/bvh_difference.npz',points=points,origins=origins,directions=directions,reference=reference,actual=actual)
            assert np.max(delta)<2e-4,(smooth,accelerated,float(np.max(delta)))
            print(f'PASS {n} rays, smooth={smooth}, height-cull={accelerated}: max difference={delta.max():.3g}; Mesa median {duration*1000:.2f}ms vs old {base*1000:.2f}ms',flush=True)
    assert proc('glGetError',C.c_uint)()==0

if __name__=='__main__':main()
