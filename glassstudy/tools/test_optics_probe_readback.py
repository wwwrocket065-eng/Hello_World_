import sys,ctypes as C,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from test_gpu_optics import context,proc,compile_program
from pathlib import Path
context()
a=Path(__file__).resolve().parents[1]/'app/src/main/assets/optics'
p=compile_program((a/'quad.vert').read_text(),(a/'probe.frag').read_text().replace('/*OPTICS_KERNEL*/',(a/'kernel.glsl').read_text()))
proc('glUseProgram',None,C.c_uint)(p)
textures=(C.c_uint*2)();proc('glGenTextures',None,C.c_int,C.POINTER(C.c_uint))(2,textures)
bind=proc('glBindTexture',None,C.c_uint,C.c_uint)
image=proc('glTexImage2D',None,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
param=proc('glTexParameteri',None,C.c_uint,C.c_uint,C.c_int)
profile=(C.c_float*12)(0,0,0,1,2,1,2,.8,.2,.8,.2,0)
bind(0x0DE1,textures[0]);image(0x0DE1,0,0x8230,6,1,0,0x8227,0x1406,profile)
for key in (0x2801,0x2800):param(0x0DE1,key,0x2600)
loc=proc('glGetUniformLocation',C.c_int,C.c_uint,C.c_char_p)
def i(name,v):proc('glUniform1i',None,C.c_int,C.c_int)(loc(p,name.encode()),v)
def f(name,v):proc('glUniform1f',None,C.c_int,C.c_float)(loc(p,name.encode()),v)
def v(name,*xyz):proc('glUniform3f',None,C.c_int,C.c_float,C.c_float,C.c_float)(loc(p,name.encode()),*xyz)
i('uProfile',0);i('uProfileCount',6);i('uBounces',16);i('uFloorMode',0)
f('uIor',1.52);f('uTanFov',0);f('uAspect',1);f('uFloorWidth',18)
v('uBoundsMin',-1.01,-.01,-1.01);v('uBoundsMax',1.01,2.01,1.01);v('uEye',0,1,5)
proc('glActiveTexture',None,C.c_uint)(0x84C1)
bind(0x0DE1,textures[1]);image(0x0DE1,0,0x8814,64,9,0,0x1908,0x1406,None)
for key in (0x2801,0x2800):param(0x0DE1,key,0x2600)
fbo=C.c_uint();proc('glGenFramebuffers',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(fbo))
proc('glBindFramebuffer',None,C.c_uint,C.c_uint)(0x8D40,fbo)
proc('glFramebufferTexture2D',None,C.c_uint,C.c_uint,C.c_uint,C.c_uint,C.c_int)(0x8D40,0x8CE0,0x0DE1,textures[1],0)
assert proc('glCheckFramebufferStatus',C.c_uint,C.c_uint)(0x8D40)==0x8CD5
vao=C.c_uint();proc('glGenVertexArrays',None,C.c_int,C.POINTER(C.c_uint))(1,C.byref(vao));proc('glBindVertexArray',None,C.c_uint)(vao)
proc('glViewport',None,C.c_int,C.c_int,C.c_int,C.c_int)(0,0,64,9)
read=proc('glReadPixels',None,C.c_int,C.c_int,C.c_int,C.c_int,C.c_uint,C.c_uint,C.c_void_p)
baseline=None
for analytic in (0,1):
 for cull in (0,1):
  i('uAnalyticSurface',analytic);i('uCullSegments',cull)
  proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
  out=(C.c_float*(64*9*4))();read(0,0,64,9,0x1908,0x1406,out)
  assert proc('glGetError',C.c_uint)()==0
  assert all(math.isfinite(x) for x in out)
  for ray in range(9):
   for step,z in enumerate((1,.8,-.8,-1)):
    offset=ray*64*4+step*16
    assert abs(out[offset+2]-z)<2e-4 and out[offset+3]==1,(ray,step,list(out[offset:offset+16]))
   assert out[ray*64*4+4*16+3]==3
   assert all(x==0 for x in out[ray*64*4+5*16:ray*64*4+16*16])
  print('PASS real probe readback: nine rays, four wall interfaces, background termination; analytic=%d cull=%d'%(analytic,cull))
