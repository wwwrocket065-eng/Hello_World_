"""Read exact production primitive intersections on real EGL/ES3."""
import test_optics_probe_readback as gpu
from test_optics_probe_readback import C,proc,read,i,f,v
from test_gpu_optics import compile_program
kernel=(gpu.a/'kernel.glsl').read_text()
fragment='#version 300 es\nprecision highp float;\nprecision highp int;\n'+kernel+'\nuniform vec3 uEye;out vec4 color;void main(){vec3 n;float t=wallHit(uEye,vec3(0,0,-1),n);color=vec4(n,t);}'
gpu.p=compile_program((gpu.a/'quad.vert').read_text(),fragment)
proc('glUseProgram',None,C.c_uint)(gpu.p)
f('uPrimitiveRadius',.8);f('uPrimitiveHeight',1.6)
for kind,front in ((1,.8),(2,.4),(3,.8),(4,.8)):
    i('uPrimitiveType',kind)
    for start,expected,sign in ((5,5-front,1),(0,front,-1)):
        v('uEye',0,.8,start)
        proc('glDrawArrays',None,C.c_uint,C.c_int,C.c_int)(4,0,3)
        out=(C.c_float*(64*9*4))();read(0,0,64,9,0x1908,0x1406,out)
        assert proc('glGetError',C.c_uint)()==0
        assert all(gpu.math.isfinite(x) for x in out)
        assert abs(out[3]-expected)<2e-4,(kind,start,list(out[:4]))
        assert out[2]*sign>.5,(kind,start,list(out[:4]))
    print('PASS production primitive entry/inside exit, type',kind)
