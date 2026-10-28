#version 300 es
precision highp float;
uniform sampler2D uImage;
uniform vec2 uScreen;
uniform float uExposure;
uniform int uFxaa;
out vec4 color;
vec3 mapped(vec2 uv) {
    vec3 linear=texture(uImage,uv).rgb*uExposure;
    linear=linear/(1.0+linear);
    return mix(linear*12.92,1.055*pow(linear,vec3(1.0/2.4))-.055,step(vec3(.0031308),linear));
}
void main() {
    vec2 uv=gl_FragCoord.xy/uScreen;
    vec3 center=mapped(uv);
    if(uFxaa==0) { color=vec4(center,1);return; }
    vec2 texel=1.0/vec2(textureSize(uImage,0));
    vec3 nw=mapped(uv+vec2(-1,1)*texel),ne=mapped(uv+vec2(1,1)*texel);
    vec3 sw=mapped(uv+vec2(-1,-1)*texel),se=mapped(uv+vec2(1,-1)*texel);
    vec3 luma=vec3(.299,.587,.114);
    float m=dot(center,luma),a=dot(nw,luma),b=dot(ne,luma),c=dot(sw,luma),d=dot(se,luma);
    float lo=min(m,min(min(a,b),min(c,d))),hi=max(m,max(max(a,b),max(c,d)));
    if(hi-lo<max(.0312,hi*.125)) { color=vec4(center,1);return; }
    vec2 direction=vec2(-(a+b-c-d),a+c-b-d);
    float reduction=max((a+b+c+d)*.03125,.0078125);
    direction=clamp(direction/(min(abs(direction.x),abs(direction.y))+reduction),vec2(-8),vec2(8))*texel;
    vec3 shortFilter=.5*(mapped(uv+direction*(-1.0/6.0))+mapped(uv+direction*(1.0/6.0)));
    vec3 longFilter=shortFilter*.5+.25*(mapped(uv-direction*.5)+mapped(uv+direction*.5));
    float filteredLuma=dot(longFilter,luma);
    color=vec4(filteredLuma<lo || filteredLuma>hi ? shortFilter : longFilter,1);
}
