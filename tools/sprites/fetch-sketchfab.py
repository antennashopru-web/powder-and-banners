#!/usr/bin/env python3
"""Скачивает модели со Sketchfab (CC BY 4.0) в skf/. Токен берётся из SKETCHFAB_TOKEN и отправляется только на api.sketchfab.com."""
import json, os, sys, time, urllib.request
MODELS = {
    'cannon': 'cb4f561457084ba4ab6704f5139a3c5c', 'mortar': '34bdf984f4424a87af5b9e44ffb31859',
    'horse': 'd1ca14c1befe47c8a4686d2f64c82f45', 'house': 'dd992c6d1aee4b1186aaab296f5727fe',
    'tower': '6c821f2aae3b4e8db5d94c6f48fb568b', 'mill': '36e96a2d6a944ad3ac75964022638ad3',
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
