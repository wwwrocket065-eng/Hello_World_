"""Sign CI APK with the user's private durable identity; no key material in repository.
Usage: python3 tools/sign_delivery.py APKSIGNER_JAR INPUT_APK KEY_DIRECTORY OUTPUT_APK
Key directory contains arknights-assistant.p12 and password.txt from private backup.
"""
import pathlib,subprocess,sys
jar,source,keys,target=map(pathlib.Path,sys.argv[1:])
subprocess.run(['java','-jar',str(jar),'sign','--ks',str(keys/'arknights-assistant.p12'),'--ks-key-alias','arknights-assistant','--ks-pass','file:'+str(keys/'password.txt'),'--out',str(target),str(source)],check=True)
subprocess.run(['java','-jar',str(jar),'verify','--verbose','--print-certs',str(target)],check=True)
