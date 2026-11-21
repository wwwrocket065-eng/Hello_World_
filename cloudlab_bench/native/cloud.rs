// CloudLab benchmark: independently compiled Rust implementation
use std::ffi::c_void;
#[inline(always)] fn mix(a:f64,b:f64,t:f64)->f64 {a+(b-a)*t}
#[inline(always)] fn hash3(x:i64,y:i64,z:i64)->f64 {
    ((x*127+y*311+z*719)%1021) as f64 /1021.0
}
#[inline(always)] fn density(x:f64,y:f64,z:f64,cover:f64)->f64 {
    let a=(x+60.0)*0.72;let b=(y+60.0)*0.72;let c=(z+60.0)*0.72;
    let ix=a as i64;let iy=b as i64;let iz=c as i64;
    let mut u=a-ix as f64;let mut v=b-iy as f64;let mut w=c-iz as f64;
    u=u*u*(3.0-2.0*u);v=v*v*(3.0-2.0*v);w=w*w*(3.0-2.0*w);
    let n0=mix(mix(hash3(ix,iy,iz),hash3(ix+1,iy,iz),u),
               mix(hash3(ix,iy+1,iz),hash3(ix+1,iy+1,iz),u),v);
    let n1=mix(mix(hash3(ix,iy,iz+1),hash3(ix+1,iy,iz+1),u),
               mix(hash3(ix,iy+1,iz+1),hash3(ix+1,iy+1,iz+1),u),v);
    let n=mix(n0,n1,w);let detail=hash3(ix+4,iy+2,iz+9);
    let mut d=(0.77*n+0.23*detail-(1.0-cover)*0.63-0.28)*2.4;
    if d<0.0 {d=0.0;} if d>1.0 {d=1.0;}
    let h=(y-5.5)/2.8;let mut mask=1.0-h*h;if mask<0.0 {mask=0.0;}
    d*mask
}
pub fn batch(cloud:f64,tick:i64)->f64 {
    let shift=(tick%4096) as f64*0.000213;let mut total=0.0;
    for y in 0..45 {for x in 0..80 {
        let px=(x as f64-39.5)*0.0625;let pz=(y as f64-22.0)*0.0875;
        let mut trans=1.0;
        for i in 0..22 {
            let t=i as f64*0.31;let sx=px+t*0.17+shift;let sy=3.0+t*0.25;
            let sz=pz+t*0.61+shift*0.7;
            let d=density(sx,sy,sz,cloud);
            let shade=density(sx+0.38,sy+0.72,sz+0.16,cloud);
            trans*=1.0/(1.0+0.025*(d+shade));
        }
        total+=1.0-trans;
    }}
    total
}
#[no_mangle]
pub extern "system" fn Java_com_cloudlab_raymarch_MainActivity_rustBatch(_env:*mut c_void,_klass:*mut c_void,cover:f64,tick:i64)->f64{
    batch(cover,tick)
}
