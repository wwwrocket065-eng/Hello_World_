#!/usr/bin/env python3
"""Exercise the exact native backend on a real headless ES3 context.
GPU timing and fence readback here validate API use, NOT phone performance.
"""
import ctypes as C
import subprocess
import tempfile
import time
from pathlib import Path
from test_gpu_optics import context,proc,compile_program
ROOT=Path(__file__).resolve().parents[1]

def main():
    context()
    get_string=proc('glGetString',C.c_char_p,C.c_uint)
    extensions=get_string(0x1F03).decode().split()
    assert 'GL_EXT_disjoint_timer_query' in extensions,'Mesa timer extension required for this QA'
    with tempfile.TemporaryDirectory() as folder:
        library=Path(folder)/'optical_timer.so'
        subprocess.run(['g++','-std=c++17','-Wall','-Wextra','-Werror','-shared','-fPIC',
            '-DGLASSSTUDY_TIMER_TEST',str(ROOT/'app/src/main/cpp/optical_timer.cpp'),
            '-Wl,-z,max-page-size=16384','-l:libEGL.so.1','-o',str(library)],check=True)
        api=C.CDLL(str(library))
        api.glassstudyTimerInit.restype=C.c_int
        api.glassstudyTimerBegin.restype=C.c_uint
        api.glassstudyTimerResult.argtypes=[C.c_uint];api.glassstudyTimerResult.restype=C.c_int64
        api.glassstudyTimerDelete.argtypes=[C.c_uint]
        api.glassstudyTimerDisjoint.restype=C.c_bool
        bits=api.glassstudyTimerInit();assert bits>=64,bits
        vertex=(ROOT/'app/src/main/assets/optics/quad.vert').read_text()
        fragment='''#version 300 es
precision highp float;
out vec4 color;
void main(){color=vec4(.25,.5,.75,1.0);}
'''
        program=compile_program(vertex,fragment)
        proc('glUseProgram',None,C.c_uint)(program)
        vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao))
        proc('glBindVertexArray',None,C.c_uint)(vao.value)
        proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,64,64)
        draw=proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)
        read=proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
        flush=proc('glFlush',None)
        # Control image before instrumentation.
        draw(4,0,3);before=(C.c_ubyte*(64*64*4))();read(0,0,64,64,0x1908,0x1401,before)
        query=api.glassstudyTimerBegin();assert query
        draw(4,0,3);api.glassstudyTimerEnd()
        fence=proc('glFenceSync',C.c_void_p,C.c_uint,C.c_uint)(0x9117,0);assert fence
        wait=proc('glClientWaitSync',C.c_uint,C.c_void_p,C.c_uint,C.c_uint64)
        flush();deadline=time.monotonic()+10
        status=wait(fence,0,0)
        while status not in (0x911A,0x911C):
            assert status!=0x911D,'fence wait failed'
            assert time.monotonic()<deadline,'fence did not finish'
            time.sleep(.001);status=wait(fence,0,0)
        duration=api.glassstudyTimerResult(query)
        assert duration>0,duration
        assert not api.glassstudyTimerDisjoint()
        after=(C.c_ubyte*(64*64*4))();read(0,0,64,64,0x1908,0x1401,after)
        assert bytes(before)==bytes(after),'instrumentation changed pixels'
        api.glassstudyTimerDelete(query)
        proc('glDeleteSync',None,C.c_void_p)(fence)
        assert proc('glGetError',C.c_uint)()==0
        print(f'PASS: exact native backend on real ES3, {bits}-bit timer, nonblocking fence, unchanged pixels')

if __name__=='__main__':main()
