#version 300 es
precision highp float;
uniform sampler2D uImage;
uniform vec2 uScreen;
uniform float uExposure;
out vec4 color;
void main() {
    vec3 linear=texture(uImage,gl_FragCoord.xy/uScreen).rgb*uExposure;
    linear=linear/(1.0+linear);
    vec3 srgb=mix(linear*12.92,1.055*pow(linear,vec3(1.0/2.4))-.055,step(vec3(.0031308),linear));
    color=vec4(srgb,1);
}
