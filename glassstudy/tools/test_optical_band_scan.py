#!/usr/bin/env python3
"""Dense deterministic production-kernel probes; not a diagnosis of a phone screenshot."""
import ctypes as C
import json,math
from pathlib import Path
import numpy as np
from glass_optical_reference import HollowLathe
from test_gpu_optics import context,proc,compile_program

context()
assets=Path(__file__).resolve().parents[1]/'app/src/main/assets'
fragment=(assets/'optics/probe.frag').read_text().replace('/*OPTICS_KERNEL*/',(assets/'optics/kernel.glsl').read_text())
fragment=fragment.replace('vec2 ndc=vec2(float(ray%3-1)*.4,float(ray/3-1)*.2+.25);',
    'vec2 ndc=vec2(float(ray%3-1)*.35,-.1+float(ray/3)*.7/128.0);')
p=compile_program((assets/'optics/quad.vert').read_text(),fragment)
proc('glUseProgram',None,C.c_uint)(p)
loc=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)
def i(n,v):proc('glUniform1i',None,C.c_int,C.c_int)(loc(p,n.encode()),v)
def f(n,v):proc('glUniform1f',None,C.c_int,C.c_float)(loc(p,n.encode()),v)
def v(n,*xyz):proc('glUniform3f',None,C.c_int,C.c_float,C.c_float,C.c_float)(loc(p,n.encode()),*xyz)
tex=(C.c_uint*2)();proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))(2,tex)
bind=proc('glBindTexture',None,C.c_uint,C.c_uint)
image=proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
param=proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)
points=np.asarray(HollowLathe(json.loads((assets/'catalogs/goblet.json').read_text())['profile']).profile,np.float32)
bind(0x0DE1,tex[0]);image(0x0DE1,0,0x8230,len(points),1,0,0x8227,0x1406,points.ctypes.data)
for key in (0x2801,0x2800):param(0x0DE1,key,0x2600)
i('uProfile',0);i('uProfileCount',len(points));i('uBounces',16)
f('uIor',1.52);f('uTanFov',math.tan(math.pi/8));f('uAspect',2/3)
r=float(points[:,1].max())+.001
v('uBoundsMin',-r,float(points[:,0].min())-.001,-r)
v('uBoundsMax',r,float(points[:,0].max())+.001,r)
v('uEye',7*math.cos(.34)*math.sin(.56),1+7*math.sin(.34),7*math.cos(.34)*math.cos(.56))
proc('glActiveTexture',None,C.c_uint)(0x84C1)
bind(0x0DE1,tex[1]);image(0x0DE1,0,0x8814,64,387,0,0x1908,0x1406,None)
for key in (0x2801,0x2800):param(0x0DE1,key,0x2600)
fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo)
proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,tex[1],0)
assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao));proc('glBindVertexArray',None,C.c_uint)(vao)
proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,64,387)
for analytic in (0,1):
    i('uAnalyticSurface',analytic)
    results=[]
    for floor in (12.,48.,0.):
        i('uFloorMode',int(floor>0));f('uFloorWidth',floor)
        baseline=None
        for cull in (0,1):
            i('uCullSegments',cull)
            proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
            out=np.empty((387,16,4,4),np.float32)
            proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)(0,0,64,387,0x1908,0x1406,out.ctypes.data)
            assert proc('glGetError',C.c_uint)()==0 and np.isfinite(out).all()
            # The last field explicitly records the culling switch; exclude it.
            if baseline is not None:assert np.array_equal(out[:,:,:3],baseline[:,:,:3]),(analytic,floor,'culling changed a hit')
            baseline=out.copy()
        status=out[:,:,0,3]
        results.append(status)
        print('PASS dense scan: 387 rays; analytic=%d floor=%g wall/floor/background records=%s'%(analytic,floor,[(status==k).sum().item() for k in (1,2,3)]))
    changed=np.any(results[0]!=results[1],axis=1).sum()
    print('Finite-platform terminal classification changes for %d/387 rays when floor expands; phone camera not reproduced'%changed)
