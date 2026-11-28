# Модели со Sketchfab (CC BY 4.0)

Все модели ниже — под лицензией Creative Commons Attribution 4.0 (https://creativecommons.org/licenses/by/4.0/).
В игре они уменьшены, текстуры собраны в атлас 1024×1024 на модель,
добавлены цвета команд; коню добавлены седло и чепрак. Солдату поставлен свой скелет (soldier.js), сетка упрощена, мундир перекрашен в цвет команды. Авторы указаны в игре (Помощь → «Графика»).

| Файл в `skf/` | Модель | Автор | uid |
|---|---|---|---|
| soldier.glb | [European Soldier 17th Century](https://sketchfab.com/3d-models/european-soldier-17th-century-7fc4d303a8654908aa56711c6e3078d8) | HaiPham | 7fc4d303a8654908aa56711c6e3078d8 |
| cannon.glb | [Canon 17th Century](https://sketchfab.com/3d-models/canon-17th-century-5ce4c3b2ece04e6c960ce14dd12bb0fe) | iZnoGouD | 5ce4c3b2ece04e6c960ce14dd12bb0fe |
| mortar.glb | [Cannon Low-Poly](https://sketchfab.com/3d-models/cannon-low-poly-34bdf984f4424a87af5b9e44ffb31859) | derdaryen | 34bdf984f4424a87af5b9e44ffb31859 |
| horse.glb | [Horse](https://sketchfab.com/3d-models/horse-6d0f9c1ce82f41048a282b6c47a50684) | MAXDESIGN-3D | 6d0f9c1ce82f41048a282b6c47a50684 |
| hall.glb | [Rauma Town Hall](https://sketchfab.com/3d-models/rauma-town-hall-e2c47838ebde464bbcea521c0ff5ce9e) | Karumaki | e2c47838ebde464bbcea521c0ff5ce9e |
| house.glb | [Half-timbered House](https://sketchfab.com/3d-models/half-timbered-house-5fe4f1fd877f4ebd884c1c0248b28e20) | Kovonic | 5fe4f1fd877f4ebd884c1c0248b28e20 |
| store.glb | [Old European House](https://sketchfab.com/3d-models/old-european-house-game-ready-ec96a821b1f142869b39b327698b68a5) | Valkirin1 | ec96a821b1f142869b39b327698b68a5 |
| barracks.glb | [Viking town Hall](https://sketchfab.com/3d-models/viking-town-hall-2b2a4053721c4afe9651662f449587e7) | Bucks (Its_Bucks) | 2b2a4053721c4afe9651662f449587e7 |
| stable.glb | [Old Wooden Barn (House 4)](https://sketchfab.com/3d-models/old-wooden-barn-house-4-3366ee69c2dd4908a99f386e07faa1d6) | Blenderust (narighillya) | 3366ee69c2dd4908a99f386e07faa1d6 |
| foundry.glb | [Old Smithy](https://sketchfab.com/3d-models/old-smithy-6e8297ae714547448cc6f7da8acbb247) | lucasprs51450 | 6e8297ae714547448cc6f7da8acbb247 |
| arsenal.glb | [Medieval house, Generic Textures](https://sketchfab.com/3d-models/medieval-house-generic-textures-game-ready-3eb9e3b600264e2f99100fc43619497e) | by__Rx | 3eb9e3b600264e2f99100fc43619497e |
| academy.glb | [Low Poly Medieval Gothic Church](https://sketchfab.com/3d-models/low-poly-medieval-gothic-church-b5eaf6f238b1490a80c682ed238a7230) | AlexFerrart3D | b5eaf6f238b1490a80c682ed238a7230 |
| tower.glb | [Medieval Tower](https://sketchfab.com/3d-models/medieval-tower-6c821f2aae3b4e8db5d94c6f48fb568b) | Den (dfomenco) | 6c821f2aae3b4e8db5d94c6f48fb568b |
| mill.glb | [Windmill Low Poly](https://sketchfab.com/3d-models/windmill-low-poly-36e96a2d6a944ad3ac75964022638ad3) | Aegis_Wolf | 36e96a2d6a944ad3ac75964022638ad3 |
| pine.glb | [Pine Tree [Game-ready]](https://sketchfab.com/3d-models/pine-tree-game-ready-dc3fbd9205cf4027a4455d1f415e0478) | Wenedi | dc3fbd9205cf4027a4455d1f415e0478 |
| leaf.glb | [Leaf Tree - PS1 Low Poly](https://sketchfab.com/3d-models/leaf-tree-ps1-low-poly-d799c08100974e1ba352fcd646cb0694) | Wersaus33 | d799c08100974e1ba352fcd646cb0694 |
| birch.glb | [Birch Trees Pack, PS1 Low Poly](https://sketchfab.com/3d-models/birch-trees-pack-ps1-low-poly-5f9aa7c0a3cf4298a4cac9d4cea59643) | Vova Truestory (FlowOff) | 5f9aa7c0a3cf4298a4cac9d4cea59643 |

Скачать (нужен свой API-токен Sketchfab в переменной `SKETCHFAB_TOKEN`):

    SKETCHFAB_TOKEN=... python3 fetch-sketchfab.py

Модели в git не хранятся (папка `skf/` в .gitignore); игра использует уже запечённые `models.bin` и спрайты.
