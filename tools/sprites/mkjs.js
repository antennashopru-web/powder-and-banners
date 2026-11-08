const fs=require('fs'),path=require('path');
const dir=process.argv[2], outf=process.argv[3];
const meta=JSON.parse(fs.readFileSync(path.join(dir,'sprites.json')));
const img={};
for(const f of fs.readdirSync(dir)) if(f.endsWith('.png')) img[f.slice(0,-4)]='data:image/png;base64,'+fs.readFileSync(path.join(dir,f)).toString('base64');
fs.writeFileSync(outf,'/* Пререндеренные 3D-спрайты (генерируются из tools/sprites) */\nwindow.SPR_DATA='+JSON.stringify({meta,img})+';\n');
console.log(outf, fs.statSync(outf).size);
