"""Compare actual Android strings/captions with source catalogs after LocaleSmokeInstrumentation."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TAGS = 'en-US en-GB en-CA pt-BR pt-PT es-ES fr-FR zh-CN hi-IN ar bn-BD ru-RU ur-PK ja-JP pnb-Arab-PK mr-IN de-DE it-IT ko-KR id-ID tr-TR'.split()
SCREENS = 'home library gallery showSettings showCinema showAppearance showScreensaverSettings showScreensaverCredits showStore showLanguage appOptions showInputs showHomeChoice showAbout'.split()
BIDI = re.compile('[\u200e\u200f\u202a-\u202e\u2066-\u2069]')

def audit(folder):
    master = {}
    for filename in ['common_strings.json', 'activity_strings.json']:
        master.update(json.loads((ROOT/'tools'/filename).read_text(encoding='utf-8')))
    photos = json.loads((ROOT/'src/android/app/src/main/assets/screensaver.json').read_text(encoding='utf-8-sig'))
    errors = []; count = 0
    for tag in TAGS:
        expected = {k:v['en'] for k,v in master.items()} if tag == 'en-US' else json.loads((ROOT/'tools/translations'/f'{tag}.json').read_text(encoding='utf-8'))
        actual = json.loads((folder/f'{tag}-compiled-strings.json').read_text(encoding='utf-8'))
        if actual.keys() != expected.keys(): errors.append({'locale':tag, 'keys':'mismatch'})
        for key,value in expected.items():
            if actual.get(key) != value: errors.append({'locale':tag, 'key':key, 'expected':value, 'actual':actual.get(key)})
            count += 1
        saver = json.loads((folder/f'{tag}-screensaver.json').read_text(encoding='utf-8'))
        captions = [expected['common_procedural_caption'].replace('%1$s',expected[f'common_procedural_{i:02}']) for i in range(1,21)]
        captions += [expected['common_photo_caption'].replace('%1$s',expected[f'common_photo_{i+1:02}']).replace('%2$s',photo['artist']) for i,photo in enumerate(photos)]
        if not saver['secure'] or BIDI.sub('',saver['caption']) not in captions or not saver['description'].startswith(expected['common_screensaver']):
            errors.append({'locale':tag,'screensaver':'caption, context or secure flag mismatch'})
    screens = json.loads((folder/'report.json').read_text(encoding='utf-8'))
    pairs = {(s['locale'],s['screen']) for s in screens}
    assert len(screens) == len(pairs) == len(TAGS)*len(SCREENS) and pairs == {(tag,screen) for tag in TAGS for screen in SCREENS}
    visible = [t for screen in screens for t in screen['texts'] if t['shown'] and t['laidOut'] and t['inViewport'] and t['width']>0 and t['height']>0]
    vertical = [t['text'] for t in visible if t['layoutHeight']+t['paddingTop']+t['paddingBottom']>t['height']]
    summary = {'compiled_values':count,'screens':len(screens),'locales':len(TAGS),'screensavers':len(TAGS),'visible_text_views':len(visible),
        'ellipsis':sum(bool(t['ellipsis']) for t in visible),'vertical_clip_candidates':vertical,'differences':errors}
    (folder/'audit.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({**summary,'differences':len(errors)},ensure_ascii=True))
    return bool(errors or vertical or summary['ellipsis'])

if __name__ == '__main__':
    if len(sys.argv) != 2: raise SystemExit('Usage: python tools/audit_runtime_locales.py PATH_TO_PULLED_LOCALE_SMOKE')
    raise SystemExit(audit(Path(sys.argv[1])))
