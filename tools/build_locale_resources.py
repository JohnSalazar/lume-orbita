"""Build Android resources from reviewed catalogs. Missing translations fail loudly."""
import argparse
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
TAGS = 'en-US en-GB en-CA pt-BR pt-PT es-ES fr-FR zh-CN hi-IN ar bn-BD ru-RU ur-PK ja-JP pnb-Arab-PK mr-IN de-DE it-IT ko-KR id-ID tr-TR'.split()
def qualifier(tag):
    if tag == 'en-US': return 'values'
    if tag == 'pnb-Arab-PK': return 'values-b+pnb+Arab+PK'
    parts = tag.split('-')
    if parts[0] == 'id': parts[0] = 'in' # Android resource matching uses the legacy language code.
    return 'values-' + parts[0] + ('-r'+parts[1] if len(parts)>1 else '')
def android_text(text):
    return '"' + escape(text.replace('\\','\\\\').replace('"','\\"').replace("'","\\'").replace('\n','\\n')) + '"'
def master():
    result = {}
    for file in ['common_strings.json','activity_strings.json']:
        for key, value in json.loads((ROOT/'tools'/file).read_text(encoding='utf-8-sig')).items():
            assert key not in result, key
            result[key] = value
    return result
def build(selected):
    source=master()
    res=ROOT/'src/android/app/src/main/res'
    for tag in selected:
        if tag == 'en-US': texts={key:value['en'] for key,value in source.items()}
        else: texts=json.loads((ROOT/'tools/translations'/f'{tag}.json').read_text(encoding='utf-8-sig'))
        assert set(texts)==set(source), (tag, 'missing',set(source)-set(texts),'extra',set(texts)-set(source))
        for key,value in texts.items():
            assert isinstance(value,str) and value.strip(), (tag,key,'empty')
            expected=sorted(re.findall(r'%\d+\$[sd]',source[key]['en']))
            assert sorted(re.findall(r'%\d+\$[sd]',value))==expected,(tag,key,'placeholders')
        folder=res/qualifier(tag);folder.mkdir(exist_ok=True)
        xml='<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        xml+=''.join(f'    <string name="{key}">{android_text(value)}</string>\n' for key,value in texts.items())
        (folder/'strings.xml').write_text(xml+'</resources>\n',encoding='utf-8')
        print(f'{tag}: {len(texts)} strings')
    (res/'xml/locales_config.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'+''.join(f'    <locale android:name="{tag}" />\n' for tag in TAGS)+'</locale-config>\n',encoding='utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--locales',nargs='+',choices=TAGS,default=TAGS)
    build(parser.parse_args().locales)
