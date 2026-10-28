#version 300 es
precision highp float;
in vec3 world;
uniform vec3 uEye;
uniform int uFloorMode;
uniform float uFloorWidth;
out vec4 color;
void main() {
    // Opaque floor can occlude the object if the camera is below the platform.
    vec3 ray=world-uEye;
    if(uFloorMode!=0 && abs(ray.y)>1e-8) {
        float t=(-.06-uEye.y)/ray.y;
        vec3 floorPoint=uEye+t*ray;
        if(t>0.0 && t<1.0 && max(abs(floorPoint.x),abs(floorPoint.z))<=uFloorWidth*.5)discard;
    }
    color=vec4(1);
}
