#!/usr/bin/env python3
"""Verify inline JavaScript parses before packaging the Android WebView APK."""
from pathlib import Path
import re
import subprocess
import tempfile

html = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "index.html"
scripts = re.findall(r"<script[^>]*>(.*?)</script>", html.read_text(encoding="utf-8"), flags=re.S | re.I)
if not scripts:
    raise SystemExit("ERROR: no inline JavaScript found")
for script in scripts:
    with tempfile.NamedTemporaryFile(mode="w", suffix=".js", encoding="utf-8") as f:
        f.write(script)
        f.flush()
        subprocess.run(["node", "--check", f.name], check=True)
print(f"Inline JS parse OK: {len(scripts)} script(s)")
