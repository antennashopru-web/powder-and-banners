const { chromium } = require('playwright'); const fs = require('fs'); const path=require('path');
(async () => {
  const out = process.argv[2] || 'out'; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader','--allow-file-access-from-files'] });
  const p = await b.newPage(); const errs=[];
  p.on('console', m => console.log('console:', m.text())); p.on('pageerror', e => errs.push(e.message));
  await p.goto('file://' + path.resolve('render.html'));
  if (errs.length) { console.log(errs); process.exit(1); }
  const t0=Date.now();
  const res = await p.evaluate(() => { try { return window.renderAll(); } catch (e) { return { err: e.stack }; } });
  if (res.err) { console.log(res.err); process.exit(1); }
  console.log('render ms', Date.now()-t0);
  const meta = { units: {}, blds: {} };
  const save = (name, o) => { fs.writeFileSync(path.join(out, name + '.png'), Buffer.from(o.url.split(',')[1], 'base64')); return o.meta; };
  for (const k in res.units) meta.units[k] = save('u_' + k, res.units[k]);
  for (const k in res.blds) meta.blds[k] = save('b_' + k, res.blds[k]);
  meta.trees = save('trees', res.trees); meta.rocks = save('rocks', res.rocks);
  fs.writeFileSync(path.join(out, 'sprites.json'), JSON.stringify(meta));
  console.log(JSON.stringify(meta).slice(0, 400));
  await b.close();
})();
