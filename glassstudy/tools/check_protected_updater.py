#!/usr/bin/env python3
"""CI guard against accidental changes to GlassStudy's cryptographic updater core.

Repairable Android storage integration files are intentionally NOT frozen while
v0.2.69 installation is blocked. Freeze those AFTER user confirms a working APK.
"""
from pathlib import Path
import hashlib
import sys

ROOT = Path(__file__).resolve().parents[2]
FROZEN = {
    "glassstudy/app/src/main/java/com/glassstudy/filament/PatchManager.kt":
        "969f0859cdb7a6247d0c17adf58377ede5a4dc72",
    "glassstudy/app/src/main/java/com/glassstudy/filament/PatchActivationCoordinator.kt":
        "10ef0bd13227ac241e7fecd21713d787d9ad403b",
    "glassstudy/app/build.gradle":
        "470e4e747c7782965062196a2fe46249c807ac1a",
}

def git_blob_sha1(content: bytes) -> str:
    header = b"blob " + str(len(content)).encode("ascii") + b"\\0"
    return hashlib.sha1(header + content).hexdigest()

def main() -> int:
    errors = []
    for relative_path, expected in FROZEN.items():
        file = ROOT / relative_path
        if not file.is_file():
            errors.append("missing: " + relative_path)
        elif git_blob_sha1(file.read_bytes()) != expected:
            errors.append("unauthorized change: " + relative_path)

    if errors:
        for error in errors:
            print("BLOCKED:", error)
        print("Update frozen references ONLY following explicit user approval.")
        return 1

    print("PASS: patch signature / rollback / APK-signing implementation unchanged")
    return 0

if __name__ == "__main__":
    sys.exit(main())
