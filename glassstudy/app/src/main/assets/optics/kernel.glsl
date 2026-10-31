// Canonical hollow lathe. Outward normals select air/glass at EACH interface.
uniform highp sampler2D uProfile;
uniform int uProfileCount;
uniform int uCullSegments;
uniform int uSmoothSurface;
uniform int uAnalyticSurface;
uniform float uIor;
uniform int uPrimitiveType;
uniform float uPrimitiveRadius,uPrimitiveHeight;
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

// Shape-preserving C1 meridian: the SAME cubic defines hits and normals.
// Height reversals / horizontal annuli keep their actual geometric crease.
float sharedSlope(vec2 previous,vec2 center,vec2 next) {
    vec2 a=center-previous,b=next-center;
    if(a.x*b.x<=0.0 || abs(a.x)<1e-8 || abs(b.x)<1e-8)return 0.0;
    float left=a.y/a.x,right=b.y/b.x;
    if(left*right<=0.0)return 0.0;
    float h0=abs(a.x),h1=abs(b.x);
    float w0=2.0*h1+h0,w1=h1+2.0*h0;
    return (w0+w1)/(w0/left+w1/right);
}
vec4 curveAt(int j) {
    vec2 a=ringAt(j),b=ringAt(j+1);float dy=b.x-a.x,delta=b.y-a.y;
    float low=delta,high=delta;
    if(j>0 && (a.x-ringAt(j-1).x)*dy>0.0)
        low=dy*sharedSlope(ringAt(j-1),a,b);
    if(j+2<uProfileCount && (ringAt(j+2).x-b.x)*dy>0.0)
        high=dy*sharedSlope(a,b,ringAt(j+2));
    return vec4(a.y,low,3.0*delta-2.0*low-high,-2.0*delta+low+high);
}
float curveRadius(vec4 c,float u) { return ((c.w*u+c.z)*u+c.y)*u+c.x; }
float polynomial(float c[49],int row,int degree,float x) {
    float y=c[row*7+degree];
    for(int k=5;k>=0;k--)if(k<degree)y=y*x+c[row*7+k];
    return y;
}
// Isolate all real roots in [0,1] by the roots of successive derivatives.
// Each interval is monotone; bisection also handles multiple intersections.
float firstRoot(float coefficients[7]) {
    float c[49];for(int k=0;k<49;k++)c[k]=0.0;
    float scale=0.0;for(int k=0;k<7;k++)scale=max(scale,abs(coefficients[k]));
    if(scale<1e-25)return 0.0;
    int degree=6;
    for(int k=6;k>0;k--) { if(abs(coefficients[k])<scale*1e-8)degree=k-1;else break; }
    if(degree==0)return FAR;
    for(int k=0;k<7;k++)c[k]=coefficients[k]/scale;
    for(int row=1;row<6;row++)for(int k=0;k<6;k++)
        c[row*7+k]=float(k+1)*c[(row-1)*7+k+1];
    float roots[7];int count=0;
    for(int d=1;d<=6;d++) {
        if(d>degree)break;
        int row=degree-d;
        float found[7];int n=0;float left=0.0;
        for(int interval=0;interval<7;interval++) {
            if(interval>count)break;
            float right=interval<count ? roots[interval] : 1.0;
            float fl=polynomial(c,row,d,left),fr=polynomial(c,row,d,right);
            if(abs(fl)<1e-7 && (n==0 || left-found[n-1]>1e-6))found[n++]=left;
            if(fl*fr<0.0) {
                float lo=left,hi=right;
                for(int step=0;step<25;step++) {
                    float mid=.5*(lo+hi),fm=polynomial(c,row,d,mid);
                    if((fm<0.0)==(fl<0.0))lo=mid;else hi=mid;
                }
                float hit=.5*(lo+hi);
                if(n==0 || hit-found[n-1]>1e-6)found[n++]=hit;
            }
            if(interval==count && abs(fr)<1e-7 && (n==0 || right-found[n-1]>1e-6))found[n++]=right;
            left=right;
        }
        count=n;for(int k=0;k<7;k++)if(k<count)roots[k]=found[k];
    }
    return count>0 ? roots[0] : FAR;
}
void curvedHit(int j,vec3 o,vec3 d,inout float best,inout int bestSegment,inout vec3 normal) {
    vec2 a=ringAt(j),b=ringAt(j+1);float dy=b.x-a.x;
    if(abs(dy)<1e-8) { segmentHit(j,o,d,best,bestSegment,normal);return; }
    float lo=EPS*1.001,hi=min(best,1e5);
    float rmax=max(a.y,b.y)+EPS;
    float aa=dot(d.xz,d.xz),bb=dot(o.xz,d.xz),cc=dot(o.xz,o.xz)-rmax*rmax;
    if(aa>1e-12) {
        float disc=bb*bb-aa*cc;if(disc<0.0)return;
        float root=sqrt(disc);lo=max(lo,(-bb-root)/aa);hi=min(hi,(-bb+root)/aa);
    } else if(cc>0.0)return;
    if(abs(d.y)>1e-10) {
        float t0=(a.x-o.y)/d.y,t1=(b.x-o.y)/d.y;
        lo=max(lo,min(t0,t1));hi=min(hi,max(t0,t1));
    } else if(o.y<min(a.x,b.x) || o.y>max(a.x,b.x))return;
    if(hi<=lo)return;
    vec4 curve=curveAt(j);
    // Compose r(u0+du*s), with ray distance lo+(hi-lo)*s.
    float span=hi-lo,u0=(o.y+d.y*lo-a.x)/dy,du=d.y*span/dy;
    float radial[4];
    radial[0]=curveRadius(curve,u0);
    radial[1]=du*(curve.y+u0*(2.0*curve.z+3.0*curve.w*u0));
    radial[2]=du*du*(curve.z+3.0*curve.w*u0);
    radial[3]=du*du*du*curve.w;
    float c[7];for(int k=0;k<7;k++)c[k]=0.0;
    for(int x=0;x<4;x++)for(int y=0;y<4;y++)c[x+y]-=radial[x]*radial[y];
    vec2 pos=(o+lo*d).xz,step=d.xz*span;
    c[0]+=dot(pos,pos);c[1]+=2.0*dot(pos,step);c[2]+=dot(step,step);
    float fraction=firstRoot(c);if(fraction>1.0)return;
    float t=lo+span*fraction;vec3 p=o+t*d;
    float u=clamp((p.y-a.x)/dy,0.0,1.0);
    float radius=curveRadius(curve,u),slope=(curve.y+u*(2.0*curve.z+3.0*curve.w*u))/dy;
    // Reject numerical near-zero extrema that are not on the actual surface.
    if(abs(length(p.xz)-radius)>2.0*EPS)return;
    if(t>EPS && (t<best || (t==best && j<bestSegment))) {
        best=t;bestSegment=j;
        normal=normalize(vec3(p.x,-slope*radius,p.z))*sign(dy);
    }
}

// Closed solid primitives use the same dielectric/photon integrator as the goblet.
float primitiveHit(vec3 o,vec3 d,out vec3 normal) {
    normal=vec3(0);float r=uPrimitiveRadius,h=uPrimitiveHeight,best=FAR;
    if(uPrimitiveType==1) {
        vec3 scale=vec3(r,h*.5,r),q=(o-vec3(0,h*.5,0))/scale,v=d/scale;
        float aa=dot(v,v),bb=dot(q,v),cc=dot(q,q)-1.0,disc=bb*bb-aa*cc;
        if(disc<0.0)return FAR;
        float a=(-bb-sqrt(disc))/aa,b=(-bb+sqrt(disc))/aa;
        best=a>EPS ? a : (b>EPS ? b : FAR);
        if(best<FAR)normal=normalize((o+best*d-vec3(0,h*.5,0))/(scale*scale));
        return best;
    }
    if(uPrimitiveType==3) {
        float nearT=-FAR,farT=FAR;vec3 nearN=vec3(0),farN=vec3(0);
        for(int axis=0;axis<3;axis++) {
            float low=axis==1 ? 0.0 : -r,high=axis==1 ? h : r;
            if(abs(d[axis])<1e-9) { if(o[axis]<low || o[axis]>high)return FAR; }
            else {
                float a=(low-o[axis])/d[axis],b=(high-o[axis])/d[axis];
                vec3 na=vec3(0),nb=vec3(0);na[axis]=-1.0;nb[axis]=1.0;
                if(a>b){float tmp=a;a=b;b=tmp;vec3 nt=na;na=nb;nb=nt;}
                if(a>nearT){nearT=a;nearN=na;}if(b<farT){farT=b;farN=nb;}
            }
        }
        if(farT<nearT || farT<=EPS)return FAR;
        normal=nearT>EPS ? nearN : farN;return nearT>EPS ? nearT : farT;
    }
    // Cone and cylinder: exact conic side plus closed disk caps.
    float slope=uPrimitiveType==2 ? -r/h : 0.0;
    float radius=r+slope*o.y,rd=slope*d.y;
    float aa=dot(d.xz,d.xz)-rd*rd,bb=2.0*(dot(o.xz,d.xz)-radius*rd);
    float cc=dot(o.xz,o.xz)-radius*radius,disc=bb*bb-4.0*aa*cc;
    if(disc>=0.0)for(int k=0;k<2;k++) {
        if(abs(aa)<1e-10 && abs(bb)<1e-10)continue;
        float t=abs(aa)<1e-10 ? -cc/bb : (-bb+(k==0 ? -1.0 : 1.0)*sqrt(disc))/(2.0*aa);
        vec3 p=o+t*d;
        if(t>EPS && t<best && p.y>=0.0 && p.y<=h) {
            best=t;vec3 n=vec3(p.x,-slope*(r+slope*p.y),p.z);
            normal=dot(n,n)>1e-20 ? normalize(n) : vec3(0,1,0);
        }
    }
    if(abs(d.y)>1e-9)for(int cap=0;cap<2;cap++) {
        float y=cap==0 ? 0.0 : h,rr=r+slope*y,t=(y-o.y)/d.y;
        vec3 p=o+t*d;
        if(t>EPS && t<best && dot(p.xz,p.xz)<=rr*rr) {
            best=t;normal=vec3(0,cap==0 ? -1.0 : 1.0,0);
        }
    }
    return best;
}

float wallHit(vec3 o,vec3 d,out vec3 normal) {
    if(uPrimitiveType!=0)return primitiveHit(o,d,normal);
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
        if(uAnalyticSurface==1)curvedHit(j,o,d,best,bestSegment,normal);
        else segmentHit(j,o,d,best,bestSegment,normal);
    }

    // Interpolate the shading normal ONCE, at the nearest hit only.
    if(uAnalyticSurface==0 && best<FAR && abs(ringAt(bestSegment+1).x-ringAt(bestSegment).x)>=1e-8)
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
