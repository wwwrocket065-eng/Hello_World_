"""Sanity-check the independent Global recruitment data API used by the app."""
import json
import urllib.request

BASE = "https://awedtan.ca/api/"
def load(path):
    request = urllib.request.Request(BASE + path, headers={"User-Agent": "ArknightsRecruitAssist/0.2", "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response)

pool = set(load("recruitpool")[0]["value"])
assert 100 <= len(pool) <= 400, f"Recruitment pool size invalid: {len(pool)}"
path = "operator?include=data.name&include=data.rarity&include=data.position&include=data.profession&include=data.tagList"
operators = load(path)
rows = []
for item in operators:
    if item.get("canon") not in pool:
        continue
    d = item.get("value", {}).get("data", {})
    if d.get("name") and d.get("rarity") and d.get("position") and d.get("profession"):
        rows.append((d["name"], d["rarity"], d.get("tagList", [])))
print(f"Global IDs: {len(pool)}; parsed operators: {len(rows)}")
print("Examples:", rows[:6])
assert len(rows) >= 100, "API schema changed or recruitment records incomplete"
