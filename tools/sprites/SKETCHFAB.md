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
| house.glb | [Medieval House Low Poly](https://sketchfab.com/3d-models/medieval-house-low-poly-dd992c6d1aee4b1186aaab296f5727fe) | SantyFrow | dd992c6d1aee4b1186aaab296f5727fe |
| tower.glb | [Medieval Tower](https://sketchfab.com/3d-models/medieval-tower-6c821f2aae3b4e8db5d94c6f48fb568b) | Den (dfomenco) | 6c821f2aae3b4e8db5d94c6f48fb568b |
| mill.glb | [Windmill Low Poly](https://sketchfab.com/3d-models/windmill-low-poly-36e96a2d6a944ad3ac75964022638ad3) | Aegis_Wolf | 36e96a2d6a944ad3ac75964022638ad3 |

Скачать (нужен свой API-токен Sketchfab в переменной `SKETCHFAB_TOKEN`):

    SKETCHFAB_TOKEN=... python3 fetch-sketchfab.py

Модели в git не хранятся (папка `skf/` в .gitignore); игра использует уже запечённые `models.bin` и спрайты.
