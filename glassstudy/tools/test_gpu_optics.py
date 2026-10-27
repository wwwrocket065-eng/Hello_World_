#!/usr/bin/env python3
"""Compile the exact Android ES3 shader assets on headless Mesa/EGL.

Requires Mesa EGL, no Android device. This does not prove mobile performance.
"""
import ctypes as C
import ctypes.util
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


def main():
    context()
    root=Path(__file__).parents[1]/'app/src/main/assets/optics'
    kernel=(root/'kernel.glsl').read_text()
    def source(name):
        return (root/name).read_text().replace('/*OPTICS_KERNEL*/',kernel)
    for v,f in [('quad.vert','path.frag'),('photon.vert','photon.frag'),('quad.vert','display.frag')]:
        compile_program(source(v),source(f))
        print(f'PASS ES3 compile+link: {v} / {f}')
    print('PASS exact Android shaders; device driver/runtime still unverified')


if __name__=='__main__':
    main()
