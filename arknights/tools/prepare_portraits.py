"""Bundle small, offline portraits for the current Global recruitment pool."""
import concurrent.futures
import io
import json
import re
import time
import unicodedata
import urllib.request
from pathlib import Path
from PIL import Image

SOURCE_REV = "0f5efc6352337e2d63f81c0d2ca6d469b2ce3d96"
BASE = f"https://raw.githubusercontent.com/yuanyan3060/ArknightsGameResource/{SOURCE_REV}/avatar/"
API = "https://awedtan.ca/api/"
OUT = Path("app/src/main/assets/operators")

def fetch(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ArknightsRecruitAssist/0.3.12"})
            with urllib.request.urlopen(req, timeout=30) as response:
                return response.read()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)

def load(url):
    return json.loads(fetch(url))

def key(name):
    return "".join(c for c in unicodedata.normalize("NFKC", name).casefold() if c.isalnum())

pool = set(load(API + "recruitpool")[0]["value"])
assert 100 <= len(pool) <= 400
roster = load(API + "operator?include=data.name")
characters = load("https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData_YoStar/main/en_US/gamedata/excel/character_table.json")
by_name = {key(d["name"]): ident for ident, d in characters.items() if d.get("name")}
selected = {}
for record in roster:
    canon = record.get("canon", "")
    if canon not in pool:
        continue
    name = record.get("value", {}).get("data", {}).get("name", "")
    assert name, f"Missing operator name: {canon}"
    ident = canon if re.fullmatch(r"char_\d+_[A-Za-z0-9]+", canon) else by_name.get(key(name))
    assert ident, f"No portrait ID for {name}"
    selected[name] = ident
assert len(selected) >= 100
OUT.mkdir(parents=True, exist_ok=True)

def portrait(item):
    name, ident = item
    data = fetch(BASE + ident + ".png")
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    image.thumbnail((72, 72), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (72, 72))
    canvas.alpha_composite(image, ((72 - image.width) // 2, (72 - image.height) // 2))
    canvas.save(OUT / (ident + ".webp"), "WEBP", quality=88, method=6)
    return name, ident

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
    index = dict(executor.map(portrait, selected.items()))
assert len(index) == len(selected)
(OUT / "avatars.json").write_text(json.dumps(index, ensure_ascii=False), encoding="utf-8")
(OUT / "SOURCE.txt").write_text(
    "Portraits: Arknights / Hypergryph. Client-resource mirror: "
    f"https://github.com/yuanyan3060/ArknightsGameResource at {SOURCE_REV}.\n"
    "Operator IDs/names: Kengxxiao/ArknightsGameData_YoStar EN data.\n"
    "Recruitment pool: awedtan.ca API (Global). Images resized to 72px for operator identification.\n",
    encoding="utf-8")
print(f"Bundled portraits: {len(index)}/{len(selected)}; bytes: {sum(p.stat().st_size for p in OUT.glob('*.webp'))}")
for name in ("Steward", "Midnight", "Plume", "Popukar", "Vanilla", "Mayer", "Beanstalk"):
    assert name in index, f"Missing regression portrait: {name}"
    print(f"Portrait verified: {name} -> {index[name]}")
