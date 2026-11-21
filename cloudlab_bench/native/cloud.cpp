// CloudLab benchmark: independently compiled C++ implementation
#include <cstdint>
#include <jni.h>
static inline double mixval(double a,double b,double t){return a+(b-a)*t;}
static inline double hash3(int64_t x,int64_t y,int64_t z){return double((x*127+y*311+z*719)%1021)/1021.0;}
static inline double density(double x,double y,double z,double cover){
 double a=(x+60.0)*.72,b=(y+60.0)*.72,c=(z+60.0)*.72;
 int64_t ix=(int64_t)a,iy=(int64_t)b,iz=(int64_t)c;
 double u=a-double(ix),v=b-double(iy),w=c-double(iz);
 u=u*u*(3.-2.*u);v=v*v*(3.-2.*v);w=w*w*(3.-2.*w);
 double n0=mixval(mixval(hash3(ix,iy,iz),hash3(ix+1,iy,iz),u),mixval(hash3(ix,iy+1,iz),hash3(ix+1,iy+1,iz),u),v);
 double n1=mixval(mixval(hash3(ix,iy,iz+1),hash3(ix+1,iy,iz+1),u),mixval(hash3(ix,iy+1,iz+1),hash3(ix+1,iy+1,iz+1),u),v);
 double n=mixval(n0,n1,w),detail=hash3(ix+4,iy+2,iz+9);
 double d=(.77*n+.23*detail-(1.-cover)*.63-.28)*2.4;
 if(d<0.)d=0.;if(d>1.)d=1.;
 double h=(y-5.5)/2.8,mask=1.-h*h;if(mask<0.)mask=0.;return d*mask;
}
static double batch(double cloud,int64_t tick){
 double shift=double(tick%4096)*.000213,total=0.;
 for(int y=0;y<45;y++)for(int x=0;x<80;x++){
  double px=(double(x)-39.5)*.0625,pz=(double(y)-22.)*.0875,trans=1.;
  for(int i=0;i<22;i++){double t=double(i)*.31;
   double sx=px+t*.17+shift,sy=3.+t*.25,sz=pz+t*.61+shift*.7;
   double d=density(sx,sy,sz,cloud),shade=density(sx+.38,sy+.72,sz+.16,cloud);
   trans*=1./(1.+.025*(d+shade));
  }total+=1.-trans;
 }return total;
}
extern "C" JNIEXPORT jdouble JNICALL Java_com_cloudlab_raymarch_MainActivity_cppBatch(JNIEnv*,jclass,jdouble cover,jlong tick){
 return batch(cover,tick);
}
