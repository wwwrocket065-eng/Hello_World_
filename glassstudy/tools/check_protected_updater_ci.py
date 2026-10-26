#!/usr/bin/env python3
"""Verify unchanged critical GlassStudy updater and signing modules in CI."""
from pathlib import Path
import hashlib
import sys

root = Path(__file__).resolve().parents[2]
frozen = {
    "glassstudy/app/src/main/java/com/glassstudy/filament/PatchManager.kt":
        "969f0859cdb7a6247d0c17adf58377ede5a4dc72",
    "glassstudy/app/src/main/java/com/glassstudy/filament/PatchActivationCoordinator.kt":
        "10ef0bd13227ac241e7fecd21713d787d9ad403b",
    "glassstudy/app/build.gradle":
        "470e4e747c7782965062196a2fe46249c807ac1a",
}
failures = []
for rel, expected in frozen.items():
    source = root / rel
    if not source.is_file():
        failures.append(f"Missing protected module: {rel}")
        continue
    content = source.read_bytes()
    git_blob = b"blob " + str(len(content)).encode("ascii") + bytes([0]) + content
    if hashlib.sha1(git_blob).hexdigest() != expected:
        failures.append(f"Protected module changed: {rel}")
if failures:
    for failure in failures:
        print("BLOCKED:", failure)
    sys.exit(1)
print("PASS: critical GlassStudy updater, rollback, APK signing unchanged")
