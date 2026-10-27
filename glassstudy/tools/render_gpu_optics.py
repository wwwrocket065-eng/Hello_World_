#!/usr/bin/env python3
"""Render Android's actual ES3 programs offscreen with Mesa for visual QA."""
import ctypes as C
import json
import math
import sys
from pathlib import Path
import numpy as np
from PIL import Image
from test_gpu_optics import context,proc,compile_program
from glass_optical_reference import HollowLathe


def render(output,width=128,height=192,samples=32):
    context()
    root=Path(__file__).parents[1]/'app/src/main/assets'
    kernel=(root/'optics/kernel.glsl').read_text()
    def source(name):return (root/'optics'/name).read_text().replace('/*OPTICS_KERNEL*/',kernel)
    path=compile_program(source('quad.vert'),source('path.frag'))
    photon=compile_program(source('photon.vert'),source('photon.frag'))
    display=compile_program(source('quad.vert'),source('display.frag'))
    gl={
        'use':proc('glUseProgram',None,C.c_uint),
        'loc':proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p),
        'f':proc('glUniform1f',None,C.c_int,C.c_float),
        'i':proc('glUniform1i',None,C.c_int,C.c_int),
        'v2':proc('glUniform2f',None,C.c_int,C.c_float,C.c_float),
        'v3':proc('glUniform3f',None,C.c_int,C.c_float,C.c_float,C.c_float),
        'active':proc('glActiveTexture',None,C.c_uint),
        'bind':proc('glBindTexture',None,C.c_uint,C.c_uint),
        'image':proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p),
        'param':proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int),
        'fbo':proc('glBindFramebuffer',None,C.c_uint,C.c_uint),
        'viewport':proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int),
        'draw':proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int),
    }
    def uniform(p,name,val,kind='f'):
        loc=gl['loc'](p,name.encode());gl[kind](loc,*val) if isinstance(val,tuple) else gl[kind](loc,val)
    textures=(C.c_uint*5)();proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))(5,textures)
    fbos=(C.c_uint*4)();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(4,fbos)
    profile=json.loads((root/'catalogs/goblet.json').read_text())['profile']
    points=np.array(HollowLathe(profile).profile,dtype=np.float32)
    gl['bind'](0x0DE1,textures[0]);gl['image'](0x0DE1,0,0x8230,len(points),1,0,0x8227,0x1406,points.ctypes.data)
    for key in [0x2801,0x2800]:gl['param'](0x0DE1,key,0x2600)
    def allocate(tex,fbo,w,h,internal):
        gl['bind'](0x0DE1,tex);gl['image'](0x0DE1,0,internal,w,h,0,0x1908,0x1406,None)
        for key in [0x2801,0x2800]:gl['param'](0x0DE1,key,0x2601)
        for key in [0x2802,0x2803]:gl['param'](0x0DE1,key,0x812F)
        gl['fbo'](0x8D40,fbo)
        proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,tex,0)
        assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
        proc('glClearColor',None,C.c_float,C.c_float,C.c_float,C.c_float)(0,0,0,0)
        proc('glClear',None,C.c_uint)(0x4000)
    for j in range(2):allocate(textures[j+1],fbos[j],width,height,0x881A)
    allocate(textures[3],fbos[2],128,128,0x881A)
    allocate(textures[4],fbos[3],width,height,0x881A)
    vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao))
    proc('glBindVertexArray',None,C.c_uint)(vao)
    def bind(tex,unit,p,name):
        gl['active'](0x84C0+unit);gl['bind'](0x0DE1,tex);uniform(p,name,unit,'i')
    sun=(math.cos(.9)*math.sin(1.3),math.sin(.9),math.cos(.9)*math.cos(1.3))
    def common(p):
        bind(textures[0],0,p,'uProfile')
        uniform(p,'uProfileCount',len(points),'i');uniform(p,'uIor',1.52)
        uniform(p,'uBoundsMin',(-.86,0.,-.86),'v3');uniform(p,'uBoundsMax',(.86,3.4,.86),'v3')
        uniform(p,'uSun',sun,'v3');uniform(p,'uBounces',16,'i');uniform(p,'uPhotonExtent',4.)
    gl['use'](photon);common(photon)
    uniform(photon,'uPhotonSide',384,'i');uniform(photon,'uPhotonMapSize',128,'i')
    gl['fbo'](0x8D40,fbos[2]);gl['viewport'](0,0,128,128)
    proc('glEnable',None,C.c_uint)(0x0BE2);proc('glBlendFunc',None,C.c_uint,C.c_uint)(1,1)
    gl['draw'](0,0,384*384);proc('glDisable',None,C.c_uint)(0x0BE2)
    print('GPU photon pass submitted',flush=True)
    result=[]
    for mode in [1,2]:
        for frame in range(samples):
            gl['use'](path);common(path)
            gl['fbo'](0x8D40,fbos[frame%2]);gl['viewport'](0,0,width,height)
            bind(textures[2-frame%2],1,path,'uPrevious');bind(textures[3],2,path,'uPhotons')
            uniform(path,'uResolution',(width,height),'v2');uniform(path,'uAspect',width/height)
            uniform(path,'uEye',(5.5*math.cos(.34)*math.sin(.56),1+5.5*math.sin(.34),5.5*math.cos(.34)*math.cos(.56)),'v3')
            uniform(path,'uTanFov',math.tan(math.radians(45)*.5))
            uniform(path,'uDirect',.83);uniform(path,'uSky',.12);uniform(path,'uHaze',.3)
            uniform(path,'uSunColor',(1.,.94,.87),'v3');uniform(path,'uBackdrop',(.2,.28,.38),'v3')
            for key,val in [('uFloorWidth',12.),('uTile',.6),('uDark',.006),('uLight',.96),('uWhite',.96)]:uniform(path,key,val)
            for key,val in [('uFloorMode',mode),('uFrame',frame),('uPhotonReady',1)]:uniform(path,key,val,'i')
            gl['draw'](4,0,3)
        gl['fbo'](0x8D40,fbos[3]);gl['viewport'](0,0,width,height)
        gl['use'](display);bind(textures[1+(samples-1)%2],0,display,'uImage')
        uniform(display,'uScreen',(width,height),'v2');uniform(display,'uExposure',3.)
        gl['draw'](4,0,3)
        data=np.zeros((height,width,4),np.float32)
        proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)(0,0,width,height,0x1908,0x1406,data.ctypes.data)
        assert np.isfinite(data).all() and data[:,:,:3].max()>.2
        assert proc('glGetError',C.c_uint)()==0
        result.append(np.uint8(np.clip(data[::-1,:,:3],0,1)*255))
        print(f'GPU floor {mode} rendered',flush=True)
    Image.fromarray(np.concatenate(result,axis=1)).save(output)


if __name__=='__main__':render(Path(sys.argv[1]))
