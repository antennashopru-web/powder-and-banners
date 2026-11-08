# Пререндер спрайтов

Все спрайты игры рендерятся из процедурных 3D-моделей (three.js) в `models.js`:
8 направлений, ходьба, атака, смерть, запечённые тени.

```
cd tools/sprites
npm install
node run.js out                                   # рендер атласов PNG в out/
node mkjs.js out ../../app/src/main/assets/sprites.js
```
Для рендера нужен Chromium с WebGL (`--use-angle=swiftshader` работает и без видеокарты).
