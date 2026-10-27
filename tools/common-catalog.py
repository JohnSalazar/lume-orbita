"""Generate the reviewed English/Portuguese source catalog; never translate machine identifiers."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
entries = {}
def add(key, en, pt):
    entries['common_' + key] = {'en': en, 'pt': pt}

for line in '''network|Network|Rede
checking_internet|Checking internet|Verificando internet
unavailable|Status unavailable|Estado indisponível
no_network|No network|Sem rede
connect_tv|Connect your TV|Conecte sua TV
unknown|Unknown status|Estado desconhecido
wifi|Wi-Fi|Wi-Fi
ethernet|Ethernet|Cabo
cellular|Mobile network|Móvel
vpn|%1$s (VPN)|%1$s (VPN)
login_required|Sign-in required|Login necessário
internet_connected|Internet connected|Internet conectada
internet_unverified|Internet not verified|Internet não verificada
video|Video|Vídeo
music|Music|Música
games|Games|Jogos
apps|Apps|Apps
back|Back|Voltar
screensaver|Screensaver|Proteção de tela
dream_label|Lume · Landscapes|Lume · Paisagens
photo_description|Screensaver · %1$s|Proteção de tela · %1$s
photo_caption|%1$s · %2$s|%1$s · %2$s
procedural_caption|%1$s · Procedural art · lume órbita|%1$s · Arte procedural · lume órbita
procedural_description|Screensaver · Procedural art · %1$s|Proteção de tela · Arte procedural · %1$s
less_second|Less than 1 s|Menos de 1 s
remaining|%1$s remaining|Tempo restante: %1$s
minutes_seconds|%1$s min %2$s s|%1$s min %2$s s
minutes|%1$s min|%1$s min
seconds|%1$s s|%1$s s'''.splitlines():
    add(*line.split('|'))

assets = ROOT / 'src/android/app/src/main/assets'
scenes = json.loads((assets/'scenes.json').read_text(encoding='utf-8-sig'))
scene_en = ['Cosmic horizon','Sea of stars','Misty woods','Serene Alps','Copper dunes','Autumn garden','Northern lights','Golden canals','Amber canyon','Glass tide','Azure coast','Jade terraces','Winter retreat','Red road','Blue nebula','Rain city','Underwater garden','Lavender fields','Forest falls','Lunar silence','Crystal lake','Amber hall','Savanna horizon','Land of fire and ice','Sunlit orchard','Atlantic lighthouse','Mineral silk','Spring on the river','Beneath infinity','Sky islands']
themes = {'Espaço':'Space','Praias':'Beaches','Florestas':'Forests','Montanhas':'Mountains','Desertos':'Deserts','Jardins':'Gardens','Inverno':'Winter','Cidades':'Cities','Paisagens':'Landscapes','Abstrato':'Abstract','Natureza':'Nature','Oceano':'Ocean','Campos':'Fields','Arquitetura':'Architecture','Fantasia':'Fantasy'}
for i, scene in enumerate(scenes):
    title = {'scene-19': 'Cachoeira na floresta', 'scene-26': 'Farol do Atlântico'}.get(scene['id'], scene['title'])
    add(f'scene_{i+1:02}', scene_en[i], title)
    add(f'theme_{i+1:02}', themes[scene['theme']], scene['theme'])
photo_en = ['Green woodland in Armenia','Waters of Cheow Lan','Lang Co coast','Zagedan lakes','Merzouga dunes','Winter in Styria','Clouds in deep blue','Mist in Ella valley','Fjallsárlón glacial lake','Ergaki mountains','Silence of the dunes','Winter trees in Gorce','Lake beneath the clouds','Green Bali']
for i, photo in enumerate(json.loads((assets/'screensaver.json').read_text(encoding='utf-8-sig'))):
    add(f'photo_{i+1:02}', photo_en[i], photo['title'])
procedural_en = ['Dawn sea','Serene mountains','Dunes of light','Valley mist','Green hills','Quiet night','City lights','Whale song','Sky routes','Galaxy garden','Our Milky Way','Coral garden','Golden road','Balloons at dawn','Deer sanctuary','Enchanted forest','Butterfly garden','Bees and flowers','Water lily lake','Lighthouse of serenity']
procedural_pt = ['Mar de aurora','Montanhas serenas','Dunas de luz','Brumas do vale','Colinas verdes','Noite tranquila','Luzes da cidade','Canto das baleias','Rotas no céu','Jardim de galáxias','Nossa Via Láctea','Jardim de corais','Estrada dourada','Balões ao amanhecer','Refúgio dos cervos','Floresta encantada','Jardim das borboletas','Abelhas e flores','Lago dos nenúfares','Farol da serenidade']
for i, (en, pt) in enumerate(zip(procedural_en,procedural_pt)):
    add(f'procedural_{i+1:02}', en, pt)
assert len(scenes) == len(scene_en) == 30
assert len(photo_en) == 14 and len(procedural_en) == 20
(ROOT/'tools/common_strings.json').write_text(json.dumps(entries, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(f'{len(entries)} common strings')
