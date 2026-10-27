// Canonical hollow lathe. Outward normals select air/glass at EACH interface.
uniform highp sampler2D uProfile;
uniform int uProfileCount;
uniform float uIor;
uniform vec3 uBoundsMin,uBoundsMax;
const float EPS=0.00008;
const float FAR=1e20;
vec2 ringAt(int i) { return texelFetch(uProfile,ivec2(i,0),0).rg; }
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
    float best=FAR; normal=vec3(0);
    for(int j=0;j<281;j++) {
        if(j>=uProfileCount-1)break;
        vec2 a=ringAt(j),b=ringAt(j+1);
        float dy=b.x-a.x,dr=b.y-a.y;
        if(abs(dy)<1e-8) {
            if(abs(d.y)<1e-9)continue;
            float t=(a.x-o.y)/d.y;
            float r=length((o+t*d).xz);
            if(t>EPS && t<best && r>=min(a.y,b.y)-EPS && r<=max(a.y,b.y)+EPS) {
                best=t;normal=vec3(0,-sign(dr),0);
            }
        } else {
            float slope=dr/dy;
            float radius=a.y+slope*(o.y-a.x),rd=slope*d.y;
            float aa=dot(d.xz,d.xz)-rd*rd;
            float bb=2.0*(dot(o.xz,d.xz)-radius*rd);
            float cc=dot(o.xz,o.xz)-radius*radius;
            float disc=bb*bb-4.0*aa*cc;
            if(disc<0.0)continue;
            for(int k=0;k<2;k++) {
                float t;
                if(abs(aa)<1e-9) {
                    if(abs(bb)<1e-9)continue;
                    t=-cc/bb;
                } else t=(-bb+(k==0 ? -1.0 : 1.0)*sqrt(disc))/(2.0*aa);
                vec3 p=o+t*d;
                if(t>EPS && t<best && p.y>=min(a.x,b.x)-EPS && p.y<=max(a.x,b.x)+EPS) {
                    best=t;
                    normal=normalize(vec3(p.x,-slope*(a.y+slope*(p.y-a.x)),p.z))*sign(dy);
                }
            }
        }
    }
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
