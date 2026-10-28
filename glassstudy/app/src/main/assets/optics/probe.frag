#version 300 es
precision highp float;
precision highp int;
/*OPTICS_KERNEL*/
uniform vec3 uEye;
uniform float uTanFov,uAspect,uFloorWidth;
uniform int uBounces;
out vec4 color;
void main() {
    int column=int(gl_FragCoord.x),ray=int(gl_FragCoord.y);
    int step=column/4,field=column%4;
    // Nine deterministic primary rays around the central bowl, no stochastic jitter.
    vec2 ndc=vec2(float(ray%3-1)*.4,float(ray/3-1)*.2+.25);
    vec3 forward=normalize(vec3(0,1,0)-uEye);
    vec3 right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
    vec3 o=uEye,d=normalize(forward+ndc.x*uAspect*uTanFov*right+ndc.y*uTanFov*up);
    color=vec4(0);
    for(int bounce=0;bounce<24;bounce++) {
        if(bounce>step || bounce>=uBounces)break;
        vec3 n;float t=wallHit(o,d,n);
        float tf=abs(d.y)>1e-8 ? (-.06-o.y)/d.y : FAR;
        vec3 fp=o+tf*d;
        bool floorHit=tf>EPS && max(abs(fp.x),abs(fp.z))<=uFloorWidth*.5 && tf<t;
        if(floorHit || t>=FAR*.5) {
            if(bounce==step)color=field==0 ? vec4(floorHit ? fp : vec3(0),floorHit ? 2.0 : 3.0) : vec4(0);
            break;
        }
        vec3 hit=o+t*d,r,tr;float f;
        dielectric(d,n,r,tr,f);
        vec3 next=f>=1.0 ? r : tr;
        if(bounce==step) {
            if(field==0)color=vec4(hit,1);
            if(field==1)color=vec4(n,t);
            if(field==2)color=vec4(next,f);
            if(field==3)color=vec4(dot(d,n),uIor,float(uAnalyticSurface),float(uCullSegments));
        }
        d=next;o=hit+d*EPS*4.0;
    }
}
