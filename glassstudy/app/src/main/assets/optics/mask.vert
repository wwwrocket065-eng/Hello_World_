#version 300 es
precision highp float;
precision highp int;
uniform highp sampler2D uProfile;
uniform int uMaskSides;
uniform vec3 uEye;
uniform float uAspect,uTanFov;
out vec3 world;
void main() {
    int vertex=gl_VertexID%6,face=gl_VertexID/6;
    int corner=vertex==0 ? 0 : vertex==1 ? 2 : vertex==2 ? 1 : vertex==3 ? 1 : vertex==4 ? 2 : 3;
    int ring=face/uMaskSides+(corner>=2 ? 1 : 0);
    int radial=face%uMaskSides+corner%2;
    vec2 point=texelFetch(uProfile,ivec2(ring,0),0).rg;
    float angle=6.28318530718*float(radial)/float(uMaskSides);
    world=vec3(point.y*cos(angle),point.x,point.y*sin(angle));
    vec3 forward=normalize(vec3(0,1,0)-uEye);
    vec3 right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
    vec3 relative=world-uEye;
    // No depth attachment is needed: rasterize the union of visible surfaces.
    gl_Position=vec4(dot(relative,right)/(uAspect*uTanFov),dot(relative,up)/uTanFov,
        0,dot(relative,forward));
}
