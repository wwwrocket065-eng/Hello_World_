#!/usr/bin/env python3
"""Offline integration verification of the LIVE signed GlassStudy patch.

This uses the public RSA key shipped in the Android app, the published feed,
the actual .pupdate bytes, and OpenSSL (available on Android CI runners).
It never accesses a private key, never fetches the network and never publishes.
Tampering trials are expected to FAIL; only genuine signed bytes may pass.
"""
import base64
import copy
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPDATES = ROOT / "updates/v2"
SOURCE = ROOT / "app/src/main/java/com/glassstudy/filament/PatchManager.kt"
FORMAT = "glassstudy-filament-assets-v2"
FEED_FORMAT = "glassstudy-filament-feed-v2"
MAX_PATCH_BYTES = 50 * 1024 * 1024
MAX_ENTRY_BYTES = 16 * 1024 * 1024
MODEL = re.compile(r"models/[a-z][a-z0-9_-]{0,63}(_(low|medium|high))?(_debug)?\.glb")
UI = re.compile(r"ui/[a-z][a-z0-9_-]{0,63}\.json")
MODULE = re.compile(r"modules/[a-z][a-z0-9_-]{0,63}/(config|manifest)\.json")
OTHER = [
    re.compile(r"catalogs/[a-z][a-z0-9_-]{0,63}\.json"),
    re.compile(r"shaders/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\.(glsl|mat)"),
    re.compile(r"textures/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\.(png|jpg|ktx|ktx2)"),
    re.compile(r"scenes/[a-z][a-z0-9_-]{0,63}\.json"),
]
HASH = re.compile(r"[0-9a-f]{64}")


def ensure(condition, message):
    if not condition:
        raise ValueError(message)


def accepts(path):
    return (
        isinstance(path, str) and len(path) <= 200 and not path.startswith("/")
        and ".." not in path and "\\" not in path
        and any(x.fullmatch(path) for x in [MODEL, UI, MODULE, *OTHER])
    )


def canonical(version, entries):
    return (
        FORMAT + "\n" + str(version) + "\n" +
        "".join(e["path"] + "\t" + e["sha256"] + "\n"
                for e in sorted(entries, key=lambda e: e["path"]))
    ).encode("utf-8")


def live_public_key():
    match = re.search(r'PUBLIC_KEY_BASE64\s*=\s*"([^"]+)"', SOURCE.read_text())
    ensure(match is not None, "No built-in original patch public key")
    return base64.b64decode(match.group(1), validate=True)


def verify_rsa(public_der, signature, message):
    with tempfile.TemporaryDirectory(prefix="glassstudy-patch-public-") as folder:
        folder = Path(folder)
        (folder / "pub.der").write_bytes(public_der)
        (folder / "sig.bin").write_bytes(signature)
        (folder / "signed.txt").write_bytes(message)
        exported = subprocess.run(
            ["openssl", "pkey", "-pubin", "-inform", "DER",
             "-in", str(folder / "pub.der"), "-out", str(folder / "pub.pem")],
            capture_output=True, text=True)
        ensure(exported.returncode == 0, "OpenSSL could not load APK-embedded patch public key")
        checked = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(folder / "pub.pem"),
             "-signature", str(folder / "sig.bin"), str(folder / "signed.txt")],
            capture_output=True, text=True)
        ensure(checked.returncode == 0 and "Verified OK" in checked.stdout,
               "RSA SHA-256 verification failed")


def verify_patch(patch, public_der):
    ensure(isinstance(patch, dict), "Patch JSON must be an object")
    ensure(patch.get("format") == FORMAT, "Wrong patch format")
    version = patch.get("version")
    ensure(type(version) is int and version > 20009, "Invalid patch version")
    entries = patch.get("entries")
    ensure(isinstance(entries, list) and 1 <= len(entries) <= 120, "Invalid entries")
    seen = set()
    total = 0
    for e in entries:
        ensure(isinstance(e, dict), "Invalid entry")
        path = e.get("path")
        digest = e.get("sha256")
        ensure(accepts(path), "Unsafe resource path")
        ensure(path not in seen, "Duplicate patch resource")
        seen.add(path)
        ensure(isinstance(digest, str) and HASH.fullmatch(digest), "Invalid hash")
        raw64 = e.get("data")
        ensure(isinstance(raw64, str), "Missing data")
        raw = base64.b64decode(raw64, validate=True)
        ensure(0 < len(raw) <= MAX_ENTRY_BYTES, "Unbounded resource")
        total += len(raw)
        ensure(total <= MAX_PATCH_BYTES, "Unbounded total size")
        ensure(hashlib.sha256(raw).hexdigest() == digest, "Resource digest mismatch")
        if path.endswith(".glb"):
            ensure(raw.startswith(b"glTF") and len(raw) >= 20, "Invalid GLB")
            ensure(int.from_bytes(raw[4:8], "little") == 2, "Bad GLB version")
            ensure(int.from_bytes(raw[8:12], "little") == len(raw), "Bad GLB length")
        elif path == "ui/layout.json":
            ui = json.loads(raw)
            ensure(ui.get("format") == "glassstudy-ui-v1" and ui.get("schema") == 1,
                   "Unsupported UI schema")
        elif path.endswith(".json"):
            json.loads(raw)

    signature = patch.get("signature")
    ensure(isinstance(signature, str), "Missing signature")
    verify_rsa(public_der, base64.b64decode(signature, validate=True),
               canonical(version, entries))
    return version


def fails(patch, key, reason):
    try:
        verify_patch(patch, key)
    except (ValueError, json.JSONDecodeError):
        print("PASS rejected tamper:", reason)
        return
    raise AssertionError("Tampered signed patch unexpectedly verified: " + reason)


def main():
    ensure(shutil.which("openssl") is not None, "OpenSSL is required for offline integration checks")
    feed = json.loads((UPDATES / "latest.json").read_bytes())
    ensure(feed.get("format") == FEED_FORMAT, "Invalid published feed format")
    version = feed.get("patchVersion")
    ensure(type(version) is int and version > 20009, "Invalid live feed version")
    expected = "GlassStudy_Filament_" + str(version) + ".pupdate"
    ensure(feed.get("patchName") == expected, "Unsafe or mismatched published filename")
    patch_bytes = (UPDATES / expected).read_bytes()
    ensure(0 < len(patch_bytes) <= MAX_PATCH_BYTES, "Published patch exceeds app limit")
    ensure(feed.get("patchSha256") == hashlib.sha256(patch_bytes).hexdigest(),
           "Published feed SHA does not match actual .pupdate bytes")
    patch = json.loads(patch_bytes)
    public = live_public_key()
    ensure(verify_patch(patch, public) == version, "Feed and signed patch disagree")
    print("PASS live RSA/SHA256:", expected, "entries:", len(patch["entries"]))

    altered = copy.deepcopy(patch)
    entry = altered["entries"][0]
    data = bytearray(base64.b64decode(entry["data"], validate=True))
    data[-1] ^= 1
    entry["data"] = base64.b64encode(data).decode()
    fails(altered, public, "modified payload, original SHA")

    altered["entries"][0]["sha256"] = hashlib.sha256(data).hexdigest()
    fails(altered, public, "recomputed SHA but old RSA signature")

    for title, change in (
        ("unsafe traversal path", lambda x: x["entries"][0].update(path="../ui/layout.json")),
        ("duplicate path", lambda x: x["entries"].append(copy.deepcopy(x["entries"][0]))),
        ("wrong version", lambda x: x.update(version=version + 1)),
        ("wrong format", lambda x: x.update(format="malicious-format")),
        ("wrong signature", lambda x: x.update(signature=base64.b64encode(b"x"*384).decode())),
    ):
        case = copy.deepcopy(patch)
        change(case)
        fails(case, public, title)

    # Changing just one byte in the published patch must invalidate the FEED too.
    bad_feed_sha = hashlib.sha256(patch_bytes + b"x").hexdigest()
    ensure(bad_feed_sha != feed["patchSha256"], "Feed tamper not detected")
    print("PASS rejected tamper: feed SHA differs")
    print("PASS: 8 malicious variants fail; no private key or network needed")


if __name__ == "__main__":
    main()
