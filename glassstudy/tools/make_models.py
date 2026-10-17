#!/usr/bin/env python3
"""Generate 33 local GLB meshes for an independent Filament Android beta.
Mesh geometry is used directly by gltfio for PBR, never a separate SDF."""
import math, json, struct, pathlib
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/models'
root.mkdir(parents=True, exist_ok=True)
names=['faceted','tumbler','wine','mug','pitcher','bowl','vase','sphere','cube','cylinder','cone']
def make(name,lod,debug=False):
    seg={'low':16,'medium':40,'high':80}[lod]
    h=2.0; rad=.72
    if name in ('mug','pitcher'):h=2.2;rad=.86
    if name=='bowl':h=1.2;rad=1.05
    if name=='vase':h=2.7;rad=.72
    if name=='wine':h=2.5;rad=.92
    # A closed 2D wall cross-section revolved around the y-axis.
    if name in ('sphere','cube','cylinder','cone'):
        if name=='sphere':profile=[(.03,.0),(.12,.45),(.4,.72),(.9,.8),(1.4,.72),(1.8,.45),(1.9,.0)]
        elif name=='cube':profile=[(.04,0),(.04,.75),(1.75,.75),(1.75,0)]
        elif name=='cone':profile=[(.04,0),(.04,.85),(1.9,.01),(1.91,0)]
        else:profile=[(.04,0),(.04,.74),(1.85,.74),(1.85,0)]
    else:
        if name=='wine':
            profile=[(.03,0),(.03,.52),(.12,.52),(.15,.14),(.95,.13),(1.03,.28),(1.12,.54),(1.50,.81),(1.9,.92),(2.3,.88),(2.34,.83),(2.26,.80),(1.9,.82),(1.52,.72),(1.2,.47),(1.15,.20),(1.12,0)]
        elif name=='bowl':
            profile=[(.03,0),(.03,.49),(.12,.53),(.27,.70),(.55,.92),(1.10,1.04),(1.13,.99),(1.05,.95),(.55,.84),(.30,.63),(.20,.41),(.20,0)]
        else:
            profile=[(.03,0),(.03,rad*.76),(.07,rad*.86),(.24,rad*.94),(h*.6,rad*(1.06 if name!='vase' else .70)),(h-.07,rad*(1.06 if name!='vase' else .83)),(h,rad),(h,rad-.055),(h-.08,rad-.065),(h*.6,rad*(.98 if name!='vase' else .61)),(.28,rad*.83),(.19,rad*.7),(.19,0)]
    def xyz(j,k):
        y,r=profile[j];a=2*math.pi*k/seg
        if name=='faceted':a=2*math.pi*round(k/(seg/12)) / 12
        return (r*math.cos(a),y,r*math.sin(a))
    verts=[xyz(j,k) for j in range(len(profile)) for k in range(seg)]
    faces=[]
    for j in range(len(profile)-1):
        for k in range(seg):
            a=j*seg+k;b=j*seg+(k+1)%seg;c=(j+1)*seg+k;d=(j+1)*seg+(k+1)%seg
            faces.extend([a,b,c,b,d,c])
    # Calculate smooth normals from all triangle faces.
    normals=[[0.,0.,0.] for _ in verts]
    for i in range(0,len(faces),3):
        a,b,c=[verts[faces[i+t]] for t in range(3)]
        u=[b[q]-a[q] for q in range(3)];v=[c[q]-a[q] for q in range(3)]
        n=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        for ix in faces[i:i+3]:
            for q in range(3):normals[ix][q]+=n[q]
    normals=[tuple(v/max(1.e-12,math.sqrt(sum(x*x for x in n))) for v in n) for n in normals]
    buf=bytearray();views=[];acc=[]
    def write(vals,comps,kind,target):
        while len(buf)%4:buf.append(0)
        off=len(buf)
        fmt='<'+ ('f' if kind==5126 else 'I')*comps
        for item in vals:
            if comps==1:item=[item]
            buf.extend(struct.pack(fmt,*item))
        bv=len(views);views.append(dict(buffer=0,byteOffset=off,byteLength=len(buf)-off,target=target))
        a=dict(bufferView=bv,componentType=kind,count=len(vals),type='VEC3' if comps==3 else 'SCALAR')
        if kind==5126 and comps==3:
            a['min']=[min(v[q] for v in vals) for q in range(3)]
            a['max']=[max(v[q] for v in vals) for q in range(3)]
        ai=len(acc);acc.append(a);return ai
    p=write(verts,3,5126,34962); n=write(normals,3,5126,34962);i=write(faces,1,5125,34963)
    js={'asset':{'version':'2.0','generator':'GlassStudy Filament native'},'extensionsUsed':['KHR_materials_transmission','KHR_materials_ior','KHR_materials_volume'],
      'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'mesh':0}],'meshes':[{'primitives':[{'attributes':{'POSITION':p,'NORMAL':n},'indices':i,'material':0}]}],
      'materials':[{'name':'Glass','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.98,.99,1,1],'metallicFactor':0,'roughnessFactor':.02},'extensions':{'KHR_materials_transmission':{'transmissionFactor':1},'KHR_materials_ior':{'ior':1.52},'KHR_materials_volume':{'thicknessFactor':.035,'attenuationDistance':10,'attenuationColor':[.98,.99,1]}}}],
      'bufferViews':views,'accessors':acc,'buffers':[{'byteLength':len(buf)}]}
    if True:
        # Debug: full triangle mesh; regular glass: only slim rim/foot outlines.
        # The underlying opaque primitive and the overlay share the same vertex mesh.
        edges=set()
        for fi in range(0,len(faces),3):
            a,b,c=faces[fi:fi+3]
            for a1,b1 in ((a,b),(b,c),(c,a)):
                if a1!=b1:edges.add((min(a1,b1),max(a1,b1)))
        line_verts=[];line_normals=[];line_faces=[]
        thickness=.005
        for a,b in sorted(edges):
            pa=verts[a];pb=verts[b]
            if not debug and (abs(pa[1]-pb[1])>.0005 or not (pa[1]<.26 or pa[1]>h-.1)):
                continue
            d=[pb[q]-pa[q] for q in range(3)]
            length=math.sqrt(sum(t*t for t in d))
            if length<1e-5: continue
            t=[v/length for v in d]
            na=normals[a];nb=normals[b]
            n=[na[q]+nb[q] for q in range(3)]
            ln=math.sqrt(sum(v*v for v in n))
            if ln<.0001: continue
            n=[v/ln for v in n]
            side=[t[1]*n[2]-t[2]*n[1],t[2]*n[0]-t[0]*n[2],t[0]*n[1]-t[1]*n[0]]
            ls=math.sqrt(sum(v*v for v in side))
            if ls<.0001:continue
            side=[v/ls for v in side]
            base=len(line_verts)
            for p,nn in ((pa,na),(pb,nb)):
                for sign in (-1,1):
                    line_verts.append(tuple(p[q]+nn[q]*.012 + side[q]*thickness*sign for q in range(3)))
                    line_normals.append(tuple(n))
            line_faces.extend([base,base+1,base+2,base+1,base+3,base+2])
        if line_verts:
            lp=write(line_verts,3,5126,34962)
            ln=write(line_normals,3,5126,34962)
            li=write(line_faces,1,5125,34963)
            js["meshes"][0]["primitives"].append({"attributes":{"POSITION":lp,"NORMAL":ln},"indices":li,"material":1})
            edge_color=[.012,.028,.040,1] if debug else [.12,.24,.33,.9]
            js["materials"].append({"name":"Wireframe mesh edges" if debug else "Visible glass rims","doubleSided":True,"pbrMetallicRoughness":{"baseColorFactor":edge_color,"metallicFactor":.16,"roughnessFactor":.4}})
        if debug:
            js["extensionsUsed"]=[]
            js["materials"][0]={"name":"Solid mesh beneath edges","doubleSided":True,"pbrMetallicRoughness":{"baseColorFactor":[.25,.66,.78,1.0],"metallicFactor":0,"roughnessFactor":.7}}
    if not debug:
        js["materials"][0]["extensions"]["KHR_materials_transmission"]["transmissionFactor"]=.68
        js["materials"][0]["extensions"]["KHR_materials_volume"]["thicknessFactor"]=.06
        js["materials"][0]["pbrMetallicRoughness"]["roughnessFactor"]=.075
    js['buffers'][0]['byteLength']=len(buf)
    jsb=json.dumps(js,separators=(',',':')).encode()
    while len(jsb)%4:jsb+=b' '
    while len(buf)%4:buf.append(0)
    chunk=struct.pack('<I4s',len(jsb),b'JSON')+jsb+struct.pack('<I4s',len(buf),b'BIN\\0'.replace(b'\\0',b'\x00'))+buf
    glb=struct.pack('<4sII',b'glTF',2,12+len(chunk))+chunk
    (root/f'{name}_{lod}{"_debug" if debug else ""}.glb').write_bytes(glb)
for name in names:
  for q in ('low','medium','high'):
    make(name,q)
    make(name,q,True)
print('Created',len(list(root.glob('*.glb'))),'meshes')
