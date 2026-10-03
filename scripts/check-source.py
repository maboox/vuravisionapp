#!/usr/bin/env python3
"""Offline structural checks; does not replace the Kotlin compiler or Android lint."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
errors=[]
for p in (root/'app/src').rglob('*.xml'):
    try:ET.parse(p)
    except Exception as e:errors.append(f'{p}: {e}')
for p in (root/'app/src').rglob('*.kt'):
    text=p.read_text();stack=[];i=0;state='code'
    pairs={')':'(',']':'[','}':'{'}
    while i<len(text):
        if state=='line':
            if text[i]=='\n':state='code'
            i+=1;continue
        if state=='comment':
            if text[i:i+2]=='*/':state='code';i+=2
            else:i+=1
            continue
        if state=='triple':
            if text[i:i+3]=='"""':state='code';i+=3
            else:i+=1
            continue
        if state in ['string','char']:
            if text[i]=='\\':i+=2;continue
            if text[i]==('"'if state=='string'else"'"):state='code'
            i+=1;continue
        if text[i:i+2]=='//':state='line';i+=2;continue
        if text[i:i+2]=='/*':state='comment';i+=2;continue
        if text[i:i+3]=='"""':state='triple';i+=3;continue
        if text[i]=='"':state='string';i+=1;continue
        if text[i]=="'":state='char';i+=1;continue
        if text[i] in '({[':stack.append((text[i],i))
        if text[i] in ')}]':
            if not stack or stack[-1][0]!=pairs[text[i]]:errors.append(f'{p}: unmatched delimiter at {i}');break
            stack.pop()
        i+=1
    if stack:errors.append(f'{p}: unclosed delimiter at {stack[-1]}')
    if state not in ['code','line']:errors.append(f'{p}: unfinished {state}')
topics=json.loads((root/'app/src/main/assets/guide/topics.json').read_text())
assert len(topics)==27 and len({t['id'] for t in topics})==27
assert all(len(t['stepsEn'])==len(t['stepsFa']) for t in topics)
assets=root/'app/src/main/java/com/vuravision/classroom'
lab_ids=re.search(r'val ids=listOf\((.*?)\)',(assets/'NativeLabs.kt').read_text()).group(1)
keys=re.findall(r'"([^"]+)"',lab_ids)+['energy','triangle_area','statistics','dilution','trig']
classified=[]
for ids in re.findall(r'"([a-z_ ]+)"\.split\(" "\)',(assets/'LabCatalog.kt').read_text()):classified+=ids.split()
assert len(keys)==73 and set(classified)==set(keys) and len(classified)==len(set(classified))
if errors:raise SystemExit('\n'.join(errors))
print(json.dumps({'kotlin_delimiters':'pass (not compilation)','xml':'pass','guide_chapters':27,'native_labs_classified':73},indent=2))
