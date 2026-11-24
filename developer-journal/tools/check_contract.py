#!/usr/bin/env python3
"""Block regressions in the Developer Journal before every unsigned build."""
from pathlib import Path
import hashlib,json,re
ROOT=Path(__file__).resolve().parents[1]
assets=ROOT/"app/src/main/assets"
ui=(assets/"index.html").read_text(encoding="utf-8")
data=json.loads((assets/"journal.json").read_text(encoding="utf-8"))
book=re.search(r"book:'([^']+)'",ui)
assert book and hashlib.sha256(book.group(1).encode()).hexdigest()=="2c51629672420a95cdc8d283b4bbe29a4a4808b7ccdc16f9a2c97e537a65bbac", "Protected in-app book SVG changed"
idx=[ui.find('cards.append(row("'+x+'"') for x in ["Проекты","Статьи","Графики","3D-примеры"]]
assert all(x>=0 for x in idx) and idx==sorted(idx), "Unexpected home section order"
assert ui.count('function kvDemo(parent)')==1, "Duplicate KV calculation function"
assert ui.count('function contextDemo(parent)')==1, "Context demonstration missing"
assert "touch-action:none" not in ui and "touch-action:pan-y" in ui, "Vertical scrolling regressed"
assert "Ошибки: собственные измерения" not in ui, "Manual error entries returned"
demos={"kv","quant","context"}
seen=set()
for section in data["sections"]:
 assert section.get("id") and isinstance(section.get("chapters"),list)
 for chapter in section["chapters"]:
  for article in chapter.get("articles",[]):
   item=(section["id"],article["id"])
   assert item not in seen,"Duplicate article ID: "+str(item)
   seen.add(item)
   if article.get("demo"): assert article["demo"] in demos,"Unsupported graph demo"
   if article.get("demo3d"): assert article["demo3d"]=="kv","Unsupported 3D demo"
ai=next(s for s in data["sections"] if s["id"]=="ai")
mapping={a["id"]:a.get("demo") for c in ai["chapters"] for a in c["articles"]}
assert mapping.get("context")=="context" and mapping.get("kv")=="kv", "KV demo reused for context"
launcher=(ROOT/"app/src/main/res/drawable/ic_book.xml").read_text(encoding="utf-8")
assert 'android:scaleX="0.77"' in launcher and 'android:scaleY="0.77"' in launcher, "Launcher icon size regressed"
# All chapters and articles must have distinct topic-specific icons within each project.
chapters_obj=re.search(r'const chapterIcons=Object\.freeze\((\{[^;]+\})\);',ui)
articles_obj=re.search(r'const articleIcons=Object\.freeze\((\{[^;]+\})\);',ui)
assert chapters_obj and articles_obj, "Missing topic icon maps"
chapter_icons=json.loads(chapters_obj.group(1))
article_icons=json.loads(articles_obj.group(1))
icon_block=ui.split("const icons={",1)[1].split("};",1)[0]
known_icons=set(re.findall(r"(?m)^\s*([a-zA-Z][\w]*):'",icon_block))
for section in data["sections"]:
 chapters_used=[]; articles_used=[]
 for chapter in section["chapters"]:
  cicon=chapter_icons.get(chapter["id"])
  assert cicon in known_icons, "Missing chapter icon: "+chapter["id"]
  chapters_used.append(cicon)
  for article in chapter["articles"]:
   aicon=article_icons.get(article["id"])
   assert aicon in known_icons, "Missing article icon: "+article["id"]
   articles_used.append(aicon)
 assert len(set(chapters_used))==len(chapters_used), "Duplicate chapter icons in "+section["id"]
 assert len(set(articles_used))==len(articles_used), "Duplicate article icons in "+section["id"]
print("Journal UI contract: PASS — in-app book, menu, scroll, demonstrations and launcher icon protected")
