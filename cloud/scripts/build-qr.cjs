'use strict';
// Builds a dependency-free browser bundle from the retained, licensed QR source.
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');const files={};
for(const f of fs.readdirSync(path.join(root,'vendor/QRCode')).filter(f=>f.endsWith('.js'))) files['vendor/QRCode/'+f]=fs.readFileSync(path.join(root,'vendor/QRCode',f),'utf8');
files['src/qr.cjs']=fs.readFileSync(path.join(root,'src/qr.cjs'),'utf8');
let out='/* Vendored QRCode (c) 2009 Kazuhiko Arase. MIT. See vendor/QRCode/LICENSE.txt. */\n(function(){\n"use strict";\nconst modules={\n';
out+=Object.entries(files).map(([name,body])=>JSON.stringify(name)+':function(require,module,exports){\n'+body+'\n}').join(',\n');
out+='\n};const cache={};function load(id){if(cache[id])return cache[id].exports;const m=cache[id]={exports:{}};modules[id](function(spec){const parts=id.split("/").slice(0,-1);for(const s of spec.split("/")){if(s==="..")parts.pop();else if(s!==".")parts.push(s);}let key=parts.join("/");if(!modules[key])key=modules[key+".js"]?key+".js":key+"/index.js";return load(key);},m,m.exports);return m.exports;}window.LensDropQR=load("src/qr.cjs");})();\n';
fs.writeFileSync(path.join(root,'public/qr.js'),out);
console.log('Bundled local QR encoder; no remote QR service.');
