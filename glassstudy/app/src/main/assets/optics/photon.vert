#version 300 es
precision highp float;
precision highp int;
/*OPTICS_KERNEL*/
uniform vec3 uSun;
uniform float uPhotonExtent;
uniform int uPhotonSide,uPhotonMapSize,uBounces;
out float photonEnergy;
void main() {
    vec2 cell=vec2(gl_VertexID%uPhotonSide,gl_VertexID/uPhotonSide)+.5;
    vec2 xz=(cell/float(uPhotonSide)*2.0-1.0)*uPhotonExtent;
    vec3 d=-uSun;
    vec3 o=vec3(xz.x,-.06,xz.y)+uSun*(11.0/max(uSun.y,.0001));
    float energy=1.0;
    vec2 impact=vec2(10);
    for(int bounce=0;bounce<24;bounce++) {
        if(bounce>=uBounces) { energy=0.0;break; }
        vec3 n;float t=wallHit(o,d,n);
        float tf=d.y< -1e-8 ? (-.06-o.y)/d.y : FAR;
        if(tf>EPS && tf<t) { impact=(o+tf*d).xz/uPhotonExtent;break; }
        if(t>=FAR*.5) { energy=0.0;break; }
        vec3 hit=o+t*d,r,tr;float f;
        dielectric(d,n,r,tr,f);
        // Unbiased Fresnel branch sampling, including total internal reflection.
        uint h=uint(gl_VertexID+1)*747796405u+uint(bounce+1)*2891336453u;
        h=(h^(h>>16))*2246822519u;
        d=float(h)*(1.0/4294967296.0)<f ? r : tr;
        o=hit+d*EPS*4.0;
    }
    gl_Position=vec4(impact,0,1);gl_PointSize=1.0;
    photonEnergy=energy*float(uPhotonMapSize*uPhotonMapSize)/float(uPhotonSide*uPhotonSide);
}
