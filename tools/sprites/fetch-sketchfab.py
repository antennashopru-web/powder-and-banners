#!/usr/bin/env python3
"""Скачивает модели со Sketchfab (CC BY 4.0) в skf/. Токен берётся из SKETCHFAB_TOKEN и отправляется только на api.sketchfab.com."""
import json, os, sys, time, urllib.request
MODELS = {
    'cannon': '5ce4c3b2ece04e6c960ce14dd12bb0fe', 'mortar': '34bdf984f4424a87af5b9e44ffb31859',
    'horse': '6d0f9c1ce82f41048a282b6c47a50684', 'hall': 'e2c47838ebde464bbcea521c0ff5ce9e', 'house': '5fe4f1fd877f4ebd884c1c0248b28e20', 'store': 'ec96a821b1f142869b39b327698b68a5', 'barracks': '2b2a4053721c4afe9651662f449587e7', 'stable': '3366ee69c2dd4908a99f386e07faa1d6', 'foundry': '6e8297ae714547448cc6f7da8acbb247', 'arsenal': '3eb9e3b600264e2f99100fc43619497e', 'academy': 'b5eaf6f238b1490a80c682ed238a7230',
    'tower': '6c821f2aae3b4e8db5d94c6f48fb568b', 'soldier': '7fc4d303a8654908aa56711c6e3078d8', 'mill': '36e96a2d6a944ad3ac75964022638ad3',
}
tok = os.environ.get('SKETCHFAB_TOKEN') or sys.exit('нужен SKETCHFAB_TOKEN')
os.makedirs(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skf'), exist_ok=True)
def get(url, auth):
    for k in range(6):
        try:
            req = urllib.request.Request(url, headers={'Authorization': 'Token ' + tok} if auth else {})
            return urllib.request.urlopen(req, timeout=120).read()
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(10 * (k + 1))
    raise RuntimeError('rate limit')
for name, uid in MODELS.items():
    info = json.loads(get('https://api.sketchfab.com/v3/models/' + uid, False))
    assert info['license']['label'] == 'CC Attribution', (name, info['license']['label'])
    d = json.loads(get('https://api.sketchfab.com/v3/models/%s/download' % uid, True))
    data = get(d['glb']['url'], False)
    open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skf', name + '.glb'), 'wb').write(data)
    print(name, info['name'], '—', info['user']['displayName'], len(data))
