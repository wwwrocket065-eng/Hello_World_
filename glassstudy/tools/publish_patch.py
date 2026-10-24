#!/usr/bin/env python3
"""Create signed model and declarative UI .pupdate plus latest.json for GlassStudy 0.2.13+.

Never include private/patch_key.pem, keystore, or KEY_BACKUP in a source/release archive.
Example:
 python tools/publish_patch.py --key-backup /secure/GlassStudy_PRIVATE_Keys_Backup.zip \
   --version 20011 --models tumbler_low.glb --output updates
"""
import argparse, base64, hashlib, json, re, zipfile
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

PATTERN = re.compile(r"(faceted|tumbler|wine|goblet|mug|pitcher|bowl|vase|sphere|cube|cylinder|cone)_(low|medium|high|ultra)(_debug)?\.glb")
FORMAT = "glassstudy-filament-assets-v2"
FEED = "glassstudy-filament-feed-v2"

def canonical(version, entries):
    return (FORMAT+"\n"+str(version)+"\n"+
            "".join(f"{x['path']}\t{x['sha256']}\n" for x in sorted(entries,key=lambda e:e['path']))).encode("utf-8")

def package(version, models_dir, names, private_key, ui_file=None):
    records=[]
    for name in sorted(set(names)):
        if not PATTERN.fullmatch(name): raise ValueError(f"Unexpected filename: {name}")
        raw=(models_dir/name).read_bytes()
        if len(raw)>16*1024*1024:raise ValueError('Model too large')
        records.append(dict(path='models/'+name,sha256=hashlib.sha256(raw).hexdigest(),data=base64.b64encode(raw).decode('ascii')))
    if ui_file is not None:
        raw=ui_file.read_bytes()
        if not 64<=len(raw)<=32768:raise ValueError('UI layout too large or missing')
        layout=json.loads(raw)
        if layout.get('format')!='glassstudy-ui-v1' or layout.get('schema')!=1:
            raise ValueError('Invalid UI schema')
        records.append(dict(path='ui/layout.json',sha256=hashlib.sha256(raw).hexdigest(),
                            data=base64.b64encode(raw).decode('ascii')))
    if not records or len(records)>120:raise ValueError('Invalid patch contents')
    sig=private_key.sign(canonical(version,records),padding.PKCS1v15(),hashes.SHA256())
    obj=dict(format=FORMAT,version=version,entries=records,signature=base64.b64encode(sig).decode('ascii'))
    return json.dumps(obj,ensure_ascii=False,separators=(',',':')).encode('utf-8')

def verify(patch, pubkey):
    obj=json.loads(patch)
    if obj['format']!=FORMAT:raise ValueError('Bad patch type')
    for e in obj['entries']:
        raw=base64.b64decode(e['data'],validate=True)
        if hashlib.sha256(raw).hexdigest()!=e['sha256']:raise ValueError('Corrupt model SHA')
        if e['path'] != 'ui/layout.json' and not (e['path'].startswith('models/') and PATTERN.fullmatch(e['path'].removeprefix('models/'))):raise ValueError('Bad path')
    pubkey.verify(base64.b64decode(obj['signature'],validate=True),canonical(obj['version'],obj['entries']),padding.PKCS1v15(),hashes.SHA256())
    return obj

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--version',type=int,required=True)
    ap.add_argument('--models-dir',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/assets/models')
    ap.add_argument('--models',nargs='*',default=[])
    ap.add_argument('--ui',type=Path,help='Optional declarative ui/layout.json file')
    ap.add_argument('--key-backup',type=Path,required=True)
    ap.add_argument('--output',type=Path,default=Path('updates'))
    ap.add_argument('--apk',type=Path,help='Optional signed release APK.')
    ap.add_argument('--apk-version-code',type=int,help='Required with --apk; must match its Android manifest.')
    args=ap.parse_args()
    if args.version<=20009:ap.error('Patch version must be higher than the built-in baseline (20009)')
    with zipfile.ZipFile(args.key_backup) as z:
        private=serialization.load_pem_private_key(z.read('private/patch_key.pem'),None)
    if not args.models and not args.ui:ap.error("Provide --models and/or --ui")
    patch=package(args.version,args.models_dir,args.models,private,args.ui)
    verify(patch,private.public_key())
    args.output.mkdir(parents=True,exist_ok=True)
    name=f'GlassStudy_Filament_{args.version}.pupdate'
    (args.output/name).write_bytes(patch)
    feed=dict(format=FEED,patchVersion=args.version,patchName=name,patchSha256=hashlib.sha256(patch).hexdigest())
    if args.apk:
        if args.apk_version_code is None:ap.error('--apk-version-code required with --apk')
        if not args.apk.name.endswith('.apk'):ap.error('APK extension expected')
        # Distinct APK version may differ from asset patch version; caller must validate it independently.
        feed.update(apkVersionCode=args.apk_version_code,apkName=args.apk.name,apkSha256=hashlib.sha256(args.apk.read_bytes()).hexdigest())
        (args.output/args.apk.name).write_bytes(args.apk.read_bytes())
    (args.output/'latest.json').write_text(json.dumps(feed,separators=(',',':'))+'\n')
    print('Signed patch and feed generated:',name,'(publish patch BEFORE latest.json)')

if __name__=='__main__':main()