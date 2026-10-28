// Canonical hollow lathe. Outward normals select air/glass at EACH interface.
uniform highp sampler2D uProfile;
uniform int uProfileCount;
uniform int uCullSegments;
uniform int uSmoothSurface;
uniform float uIor;
uniform vec3 uBoundsMin,uBoundsMax;
const float EPS=0.00008;
const float FAR=1e20;
vec3 limitedRadiance(vec3 value,float maximum) {
    if(any(isnan(value)) || any(isinf(value)))return vec3(0);
    float peak=max(value.r,max(value.g,value.b));
    return peak>maximum ? value*(maximum/peak) : value;
}
vec2 ringAt(int i) { return texelFetch(uProfile,ivec2(i,0),0).rg; }
vec2 segmentNormal(int j) {
    vec2 delta=ringAt(j+1)-ringAt(j);
    return normalize(vec2(delta.x,-delta.y));
}
vec3 opticalNormal(int j,vec3 p,vec3 d,vec3 geometric) {
    if(uSmoothSurface==0)return geometric;
    vec2 a=ringAt(j),b=ringAt(j+1),delta=b-a;
    vec2 center=segmentNormal(j),low=center,high=center;
    // Preserve real creases at rim/caps instead of smearing opposite surfaces.
    if(j>0) {
        vec2 previous=segmentNormal(j-1);
        if(dot(center,previous)>.5)low=normalize(center+previous);
    }
    if(j+2<uProfileCount) {
        vec2 next=segmentNormal(j+1);
        if(dot(center,next)>.5)high=normalize(center+next);
    }
    float radius=length(p.xz);
    float f=clamp(dot(vec2(p.y,radius)-a,delta)/max(dot(delta,delta),1e-12),0.0,1.0);
    vec2 meridian=normalize(mix(low,high,f));
    vec3 smoothN=normalize(vec3(meridian.x*p.x/max(radius,1e-9),meridian.y,
        meridian.x*p.z/max(radius,1e-9)));
    // A shading normal may not reverse the actual entry/exit side at grazing incidence.
    return dot(smoothN,d)*dot(geometric,d)>0.0 ? smoothN : geometric;
}
void segmentHit(int j,vec3 o,vec3 d,inout float best,inout int bestSegment,inout vec3 normal) {
    vec2 a=ringAt(j),b=ringAt(j+1);
    // Reject a segment whose accepted height interval cannot be reached
    // before the current nearest hit. Keep extra EPS for float roundoff;
    // radial culling would be unsafe for the old near-horizontal quadratic.
    if(uCullSegments!=0) {
        float low=min(a.x,b.x)-2.0*EPS,high=max(a.x,b.x)+2.0*EPS;
        if(abs(d.y)<1e-9) { if(o.y<low || o.y>high)return; }
        else {
            float t0=(low-o.y)/d.y,t1=(high-o.y)/d.y;
            if(min(best+EPS,max(t0,t1))<max(0.0,min(t0,t1)))return;
        }
    }
    float dy=b.x-a.x,dr=b.y-a.y;
    if(abs(dy)<1e-8) {
        if(abs(d.y)<1e-9)return;
        float t=(a.x-o.y)/d.y;
        float r=length((o+t*d).xz);
        if(t>EPS && (t<best || (t==best && j<bestSegment)) && r>=min(a.y,b.y)-EPS && r<=max(a.y,b.y)+EPS) {
            best=t;bestSegment=j;normal=vec3(0,-sign(dr),0);
        }
    } else {
        float slope=dr/dy;
        float radius=a.y+slope*(o.y-a.x),rd=slope*d.y;
        float aa=dot(d.xz,d.xz)-rd*rd;
        float bb=2.0*(dot(o.xz,d.xz)-radius*rd);
        float cc=dot(o.xz,o.xz)-radius*radius;
        float disc=bb*bb-4.0*aa*cc;
        if(disc<0.0)return;
        for(int k=0;k<2;k++) {
            float t;
            if(abs(aa)<1e-9) {
                if(abs(bb)<1e-9)continue;
                t=-cc/bb;
            } else t=(-bb+(k==0 ? -1.0 : 1.0)*sqrt(disc))/(2.0*aa);
            vec3 p=o+t*d;
            if(t>EPS && (t<best || (t==best && j<bestSegment)) && p.y>=min(a.x,b.x)-EPS && p.y<=max(a.x,b.x)+EPS) {
                best=t;bestSegment=j;
                vec3 geometric=normalize(vec3(p.x,-slope*(a.y+slope*(p.y-a.x)),p.z))*sign(dy);
                normal=geometric;
            }
        }
    }
}
float wallHit(vec3 o,vec3 d,out vec3 normal) {
    // Conservative canonical bound avoids walking the contour for most pixels.
    float nearT=0.0, farT=1e5;
    for(int axis=0;axis<3;axis++) {
        float low=uBoundsMin[axis],high=uBoundsMax[axis];
        if(abs(d[axis])<1e-9) { if(o[axis]<low || o[axis]>high)return FAR; }
        else {
            float a=(low-o[axis])/d[axis],b=(high-o[axis])/d[axis];
            nearT=max(nearT,min(a,b)); farT=min(farT,max(a,b));
        }
    }
    if(farT<nearT)return FAR;
    float best=FAR;int bestSegment=2147483647;normal=vec3(0);
    for(int j=0;j<281;j++) {
        if(j>=uProfileCount-1)break;
        segmentHit(j,o,d,best,bestSegment,normal);
    }

    // Interpolate the shading normal ONCE, at the nearest hit only.
    if(best<FAR && abs(ringAt(bestSegment+1).x-ringAt(bestSegment).x)>=1e-8)
        normal=opticalNormal(bestSegment,o+best*d,d,normal);
    return best;
}

void dielectric(vec3 d,vec3 outward,out vec3 reflected,out vec3 transmitted,out float f) {
    bool entry=dot(d,outward)<0.0;
    vec3 n=entry ? outward : -outward;
    float ni=entry ? 1.0 : uIor,nt=entry ? uIor : 1.0;
    float eta=ni/nt,c=clamp(-dot(d,n),0.0,1.0);
    float k=1.0-eta*eta*(1.0-c*c);
    reflected=normalize(d+2.0*c*n);
    if(k<0.0) { transmitted=reflected; f=1.0;return; }
    float ct=sqrt(k);
    transmitted=normalize(eta*d+(eta*c-ct)*n);
    float rs=(ni*c-nt*ct)/max(ni*c+nt*ct,1e-9);
    float rp=(nt*c-ni*ct)/max(nt*c+ni*ct,1e-9);
    f=clamp(.5*(rs*rs+rp*rp),0.0,1.0);
}
