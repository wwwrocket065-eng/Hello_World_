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
    else if(x==3) { hit=wallHit(vec3(0,1,0),vec3(1,0,0),n);color=vec4(hit,n.x,0,1); }
    else if(x==4) { color=vec4(limitedRadiance(vec3(16000,8000,4000),8.0),1); }
    else { color=vec4(limitedRadiance(vec3(uintBitsToFloat(0x7f800000u)),8.0),1); }
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
    bind(0x0DE1,ids[1]);image(0x0DE1,0,0x8814,6,1,0,0x1908,0x1406,None)
    param(0x0DE1,0x2801,0x2600);param(0x0DE1,0x2800,0x2600)
    fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
    proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo)
    proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,ids[1],0)
    assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
    vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao))
    proc('glBindVertexArray',None,C.c_uint)(vao)
    proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,6,1)
    proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
    output=(C.c_float*24)()
    proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)(0,0,6,1,0x1908,0x1406,output)
    assert proc('glGetError',C.c_uint)()==0
    expected=[((1.52-1)/(1.52+1))**2,-1,0,1,1,0,0,1,1,1,0,1,.8,-1,0,1,8,4,2,1,0,0,0,1]
    assert all(math.isclose(a,b,abs_tol=2e-5) for a,b in zip(output,expected)),list(output)
    print('PASS actual ES3 output: Fresnel, TIR, both walls, radiance outlier cap and nonfinite rejection')


def smoothing_toggle(vertex,fragment):
    """Read back the actual display shader: off/on/off without changing input."""
    size=16
    p=compile_program(vertex,fragment)
    proc('glUseProgram',None,C.c_uint)(p)
    ids=(C.c_uint*2)();proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))(2,ids)
    active=proc('glActiveTexture',None,C.c_uint)
    bind=proc('glBindTexture',None,C.c_uint,C.c_uint)
    image=proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    param=proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)
    active(0x84C0);bind(0x0DE1,ids[0])
    values=[]
    for y in range(size):
        for x in range(size):
            level=1. if x>y else 0.
            values.extend([level,level,level,1.])
    data=(C.c_float*len(values))(*values)
    image(0x0DE1,0,0x881A,size,size,0,0x1908,0x1406,data)
    for key in [0x2801,0x2800]:param(0x0DE1,key,0x2601)
    for key in [0x2802,0x2803]:param(0x0DE1,key,0x812F)
    active(0x84C1);bind(0x0DE1,ids[1])
    image(0x0DE1,0,0x8814,size,size,0,0x1908,0x1406,None)
    param(0x0DE1,0x2801,0x2600);param(0x0DE1,0x2800,0x2600)
    fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
    proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo)
    proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,ids[1],0)
    assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
    loc=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)
    uniform=proc('glUniform1i',None,C.c_int,C.c_int)
    uniform(loc(p,b'uImage'),0)
    proc('glUniform1f',None,C.c_int,C.c_float)(loc(p,b'uExposure'),1.)
    proc('glUniform2f',None,C.c_int,C.c_float,C.c_float)(loc(p,b'uScreen'),size,size)
    proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,size,size)
    read=proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    outputs=[]
    for enabled in [0,1,0]:
        uniform(loc(p,b'uFxaa'),enabled)
        proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
        output=(C.c_float*(size*size*4))();read(0,0,size,size,0x1908,0x1406,output)
        outputs.append(list(output))
    assert proc('glGetError',C.c_uint)()==0
    off,on,again=outputs
    assert max(abs(a-b) for a,b in zip(off,on))>.02,'AA toggle has no rendered effect'
    assert off==again,'AA off does not restore exact original image'
    # Flat regions stay unchanged: this is an edge filter, not global blur.
    for x,y in [(2,13),(13,2)]:
        k=(y*size+x)*4
        assert max(abs(a-b) for a,b in zip(off[k:k+4],on[k:k+4]))<1e-5
    print('PASS actual FXAA off/on/off readback: edges change, flat fields unchanged, off restores input')


def resize_and_tiles(vertex):
    """Check the half-float preview blit and partial-tile preservation on GL."""
    ids=(C.c_uint*2)();fbos=(C.c_uint*2)()
    proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))(2,ids)
    proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(2,fbos)
    bind=proc('glBindFramebuffer',None,C.c_uint,C.c_uint)
    viewport=proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)
    for j,size in enumerate([4,8]):
        proc('glBindTexture',None,C.c_uint,C.c_uint)(0x0DE1,ids[j])
        proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)(0x0DE1,0,0x881A,size,size,0,0x1908,0x1406,None)
        for key in [0x2801,0x2800]:proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)(0x0DE1,key,0x2601)
        bind(0x8D40,fbos[j])
        proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,ids[j],0)
        assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
    frag='''#version 300 es
precision highp float;
uniform int phase;
out vec4 color;
void main() { color=phase==0 ? vec4(4,2,1,1) : vec4(.25*gl_FragCoord.xy,2,1); }
'''
    p=compile_program(vertex,frag);proc('glUseProgram',None,C.c_uint)(p)
    loc=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)(p,b'phase')
    uniform=proc('glUniform1i',None,C.c_int,C.c_int)
    draw=proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)
    uniform(loc,0);bind(0x8D40,fbos[0]);viewport(0,0,4,4);draw(4,0,3)
    bind(0x8CA8,fbos[0]);bind(0x8CA9,fbos[1])
    proc('glBlitFramebuffer',None,*([C.c_int]*8),C.c_uint,C.c_uint)(0,0,4,4,0,0,8,8,0x4000,0x2601)
    bind(0x8D40,fbos[1]);viewport(0,0,8,8)
    read=proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
    seeded=(C.c_float*256)();read(0,0,8,8,0x1908,0x1406,seeded)
    assert all(abs(seeded[k]-[4,2,1,1][k%4])<1e-5 for k in range(256)),'HDR seed lost/clamped'
    uniform(loc,1);proc('glEnable',None,C.c_uint)(0x0C11)
    scissor=proc('glScissor',None,C.c_int,C.c_int,C.c_int,C.c_int)
    scissor(0,0,8,3);draw(4,0,3)
    partial=(C.c_float*256)();read(0,0,8,8,0x1908,0x1406,partial)
    assert partial[(0*8+0)*4+2]==2 and partial[(7*8+0)*4+2]==1,'Unfinished tiles not preserved'
    for row in [3,6]:scissor(0,row,8,min(3,8-row));draw(4,0,3)
    proc('glDisable',None,C.c_uint)(0x0C11)
    final=(C.c_float*256)();read(0,0,8,8,0x1908,0x1406,final)
    for y in range(8):
        for x in range(8):
            k=(y*8+x)*4
            assert abs(final[k]-.25*(x+.5))<1e-5 and abs(final[k+1]-.25*(y+.5))<1e-5
            assert final[k+2]==2
    assert proc('glGetError',C.c_uint)()==0
    print('PASS RGBA16F resize preserves HDR; partial tiles preserve preview and cover every row')


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
    smoothing_toggle(source('quad.vert'),source('display.frag'))
    resize_and_tiles(source('quad.vert'))
    print('PASS exact Android shaders; device driver/runtime still unverified')


if __name__=='__main__':
    main()
