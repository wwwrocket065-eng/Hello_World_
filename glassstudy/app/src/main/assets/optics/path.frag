#version 300 es
precision highp float;
precision highp int;
/*OPTICS_KERNEL*/
uniform sampler2D uPrevious,uPhotons;
uniform vec2 uResolution;
uniform vec3 uEye,uSun,uSunColor,uBackdrop;
uniform float uTanFov,uAspect,uDirect,uSky,uHaze,uExposure;
uniform float uFloorWidth,uTile,uDark,uLight,uWhite,uPhotonExtent;
uniform int uFloorMode,uFrame,uBounces,uPhotonReady;
out vec4 color;
uint rng;
float randomValue() {
    rng^=rng<<13; rng^=rng>>17; rng^=rng<<5;
    return float(rng)*(1.0/4294967296.0);
}
vec3 sky(vec3 d) {
    if(d.y<0.0)return vec3(0);
    float horizon=1.0-clamp(d.y,0.0,1.0);
    float cs=clamp(dot(d,uSun),-1.0,1.0);
    float rayleigh=.75*(1.0+cs*cs);
    float mie=(1.0-uHaze*.55)/pow(max(1.0+.65*.65-1.3*cs,.065),1.5);
    float low=exp(-clamp(uSun.y,0.0,1.0)*10.0);
    float aerosol=.025+.08*uHaze*min(mie,8.0)*(.3+.7*horizon);
    vec3 radiance=vec3(.36,.62,1)*(.6+.4*rayleigh)*(.65+.35*horizon)*(1.0-.55*low)+
        aerosol*vec3(1,.76,.46)*(1.0+2.5*low);
    // Finite sun, independent of diffuse atmosphere (also present in vacuum).
    float disc=cs>cos(.0093) ? 1.0 : 0.0;
    return radiance*uSky+disc*uSunColor*uDirect/(6.2831853*(1.0-cos(.0093)));
}
float floorDistance(vec3 o,vec3 d) {
    if(uFloorMode==0 || abs(d.y)<1e-8)return FAR;
    float t=(-.06-o.y)/d.y;
    vec3 p=o+t*d;
    return t>EPS && max(abs(p.x),abs(p.z))<=uFloorWidth*.5 ? t : FAR;
}
vec3 floorRadiance(vec3 p) {
    float cells=floor(uFloorWidth/uTile);
    float tile=uFloorWidth/max(cells,2.0);
    float odd=mod(floor((p.x+uFloorWidth*.5)/tile)+floor((p.z+uFloorWidth*.5)/tile),2.0);
    float albedo=uFloorMode==1 ? mix(uLight,uDark,odd) : uWhite;
    float flux=1.0;
    vec2 uv=p.xz/(2.0*uPhotonExtent)+.5;
    if(uPhotonReady==1 && min(uv.x,uv.y)>.015 && max(uv.x,uv.y)<.985) {
        // Filter irradiance, not floor reflectance. No rectangular white film.
        vec2 dx=1.0/vec2(textureSize(uPhotons,0));
        flux=texture(uPhotons,uv).r*.25;
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)
            if(x!=0 || y!=0)flux+=texture(uPhotons,uv+vec2(x,y)*dx).r*.09375;
    }
    return albedo*(uSunColor*uDirect*max(uSun.y,0.0)*flux+vec3(.36,.62,1)*uSky)/3.14159265;
}
void main() {
    rng=uint(gl_FragCoord.x)*1973u+uint(gl_FragCoord.y)*9277u+uint(uFrame+1)*26699u+1u;
    vec2 pixel=gl_FragCoord.xy+vec2(randomValue(),randomValue())-.5;
    vec2 ndc=(pixel/uResolution)*2.0-1.0;
    vec3 forward=normalize(vec3(0,1,0)-uEye);
    vec3 right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
    vec3 d=normalize(forward+ndc.x*uAspect*uTanFov*right+ndc.y*uTanFov*up);
    vec3 o=uEye,result=vec3(0);
    for(int bounce=0;bounce<24;bounce++) {
        if(bounce>=uBounces)break;
        vec3 n;float t=wallHit(o,d,n),tf=floorDistance(o,d);
        if(tf<t) { result=floorRadiance(o+tf*d);break; }
        if(t>=FAR*.5) { result=bounce==0 ? uBackdrop : sky(d);break; }
        vec3 hit=o+t*d,r,tr;float f;
        dielectric(d,n,r,tr,f);
        d=randomValue()<f ? r : tr;
        o=hit+d*EPS*4.0;
    }
    vec3 previous=uFrame==0 ? vec3(0) : texelFetch(uPrevious,ivec2(gl_FragCoord.xy),0).rgb;
    color=vec4((previous*float(uFrame)+result)/float(uFrame+1),1);
}
