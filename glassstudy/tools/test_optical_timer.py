#!/usr/bin/env python3
"""Compile/run the SAME native timer backend with a fake EGL dispatch.
Checks capability gating, 64-bit results and that pending queries never block.
No Android device performance is asserted.
"""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST=r'''
#define GLASSSTUDY_TIMER_TEST
#include "optical_timer.cpp"
#include <cassert>
#include <iostream>
static int bits=64, disjoint=0, resultReads=0;
static unsigned available=0;
static bool omit64=false;
void gen(int,unsigned* p) { *p=42; }
void del(int,const unsigned*) {}
void begin(unsigned,unsigned) {}
void end(unsigned) {}
void query(unsigned,unsigned,int* p) { *p=bits; }
void get(unsigned,unsigned,unsigned* p) { *p=available; }
void get64(unsigned,unsigned,uint64_t* p) { resultReads++;*p=5000000001ULL; }
void integer(unsigned,int* p) { *p=disjoint;disjoint=0; }
extern "C" void (*eglGetProcAddress(const char* name))() {
#define MAP(n,f) if(!std::strcmp(name,n))return reinterpret_cast<void(*)()>(f)
    MAP("glGenQueriesEXT",gen);MAP("glDeleteQueriesEXT",del);
    MAP("glBeginQueryEXT",begin);MAP("glEndQueryEXT",end);
    MAP("glGetQueryivEXT",query);MAP("glGetQueryObjectuivEXT",get);
    if(!omit64) { MAP("glGetQueryObjectui64vEXT",get64); }
    MAP("glGetIntegerv",integer);
    return nullptr;
}
int main() {
    assert(glassstudyTimerInit()==64);
    assert(glassstudyTimerBegin()==42);
    glassstudyTimerEnd();
    assert(glassstudyTimerResult(42)==-1 && resultReads==0);
    available=1;
    assert(glassstudyTimerResult(42)==5000000001LL && resultReads==1);
    disjoint=1;assert(glassstudyTimerDisjoint());assert(!glassstudyTimerDisjoint());
    glassstudyTimerDelete(42);
    bits=32;assert(glassstudyTimerInit()==32 && glassstudyTimerBegin()==0);
    bits=64;omit64=true;assert(glassstudyTimerInit()==0 && glassstudyTimerBegin()==0);
    std::cout << "PASS: native capability fallback, no blocking result, >32-bit duration, disjoint\n";
}
'''
with tempfile.TemporaryDirectory() as folder:
    source=Path(folder)/'timer_test.cpp';source.write_text(TEST)
    executable=Path(folder)/'timer_test'
    subprocess.run(['g++','-std=c++17','-Wall','-Wextra','-Werror',
        '-I'+str(ROOT/'app/src/main/cpp'),str(source),'-o',str(executable)],check=True)
    subprocess.run([str(executable)],check=True)
