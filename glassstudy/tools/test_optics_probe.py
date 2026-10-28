#!/usr/bin/env python3
"""Compile the shipped optional deterministic ray probe on a real ES3 context."""
from pathlib import Path
from test_gpu_optics import context, compile_program, proc
import ctypes as C
context()
assets=Path(__file__).resolve().parents[1]/'app/src/main/assets/optics'
fragment=(assets/'probe.frag').read_text().replace('/*OPTICS_KERNEL*/',(assets/'kernel.glsl').read_text())
program=compile_program((assets/'quad.vert').read_text(),fragment)
assert program
proc('glDeleteProgram',None,C.c_uint)(program)
assert proc('glGetError',C.c_uint)()==0
print('PASS: exact production probe links on ES3; not phone driver validation')
