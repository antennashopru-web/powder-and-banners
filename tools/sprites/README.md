# Пререндер спрайтов

Спрайты игры рендерятся из 3D: персонажи и здания — CC0-модели KayKit (Kay Lousberg, www.kaylousberg.com),
оружие, треуголки, кони, пушки и поле фермы — процедурные модели из `models.js`. 8 направлений, ходьба,
атака, смерть, запечённые тени. Лицензии KayKit лежат рядом (`LICENSE-*.txt`).

```
cd tools/sprites
npm install
./fetch-kaykit.sh                     # скачать модели KayKit в kk/
python3 -m http.server 8765 &         # модули three.js грузятся по http
node kkrun.js out                     # рендер атласов PNG в out/
cp out/*.png out/sprites.json ../../app/src/main/assets/sprites/   # для Android
node mkjs.js out ../../web/sprites.js                             # для браузерной версии
```
Старый вариант только из процедурных моделей: `node run.js out` (render.html).
Для рендера нужен Chromium с WebGL (`--use-angle=swiftshader` работает и без видеокарты).
