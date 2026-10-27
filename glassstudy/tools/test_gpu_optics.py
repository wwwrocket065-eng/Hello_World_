#!/usr/bin/env python3
"""Compile the exact Android ES3 shader assets on headless Mesa/EGL.

Requires Mesa EGL, no Android device. This does not prove mobile performance.
"""
import ctypes as C
import ctypes.util
import math
from pathlib import Path

EGL=C.CDLL(ctypes.util.find_library('EGL'))
EGL.eglGetProcAddress.restype=C.c_void_p
EGL.eglGetProcAddress.argtypes=[C.c_char_p]


def proc(name, result, *args):
    address=EGL.eglGetProcAddress(name.encode())
    if not address:
        raise RuntimeError(f'{name} unavailable')
    return C.CFUNCTYPE(result,*args)(address)


def context():
    get_display=proc('eglGetPlatformDisplayEXT',C.c_void_p,C.c_uint,C.c_void_p,C.c_void_p)
    display=get_display(0x31DD,None,None)
    initialize=proc('eglInitialize',C.c_uint,C.c_void_p,C.POINTER(C.c_int),C.POINTER(C.c_int))
    major,minor=C.c_int(),C.c_int()
    assert initialize(display,C.byref(major),C.byref(minor))
    assert proc('eglBindAPI',C.c_uint,C.c_uint)(0x30A0)
    choose=proc('eglChooseConfig',C.c_uint,C.c_void_p,C.POINTER(C.c_int),C.POINTER(C.c_void_p),C.c_int,C.POINTER(C.c_int))
    attrs=(C.c_int*13)(0x3024,8,0x3023,8,0x3022,8,0x3033,1,0x3040,0x40,0x3038,0,0)
    config=C.c_void_p();count=C.c_int()
    assert choose(display,attrs,C.byref(config),1,C.byref(count)) and count.value
    surface=proc('eglCreatePbufferSurface',C.c_void_p,C.c_void_p,C.c_void_p,C.POINTER(C.c_int))(
        display,config,(C.c_int*5)(0x3057,64,0x3056,64,0x3038))
    ctx=proc('eglCreateContext',C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p,C.POINTER(C.c_int))(
        display,config,None,(C.c_int*3)(0x3098,3,0x3038))
    assert ctx and surface
    assert proc('eglMakeCurrent',C.c_uint,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p)(display,surface,surface,ctx)
    return display,surface,ctx


def compile_program(vertex,fragment):
    create=proc('glCreateShader',C.c_uint,C.c_uint)
    source=proc('glShaderSource',None,C.c_uint,C.c_int,C.POINTER(C.c_char_p),C.c_void_p)
    compile_=proc('glCompileShader',None,C.c_uint)
    status=proc('glGetShaderiv',None,C.c_uint,C.c_uint,C.POINTER(C.c_int))
    log=proc('glGetShaderInfoLog',None,C.c_uint,C.c_int,C.c_void_p,C.c_void_p)
    shaders=[]
    for kind,text in ((0x8B31,vertex),(0x8B30,fragment)):
        shader=create(kind);encoded=C.c_char_p(text.encode());source(shader,1,C.byref(encoded),None);compile_(shader)
        ok=C.c_int();status(shader,0x8B81,C.byref(ok))
        if not ok.value:
            message=C.create_string_buffer(10000);log(shader,len(message),None,message)
            raise AssertionError(message.value.decode())
        shaders.append(shader)
    program=proc('glCreateProgram',C.c_uint)()
    for shader in shaders:
        proc('glAttachShader',None,C.c_uint,C.c_uint)(program,shader)
    proc('glLinkProgram',None,C.c_uint)(program)
    ok=C.c_int();proc('glGetProgramiv',None,C.c_uint,C.c_uint,C.POINTER(C.c_int))(program,0x8B82,C.byref(ok))
    if not ok.value:
        message=C.create_string_buffer(10000)
        proc('glGetProgramInfoLog',None,C.c_uint,C.c_int,C.c_void_p,C.c_void_p)(program,len(message),None,message)
        raise AssertionError(message.value.decode())
    return program


def numerical_kernel(vertex,kernel):
    fragment='''#version 300 es
precision highp float;
precision highp int;
'''+kernel+'''
out vec4 color;
void main() {
    int x=int(gl_FragCoord.x);
    vec3 r,t,n;float f,hit;
    if(x==0) { dielectric(vec3(0,-1,0),vec3(0,1,0),r,t,f); color=vec4(f,t.y,0,1); }
    else if(x==1) { float a=radians(50.0);dielectric(vec3(sin(a),cos(a),0),vec3(0,1,0),r,t,f);color=vec4(f,0,0,1); }
    else if(x==2) { hit=wallHit(vec3(2,1,0),vec3(-1,0,0),n); color=vec4(hit,n.x,0,1); }
    else { hit=wallHit(vec3(0,1,0),vec3(1,0,0),n);color=vec4(hit,n.x,0,1); }
}
'''
    p=compile_program(vertex,fragment)
    proc('glUseProgram',None,C.c_uint)(p)
    gen_tex=proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))
    bind=proc('glBindTexture',None,C.c_uint,C.c_uint)
    image=proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    param=proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)
    ids=(C.c_uint*2)();gen_tex(2,ids)
    bind(0x0DE1,ids[0])
    profile=(C.c_float*12)(0,0,0,1,2,1,2,.8,.2,.8,.2,0)
    image(0x0DE1,0,0x8230,6,1,0,0x8227,0x1406,profile)
    param(0x0DE1,0x2801,0x2600);param(0x0DE1,0x2800,0x2600)
    location=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)
    proc('glUniform1i',None,C.c_int,C.c_int)(location(p,b'uProfile'),0)
    proc('glUniform1i',None,C.c_int,C.c_int)(location(p,b'uProfileCount'),6)
    proc('glUniform1f',None,C.c_int,C.c_float)(location(p,b'uIor'),1.52)
    uniform3=proc('glUniform3f',None,C.c_int,C.c_float,C.c_float,C.c_float)
    uniform3(location(p,b'uBoundsMin'),-1.01,-.01,-1.01)
    uniform3(location(p,b'uBoundsMax'),1.01,2.01,1.01)
    proc('glActiveTexture',None,C.c_uint)(0x84C1)
    bind(0x0DE1,ids[1]);image(0x0DE1,0,0x8814,4,1,0,0x1908,0x1406,None)
    param(0x0DE1,0x2801,0x2600);param(0x0DE1,0x2800,0x2600)
    fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
    proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo)
    proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,ids[1],0)
    assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
    vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao))
    proc('glBindVertexArray',None,C.c_uint)(vao)
    proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,4,1)
    proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
    output=(C.c_float*16)()
    proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)(0,0,4,1,0x1908,0x1406,output)
    assert proc('glGetError',C.c_uint)()==0
    expected=[((1.52-1)/(1.52+1))**2,-1,0,1,1,0,0,1,1,1,0,1,.8,-1,0,1]
    assert all(math.isclose(a,b,abs_tol=2e-5) for a,b in zip(output,expected)),list(output)
    print('PASS actual ES3 numerical output: Fresnel, TIR, outer and inner wall distances/normals')


def main():
    context()
    root=Path(__file__).parents[1]/'app/src/main/assets/optics'
    kernel=(root/'kernel.glsl').read_text()
    def source(name):
        return (root/name).read_text().replace('/*OPTICS_KERNEL*/',kernel)
    for v,f in [('quad.vert','path.frag'),('photon.vert','photon.frag'),('quad.vert','display.frag')]:
        compile_program(source(v),source(f))
        print(f'PASS ES3 compile+link: {v} / {f}')
    numerical_kernel(source('quad.vert'),kernel)
    print('PASS exact Android shaders; device driver/runtime still unverified')


if __name__=='__main__':
    main()
