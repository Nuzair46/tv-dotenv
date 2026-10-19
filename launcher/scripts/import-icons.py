#!/usr/bin/env python3
"""Convert the five audited outline SVGs to Android vectors without rasterization."""
from pathlib import Path
import xml.etree.ElementTree as E

root=Path(__file__).resolve().parents[1]
for source in sorted((root/'assets/icons').glob('*.svg')):
    svg=E.parse(source).getroot()
    assert svg.attrib['viewBox']=='0 0 48 48'
    paths=[]
    for item in svg.iter():
        tag=item.tag.split('}')[-1]
        if tag=='path': paths.append(item.attrib['d'])
        elif tag=='circle':
            x,y,r=(float(item.attrib[k]) for k in ['cx','cy','r'])
            paths.append(f'M{x-r},{y} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0 Z')
        elif tag not in ['svg','defs','style']:raise ValueError('Unsupported SVG element: '+tag)
    assert paths
    text='''<?xml version="1.0" encoding="utf-8"?>
<!-- Arcticons vector outline. See THIRD_PARTY.md. Stroke widened from 1 to 1.1 for TV. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="48" android:viewportHeight="48">
'''
    for path in paths:
        text+=f'    <path android:fillColor="#00000000" android:strokeColor="#FFF0F2F5" android:strokeWidth="1.1" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{path}" />\n'
    text+='</vector>\n'
    (root/'app/src/main/res/drawable'/f'{source.stem}.xml').write_text(text)
    old=root/'app/src/main/res/drawable-nodpi'/f'{source.stem}.webp'
    if old.exists():old.unlink() # Already retained in the revision backup.
    print(source.stem, len(paths), 'vector paths')
