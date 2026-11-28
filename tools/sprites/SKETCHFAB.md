# Модели со Sketchfab (CC BY 4.0)

Все модели ниже — под лицензией Creative Commons Attribution 4.0 (https://creativecommons.org/licenses/by/4.0/).
В игре они уменьшены, цвета текстур запечены в вершины (средний цвет треугольника), металл затемнён,
добавлены цвета команд; у коня убраны поводья и перекрашен чепрак. Авторы указаны в игре (Помощь → «Графика»).

| Файл в `skf/` | Модель | Автор | uid |
|---|---|---|---|
| cannon.glb | [Medieval Mortar](https://sketchfab.com/3d-models/medieval-mortar-cb4f561457084ba4ab6704f5139a3c5c) | José Luis Ruiz | cb4f561457084ba4ab6704f5139a3c5c |
| mortar.glb | [Cannon Low-Poly](https://sketchfab.com/3d-models/cannon-low-poly-34bdf984f4424a87af5b9e44ffb31859) | derdaryen | 34bdf984f4424a87af5b9e44ffb31859 |
| horse.glb | [Low-Poly Horse](https://sketchfab.com/3d-models/low-poly-horse-d1ca14c1befe47c8a4686d2f64c82f45) | antemeossa | d1ca14c1befe47c8a4686d2f64c82f45 |
| house.glb | [Medieval House Low Poly](https://sketchfab.com/3d-models/medieval-house-low-poly-dd992c6d1aee4b1186aaab296f5727fe) | SantyFrow | dd992c6d1aee4b1186aaab296f5727fe |
| tower.glb | [Medieval Tower](https://sketchfab.com/3d-models/medieval-tower-6c821f2aae3b4e8db5d94c6f48fb568b) | Den (dfomenco) | 6c821f2aae3b4e8db5d94c6f48fb568b |
| mill.glb | [Windmill Low Poly](https://sketchfab.com/3d-models/windmill-low-poly-36e96a2d6a944ad3ac75964022638ad3) | Aegis_Wolf | 36e96a2d6a944ad3ac75964022638ad3 |

Скачать (нужен свой API-токен Sketchfab в переменной `SKETCHFAB_TOKEN`):

    SKETCHFAB_TOKEN=... python3 fetch-sketchfab.py

Модели в git не хранятся (папка `skf/` в .gitignore); игра использует уже запечённые `models.bin` и спрайты.
