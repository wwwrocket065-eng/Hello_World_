#!/usr/bin/env python3
"""Check ZIP alignment required for Android resource installation (API 30+)."""
import sys,struct,zipfile
from pathlib import Path

def verify(path):
    apk=Path(path)
    with zipfile.ZipFile(apk) as z, apk.open("rb") as source:
        assert z.testzip() is None, "Invalid APK ZIP"
        entries=z.namelist()
        assert len(entries)==len(set(entries)), "Duplicate entries"
        item=z.getinfo("resources.arsc")
        assert item.compress_type==zipfile.ZIP_STORED, "resources.arsc compressed"
        for info in z.infolist():
            if info.compress_type!=zipfile.ZIP_STORED: continue
            source.seek(info.header_offset)
            header=source.read(30)
            assert header[:4]==bytes.fromhex("504b0304"), "Bad ZIP local header"
            name_length,extra_length=struct.unpack_from("<HH",header,26)
            offset=info.header_offset+30+name_length+extra_length
            assert offset % 4==0, f"Unaligned ZIP member: {info.filename} at {offset}"
        print("APK resources alignment: PASS",apk)

if __name__=="__main__":
    if len(sys.argv)!=2: raise SystemExit("Usage: verify_apk_alignment.py APK")
    verify(sys.argv[1])
