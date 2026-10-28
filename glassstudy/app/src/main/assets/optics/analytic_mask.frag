#version 300 es
precision highp float;
precision highp int;
/*OPTICS_KERNEL*/
uniform vec3 uEye;
uniform vec2 uResolution;
uniform float uAspect,uTanFov,uFloorWidth;
uniform int uFloorMode;
out vec4 color;
void main() {
    vec3 forward=normalize(vec3(0,1,0)-uEye);
    vec3 right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
    float covered=0.0;
    for(int sampleIndex=0;sampleIndex<5;sampleIndex++) {
        vec2 offset=sampleIndex==0 ? vec2(0) : vec2((sampleIndex==1 || sampleIndex==3) ? -.5 : .5,sampleIndex<3 ? -.5 : .5);
        vec2 ndc=2.0*(gl_FragCoord.xy+offset)/uResolution-1.0;
        vec3 d=normalize(forward+ndc.x*uAspect*uTanFov*right+ndc.y*uTanFov*up),n;
        float t=wallHit(uEye,d,n),floorT=FAR;
        if(uFloorMode!=0 && abs(d.y)>1e-8) {
            float tf=(-.06-uEye.y)/d.y;vec3 p=uEye+tf*d;
            if(tf>EPS && max(abs(p.x),abs(p.z))<=uFloorWidth*.5)floorT=tf;
        }
        if(t<min(floorT,FAR*.5))covered=1.0;
    }
    color=vec4(covered,0,0,1);
}
