#!/usr/bin/env python3
"""Fast signed declarative GlassStudy patches; NO Gradle, APK, or executable updates.

Examples:
  python tools/fast_patch.py --version 20030 --set ui.topButtonSizeDp=32 --set ui.gearInnerRoundness=1.0 --key-backup /secure/GlassStudy_PRIVATE_Keys_Backup.zip
  python tools/fast_patch.py --version 20031 --set camera.lightIntensity=45000 --set render.iblIntensity=30000 --key-backup /secure/GlassStudy_PRIVATE_Keys_Backup.zip
  python tools/fast_patch.py --version 20032 --file textures/custom/wood.png=./wood.png --key-backup /secure/GlassStudy_PRIVATE_Keys_Backup.zip

Only changed signed resource files are included. The Android app's existing
module validators remain authoritative. Existing installed resources are
carried forward by PatchManager.stage(), never re-packed here.
"""
import argparse
import base64
import hashlib
import json
import re
import zipfile
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets"
FORMAT="glassstudy-filament-assets-v2"
FEED="glassstudy-filament-feed-v2"
MODULES=("camera","render","diagnostics","materials","scene","windows","overlay")
ALIASES={"ui":"ui/layout.json",**{m:"modules/"+m+"/config.json" for m in MODULES}}
MAX_TOTAL=50*1024*1024
MAX_SINGLE=16*1024*1024
PATHS=(
    r"ui/[a-z][a-z0-9_-]{0,63}\.json",
    r"modules/[a-z][a-z0-9_-]{0,63}/(config|manifest)\.json",
    r"catalogs/[a-z][a-z0-9_-]{0,63}\.json",
    r"scenes/[a-z][a-z0-9_-]{0,63}\.json",
    r"models/[a-z][a-z0-9_-]{0,63}(_(low|medium|high))?(_debug)?\.glb",
    r"shaders/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\.(glsl|mat)",
    r"textures/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\.(png|jpg|ktx|ktx2)"
)

def accepted(name):
    return (isinstance(name,str) and len(name)<=200 and not name.startswith("/")
            and ".." not in name and "\\" not in name
            and any(re.fullmatch(p,name) for p in PATHS))

def canonical(version,entries):
    return (FORMAT+"\n"+str(version)+"\n"+
        "".join(x["path"]+"\t"+x["sha256"]+"\n" for x in sorted(entries,key=lambda v:v["path"]))
    ).encode("utf-8")

def encode_value(value):
    try:return json.loads(value)
    except json.JSONDecodeError:return value

def config_resource(alias,baseline):
    path=ALIASES.get(alias)
    if path is None:raise ValueError("Unknown module alias: "+alias)
    source=ASSETS/path
    if not source.is_file():raise ValueError("Default resource not found: "+str(source))
    doc=json.loads(baseline.get(path,source.read_bytes()))
    if not isinstance(doc,dict):raise ValueError("Configuration must be an object")
    # Older signed layouts omit newly introduced optional keys. Merge ONLY
    # missing keys from the APK defaults; preserve every previously patched value.
    defaults=json.loads(source.read_bytes())
    def fill_missing(target,origin):
        for key,value in origin.items():
            if key not in target:target[key]=value
            elif isinstance(value,dict) and isinstance(target[key],dict):
                fill_missing(target[key],value)
    fill_missing(doc,defaults)
    if path=="ui/layout.json":
        templates={b["id"]:b for b in defaults.get("uiButtons",[])}
        for button in doc.get("uiButtons",[]):
            for key,value in templates.get(button["id"],{}).items():
                button.setdefault(key,value)
    return path,doc

def assign(document,selector,value):
    """Existing JSON object keys only. No guessed API or unknown properties."""
    keys=selector.split(".")
    if not keys or any(not key for key in keys):raise ValueError("Invalid property path")
    target=document
    for key in keys[:-1]:
        if isinstance(target,dict) and key in target:
            target=target[key]
        else:raise ValueError("Unknown nested configuration key: "+selector)
    key=keys[-1]
    if not isinstance(target,dict) or key not in target:
        raise ValueError("Unknown configuration key: "+selector)
    previous=target[key]
    # Preserve number/bool/string/list/map types; prevent invalid nulls.
    if (type(value) is not type(previous)
        and not (type(previous) in (int,float) and type(value) in (int,float) and type(value) is not bool)):
        raise ValueError("Property type mismatch: "+selector)
    target[key]=value

BUTTON_LIMITS={
    "widthDp":(44,260),"heightDp":(36,72),"visualSizeDp":(20,72),
    "iconScale":(.55,1),"offsetXDp":(-80,80),"offsetYDp":(-80,80),
    "marginStartDp":(-16,32),"marginTopDp":(-16,32)
}

def set_button(document,change):
    if "=" not in change:raise ValueError("Use --button ID.FIELD=VALUE")
    key,raw=change.split("=",1)
    identifier,dot,field=key.partition(".")
    if not dot or field not in BUTTON_LIMITS:raise ValueError("Invalid button field: "+key)
    buttons=[b for b in document.get("uiButtons",[]) if b.get("id")==identifier]
    if len(buttons)!=1:raise ValueError("Unknown button ID: "+identifier)
    value=encode_value(raw)
    low,high=BUTTON_LIMITS[field]
    if type(value) not in (int,float) or not low<=value<=high:
        raise ValueError("Button value outside safe bounds: "+key)
    if field in ("widthDp","heightDp","visualSizeDp") and type(value) is not int:
        raise ValueError("Button size must be whole dp: "+key)
    buttons[0][field]=value

def validate(name,raw):
    if not accepted(name):raise ValueError("Resource not allowed by Android: "+name)
    if not 1<=len(raw)<=MAX_SINGLE:raise ValueError("Resource size invalid: "+name)
    if name.endswith(".glb"):
        if len(raw)<20 or raw[:4]!=b"glTF" or int.from_bytes(raw[4:8],"little")!=2 or int.from_bytes(raw[8:12],"little")!=len(raw):
            raise ValueError("Invalid GLB: "+name)
    if name.endswith(".json"):
        obj=json.loads(raw)
        if not isinstance(obj,dict):raise ValueError("JSON resource must be an object: "+name)
        if name=="ui/layout.json" and (obj.get("format")!="glassstudy-ui-v1" or obj.get("schema")!=1):
            raise ValueError("Unsupported UI schema")
        if name.startswith("modules/"):
            parts=name.split("/")
            if obj.get("schema",1) not in (1,2):raise ValueError("Unsupported module schema")
            if "id" in obj and obj["id"]!=parts[1]:raise ValueError("Wrong module ID")
    if name.endswith((".glsl",".mat")) and b"\0" in raw:
        raise ValueError("Binary shader source not allowed")

def load_signer(backup):
    # Original PATCH RSA key only; APK signing key is a different credential.
    with zipfile.ZipFile(backup) as archive:
        private=serialization.load_pem_private_key(archive.read("private/patch_key.pem"),None)
    return private

def create_patch(version,items,key):
    if not 20009<version<2147483647:raise ValueError("Invalid patch version")
    if not 1<=len(items)<=120:raise ValueError("Invalid changed-file count")
    records=[]
    total=0
    for name in sorted(items):
        raw=items[name]
        validate(name,raw)
        total+=len(raw)
        if total>MAX_TOTAL:raise ValueError("Patch too large")
        records.append(dict(path=name,sha256=hashlib.sha256(raw).hexdigest(),
                            data=base64.b64encode(raw).decode("ascii")))
    signature=key.sign(canonical(version,records),padding.PKCS1v15(),hashes.SHA256())
    payload=json.dumps(dict(format=FORMAT,version=version,entries=records,
                            signature=base64.b64encode(signature).decode("ascii")),
                       ensure_ascii=False,separators=(",",":")).encode("utf-8")
    key.public_key().verify(signature,canonical(version,records),padding.PKCS1v15(),hashes.SHA256())
    return payload

def published_baseline():
    """Resolve ALL prior published incremental patch resources, oldest to newest.

    Prevents the very common bug where editing one size with bundled defaults
    silently undoes unrelated colors, menus or earlier module customizations.
    Verify each archive against the APK's embedded PATCH public RSA key.
    """
    from cryptography.hazmat.primitives.serialization import load_der_public_key
    source=(ROOT/"app/src/main/java/com/glassstudy/filament/PatchManager.kt").read_text()
    matched=re.search(r'PUBLIC_KEY_BASE64\s*=\s*"([^"]+)"',source)
    if not matched:raise ValueError("Missing Android patch public key")
    pub=serialization.load_der_public_key(base64.b64decode(matched.group(1)))
    updates=ROOT/"updates/v2"
    feed=json.loads((updates/"latest.json").read_text("utf-8"))
    published=feed["patchVersion"]
    current_name=feed["patchName"]
    latest_raw=(updates/current_name).read_bytes()
    if hashlib.sha256(latest_raw).hexdigest()!=feed["patchSha256"]:
        raise ValueError("Published feed checksum does not match patch")
    resources={}
    version=0
    for path in sorted(updates.glob("GlassStudy_Filament_*.pupdate"),
                       key=lambda p:int(p.stem.split("_")[-1])):
        doc=json.loads(path.read_bytes())
        if doc.get("version",0)>published:continue
        if doc.get("format")!=FORMAT:raise ValueError("Bad patch format: "+path.name)
        version=doc["version"]
        entries=doc["entries"]
        sig=base64.b64decode(doc["signature"],validate=True)
        pub.verify(sig,canonical(version,entries),padding.PKCS1v15(),hashes.SHA256())
        for e in entries:
            name=e["path"]
            if not accepted(name):raise ValueError("Unsupported published path "+name)
            raw=base64.b64decode(e["data"],validate=True)
            if hashlib.sha256(raw).hexdigest()!=e["sha256"]:
                raise ValueError("Published resource hash mismatch "+name)
            resources[name]=raw
    if version!=published:raise ValueError("Published patch history incomplete")
    return published,resources


def produce(args):
    current_version,baseline=published_baseline()
    if args.version<=current_version:
        raise ValueError("New patch version must exceed live published "+str(current_version))
    files={}
    base={}
    for item in args.set:
        if "=" not in item:raise ValueError("--set requires module.property=value")
        lhs,value=item.split("=",1)
        alias,dot,selector=lhs.partition(".")
        if not dot:raise ValueError("Expected module.property")
        path=ALIASES.get(alias)
        if path is None:raise ValueError("Unknown module: "+alias)
        if path not in base:base[path]=config_resource(alias,baseline)[1]
        assign(base[path],selector,encode_value(value))
    if args.button:
        path="ui/layout.json"
        if path not in base:base[path]=config_resource("ui",baseline)[1]
        for declaration in args.button:set_button(base[path],declaration)
    for path,doc in base.items():
        raw=(json.dumps(doc,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
        # No change, no need to ship it.
        if raw!=baseline.get(path,(ASSETS/path).read_bytes()):files[path]=raw

    for declaration in args.file:
        if "=" not in declaration:raise ValueError("--file requires resource/path=local_file")
        name,source=declaration.split("=",1)
        if name in files:raise ValueError("Resource supplied twice: "+name)
        source=Path(source)
        if not source.is_file():raise ValueError("Missing file "+source.as_posix())
        data=source.read_bytes()
        # Skip the unchanged built-in file automatically.
        if data!=baseline.get(name,(ASSETS/name).read_bytes() if (ASSETS/name).is_file() else None):
            files[name]=data

    if not files:raise ValueError("No changed resources: patch omitted")
    if args.dry_run:
        print("DRY RUN — no key loaded, no patch written")
        for p in sorted(files):print("  ",p,len(files[p]),"bytes")
        return
    if args.key_backup is None:raise ValueError("--key-backup is required to sign a real patch")
    signer=load_signer(args.key_backup)
    content=create_patch(args.version,files,signer)
    directory=args.output
    directory.mkdir(parents=True,exist_ok=True)
    name=f"GlassStudy_Filament_{args.version}.pupdate"
    (directory/name).write_bytes(content)
    feed=dict(format=FEED,patchVersion=args.version,patchName=name,
              patchSha256=hashlib.sha256(content).hexdigest())
    (directory/"latest.json").write_text(json.dumps(feed,separators=(",",":"))+"\n","utf-8")
    print("Signed",name,"bytes",len(content),"changed files:",len(files))
    for name in sorted(files):print(" +",name)
    print("Feed is staged locally; publish only after review and compatibility check.")

def main():
    parser=argparse.ArgumentParser(description=__doc__,formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version",type=int,required=True)
    parser.add_argument("--set",action="append",default=[],metavar="MODULE.PROPERTY=VALUE")
    parser.add_argument("--file",action="append",default=[],metavar="RESOURCE=FILE")
    parser.add_argument("--button",action="append",default=[],metavar="ID.FIELD=VALUE")
    parser.add_argument("--key-backup",type=Path)
    parser.add_argument("--output",type=Path,default=ROOT/"updates/fast-staged")
    parser.add_argument("--dry-run",action="store_true")
    args=parser.parse_args()
    try:produce(args)
    except (ValueError,KeyError,OSError,zipfile.BadZipFile) as error:parser.error(str(error))

if __name__=="__main__":main()
