"""Protect all known EN operator names without touching recruitment data."""
import json, urllib.request
from pathlib import Path
url='https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData_YoStar/main/en_US/gamedata/excel/character_table.json'
with urllib.request.urlopen(url,timeout=60) as r: data=json.load(r)
names=sorted({v['name'] for v in data.values() if v.get('name')})
assert len(names)>300
Path('app/src/main/assets/translation_names.json').write_text(json.dumps(names,ensure_ascii=False),encoding='utf8')
print('Protected operator names:',len(names))
