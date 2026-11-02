"""Extract the CN recruitment pool from client recruitDetail, never all released units."""
import json,re,urllib.request
from pathlib import Path
BASE='https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData/master/zh_CN/gamedata/excel/'
EN='https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData_YoStar/main/en_US/gamedata/excel/'
def load(url):
    with urllib.request.urlopen(url,timeout=60) as response:return json.load(response)
def build(gacha,chars,english):
    detail=re.sub(r'<[^>]*>','',gacha['recruitDetail']).replace('\\n','\n')
    names=set()
    for section in re.split(r'-{5,}',detail):
        match=re.search(r'★{1,6}\s*\n(.+)',section,re.S)
        if match:names.update(x.strip() for x in match.group(1).split('/') if x.strip())
    tags={1:'Guard',2:'Sniper',3:'Defender',4:'Medic',5:'Supporter',6:'Caster',7:'Specialist',8:'Vanguard',9:'Melee',10:'Ranged',11:'Top Operator',12:'Crowd-Control',13:'Nuker',14:'Senior Operator',15:'Healing',16:'Support',17:'Starter',18:'DP-Recovery',19:'DPS',20:'Survival',21:'AoE',22:'Defense',23:'Slow',24:'Debuff',25:'Fast-Redeploy',26:'Shift',27:'Summon',28:'Robot',29:'Elemental'}
    translations={x['tagName']:tags[x['tagId']] for x in gacha['gachaTags'] if x['tagId'] in tags}
    professions=dict(WARRIOR='Guard',TANK='Defender',PIONEER='Vanguard',SPECIAL='Specialist',SUPPORT='Supporter',SNIPER='Sniper',CASTER='Caster',MEDIC='Medic')
    result=[];found=set()
    for ident,data in chars.items():
        if data.get('name') not in names or not ident.startswith('char_'):continue
        found.add(data['name']);rarity=data['rarity'];rarity=int(rarity.split('_')[-1]) if isinstance(rarity,str) else rarity+1
        selected=[data['position'].title(),professions[data['profession']]]+[translations[t] for t in data['tagList']]
        result.append(dict(id=ident,name=english.get(ident,{}).get('name') or data.get('appellation') or data['name'],rarity=rarity,tags=list(dict.fromkeys(selected))))
    assert not names-found, f'Unresolved CN names: {names-found}'
    assert 100<=len(result)<=400
    return result
if __name__=='__main__':
    rows=build(load(BASE+'gacha_table.json'),load(BASE+'character_table.json'),load(EN+'character_table.json'))
    out=Path('app/src/main/assets/operators');out.mkdir(parents=True,exist_ok=True)
    (out/'china.json').write_text(json.dumps(rows,ensure_ascii=False),encoding='utf-8')
    print('China recruitment:',len(rows))
