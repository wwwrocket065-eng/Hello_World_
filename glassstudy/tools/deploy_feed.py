#!/usr/bin/env python3
"""Publisher for new v2 updates: uploads patch and optional APK first, latest.json LAST.
Uses a GitHub token from GH_TOKEN environment; never puts it into files or logs.
This script is intended to run in CI/CD or a controlled developer machine.
"""
import argparse,base64,json,os,re,urllib.request,urllib.error
from pathlib import Path

def github_request(method,path,body=None):
    token=os.environ.get('GH_TOKEN')
    if not token:raise SystemExit('Set GH_TOKEN with repo contents write permission')
    url='https://api.github.com/repos/wwwrocket065-eng/Hello_World_/contents/'+path
    url+='?ref=glassstudy-filament-ci' if method=='GET' else ''
    req=urllib.request.Request(url,method=method,headers={
        'Accept':'application/vnd.github+json','Authorization':'Bearer '+token,
        'X-GitHub-Api-Version':'2022-11-28','User-Agent':'GlassStudy-deploy/2'},
        data=json.dumps(body).encode('utf-8') if body else None)
    with urllib.request.urlopen(req,timeout=25) as r:return json.load(r)

def publish(path,contents):
    try:sha=github_request('GET',path)['sha']
    except urllib.error.HTTPError as e:
        if e.code!=404:raise
        sha=None
    body=dict(message='GlassStudy automatic update: '+path.rsplit('/',1)[-1],
              content=base64.b64encode(contents).decode('ascii'),branch='glassstudy-filament-ci')
    if sha:body['sha']=sha
    github_request('PUT',path,body)
    print('Published:',path)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('updates_dir',type=Path)
    args=ap.parse_args()
    path=args.updates_dir
    feed=json.loads((path/'latest.json').read_text('utf-8'))
    if feed.get('format')!='glassstudy-filament-feed-v2':ap.error('Wrong feed version')
    patch=feed['patchName']
    if patch != f"GlassStudy_Filament_{feed['patchVersion']}.pupdate":ap.error('Invalid patch name')
    import hashlib
    payload=(path/patch).read_bytes()
    if hashlib.sha256(payload).hexdigest()!=feed['patchSha256']:ap.error('Patch checksum mismatch')
    base='glassstudy/updates/v2/'
    publish(base+patch,payload)
    if feed.get('apkName'):
        apk=feed['apkName']
        if not re.fullmatch(r'GlassStudy_Filament_v[\d.]+\.apk',apk):ap.error('Invalid APK name')
        raw=(path/apk).read_bytes()
        if hashlib.sha256(raw).hexdigest()!=feed['apkSha256']:ap.error('APK checksum mismatch')
        publish(base+apk,raw)
    # Consumers see latest version only after all binaries are available.
    publish(base+'latest.json',(path/'latest.json').read_bytes())

if __name__=='__main__':main()