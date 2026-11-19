'use strict';
// The ASCII-only interface avoids the legacy encoder's lack of a UTF-8 mode.
const QRCode = require('../vendor/QRCode');
const ecc = require('../vendor/QRCode/QRErrorCorrectLevel');
function encode(url) {
  if (typeof url !== 'string' || url.length > 512 || !/^[\x20-\x7e]+$/.test(url)) throw Error('QR requires a printable ASCII URL of at most 512 characters.');
  const parsed = new URL(url);
  if (!['https:','http:'].includes(parsed.protocol) || parsed.username || parsed.password) throw Error('QR requires an HTTP(S) URL without credentials.');
  const q = new QRCode(-1, ecc.M); q.addData(parsed.href); q.make(); return q;
}
function toSvg(url) {
  const q=encode(url), border=4, size=q.getModuleCount()+border*2, d=[];
  for(let y=0;y<q.getModuleCount();y++)for(let x=0;x<q.getModuleCount();x++)if(q.isDark(y,x))d.push(`M${x+border},${y+border}h1v1h-1z`);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="512" height="512" shape-rendering="crispEdges"><rect width="100%" height="100%" fill="#fff"/><path d="${d.join('')}" fill="#000"/></svg>`;
}
module.exports={encode,toSvg};
