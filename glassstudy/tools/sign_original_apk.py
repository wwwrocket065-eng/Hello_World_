#!/usr/bin/env python3
"""Sign a GlassStudy CI APK using the ORIGINAL release identity.

Does not contain, create, upload or print signing secrets. The private key
must be supplied from a secure local backup, never committed to source.
Requires Java 17, OpenSSL and the official Android SDK apksigner.jar.

Example:
  python3 tools/sign_original_apk.py \
      --unsigned app-ciUnsigned-unsigned.apk \
      --output GlassStudy_3D_LAB_v0.2.31_ORIGINAL_KEY.apk \
      --key /secure/private/apk_key.pem \
      --cert /secure/private/apk_cert.der \
      --apksigner-jar /secure/tools/apksigner.jar
"""
import argparse
import hashlib
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ORIGINAL_APK_CERT_SHA256 = "35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc"

def checked(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return result.stdout + result.stderr

def data_offset(apk, entry: zipfile.ZipInfo) -> int:
    apk.seek(entry.header_offset + 26)
    sizes = apk.read(4)
    if len(sizes) != 4:
        raise ValueError("Truncated APK entry header")
    name_len, extra_len = struct.unpack("<HH", sizes)
    return entry.header_offset + 30 + name_len + extra_len

def inspect_zip_alignment(path: Path) -> None:
    with path.open("rb") as file, zipfile.ZipFile(file) as archive:
        seen = set()
        so_count = 0
        for entry in archive.infolist():
            if entry.filename in seen:
                raise ValueError("Duplicate APK ZIP entry")
            seen.add(entry.filename)
            if entry.filename == "resources.arsc":
                if entry.compress_type != zipfile.ZIP_STORED or data_offset(file, entry) % 4:
                    raise ValueError("resources.arsc must be stored and 4-byte aligned")
            if entry.filename.startswith("lib/") and entry.filename.endswith(".so"):
                so_count += 1
                if entry.compress_type != zipfile.ZIP_STORED:
                    raise ValueError("Native library is compressed")
                if data_offset(file, entry) % 16384:
                    raise ValueError("Native library is not 16 KiB aligned")
        if "resources.arsc" not in seen or so_count == 0:
            raise ValueError("Missing required Android resources or libraries")

def verify_payload_unchanged(source: Path, signed: Path) -> None:
    with zipfile.ZipFile(source) as a, zipfile.ZipFile(signed) as b:
        if set(a.namelist()) != set(b.namelist()):
            raise ValueError("APK content entries changed during signing")
        for name in a.namelist():
            if hashlib.sha256(a.read(name)).digest() != hashlib.sha256(b.read(name)).digest():
                raise ValueError("APK payload changed: " + name)
        if b.testzip() is not None:
            raise ValueError("Signed APK ZIP integrity check failed")

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--key", type=Path, required=True, help="Secure original private RSA key PEM or DER PKCS#8")
    parser.add_argument("--cert", type=Path, required=True, help="Original signing certificate (DER)")
    parser.add_argument("--apksigner-jar", type=Path, required=True)
    args = parser.parse_args()
    unsigned = args.unsigned.resolve(strict=True)
    key = args.key.resolve(strict=True)
    cert = args.cert.resolve(strict=True)
    signer = args.apksigner_jar.resolve(strict=True)
    output = args.output.resolve()
    if unsigned == output:
        raise ValueError("Never overwrite the unsigned build")
    if output.exists():
        raise FileExistsError("Output exists: " + str(output))
    if hashlib.sha256(cert.read_bytes()).hexdigest() != ORIGINAL_APK_CERT_SHA256:
        raise ValueError("Signing certificate differs from installed GlassStudy")
    inspect_zip_alignment(unsigned)
    with tempfile.TemporaryDirectory(prefix="glassstudy-sign-") as folder:
        tmp = Path(folder)
        os.chmod(tmp, 0o700)
        key_der = tmp / "private-key.pk8"
        # OpenSSL accepts traditional PEM, PKCS#8 PEM and DER PKCS#8.
        if key.suffix.lower() in {".pem", ".key"}:
            checked(["openssl", "pkcs8", "-topk8", "-inform", "PEM",
                "-outform", "DER", "-nocrypt", "-in", str(key), "-out", str(key_der)])
            os.chmod(key_der, 0o600)
        else:
            key_der = key
        try:
            checked(["java", "-jar", str(signer), "sign",
                "--key", str(key_der), "--cert", str(cert),
                "--min-sdk-version", "26", "--v1-signing-enabled", "false",
                "--v2-signing-enabled", "true", "--v3-signing-enabled", "false",
                "--out", str(output), str(unsigned)])
            result = checked(["java", "-jar", str(signer), "verify",
                "--min-sdk-version", "26", "--print-certs", "-v", str(output)])
            if "Verified using v2 scheme (APK Signature Scheme v2): true" not in result:
                raise ValueError("APK v2 verification failed")
            if ORIGINAL_APK_CERT_SHA256 not in result.lower():
                raise ValueError("Result was signed with a different identity")
            verify_payload_unchanged(unsigned, output)
            inspect_zip_alignment(output)
        except BaseException:
            output.unlink(missing_ok=True)
            raise
    print("SIGNED VERIFIED GlassStudy APK:", output)
    print("SHA256:", hashlib.sha256(output.read_bytes()).hexdigest())
    print("Signer certificate: original GlassStudy identity")
    print("Scheme: APK Signature v2; payload and 16 KiB alignment verified")

if __name__ == "__main__":
    main()
