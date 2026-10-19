#!/usr/bin/env python3
"""Verify CI's APK is the correct, UNsigned Android package for original-key signing.

No private key is used or exposed. This script cannot make an installable APK.
"""
import os
import re
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROPS = dict(x.split("=", 1) for x in (ROOT / "gradle.properties").read_text().splitlines()
             if x.startswith("GLASSSTUDY_") and "=" in x)
BUILD_TOOLS = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "35.0.0"
APK_DIR = ROOT / "app/build/outputs/apk/ciUnsigned"


def command(*args):
    return subprocess.run([str(x) for x in args], text=True, capture_output=True)


def offset(file, entry):
    file.seek(entry.header_offset + 26)
    values = file.read(4)
    if len(values) != 4:
        raise AssertionError("Truncated ZIP local header")
    name, extra = struct.unpack("<HH", values)
    return entry.header_offset + 30 + name + extra


def main():
    apks = list(APK_DIR.glob("*.apk"))
    assert len(apks) == 1, f"Expected precisely one CI unsigned APK: {apks}"
    apk = apks[0]
    with apk.open("rb") as raw, zipfile.ZipFile(raw) as archive:
        assert archive.testzip() is None, "Corrupted APK ZIP"
        contents = archive.infolist()
        assert len({x.filename for x in contents}) == len(contents), "Duplicate APK entry"
        names = {x.filename for x in contents}
        assert "AndroidManifest.xml" in names and "resources.arsc" in names
        libraries = [x for x in contents if x.filename.startswith("lib/") and
                     x.filename.endswith(".so")]
        assert libraries, "Filament native libraries are missing"
        for entry in contents:
            if entry.filename == "resources.arsc":
                assert entry.compress_type == zipfile.ZIP_STORED, "Compressed resources.arsc"
                assert offset(raw, entry) % 4 == 0, "Unaligned Android resources"
            if entry in libraries:
                assert entry.compress_type == zipfile.ZIP_STORED, "Compressed JNI library"
                assert offset(raw, entry) % 16384 == 0, "JNI library needs 16 KiB alignment"

    zipalign = command(BUILD_TOOLS / "zipalign", "-c", "-P", "16", "4", apk)
    assert zipalign.returncode == 0, "Official zipalign failed: " + zipalign.stdout + zipalign.stderr
    aapt = command(BUILD_TOOLS / "aapt", "dump", "badging", apk)
    assert aapt.returncode == 0, "Android manifest could not be decoded"
    package = next((line for line in aapt.stdout.splitlines() if line.startswith("package: ")), "")
    assert "name='com.glassstudy.filament.stable'" in package, package
    assert "versionCode='" + PROPS["GLASSSTUDY_VERSION_CODE"] + "'" in package, package
    assert "versionName='" + PROPS["GLASSSTUDY_VERSION"] + "'" in package, package

    signer = command(BUILD_TOOLS / "apksigner", "verify", "--verbose", apk)
    assert signer.returncode != 0, (
        "CI artifact is signed. NEVER distribute it as an unsigned release candidate."
    )
    print("PASS: Android ID + version + ZIP + 16 KiB native alignment")
    print("PASS: unsigned as expected; still requires the ORIGINAL GlassStudy key")
    print("APK:", apk.name)


if __name__ == "__main__":
    main()
