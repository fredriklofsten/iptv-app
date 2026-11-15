// Gör appen körbar i äldre webbläsare (äldre Android TV, LG, Samsung): översätter ny JS-syntax till Chrome 61-nivå.
import { transformSync } from 'esbuild';
import fs from 'node:fs';
const T = 'chrome61';
const html = 'www/index.html';
let s = fs.readFileSync(html, 'utf8');
s = s.replace(/<script>([\s\S]*?)<\/script>/g, (m, code) => '<script>' + transformSync(code, { target: T, loader: 'js' }).code + '</script>');
fs.writeFileSync(html, s);
for (const f of fs.readdirSync('www/vendor').filter(f => f.endsWith('.js'))) {
  const p = 'www/vendor/' + f;
  fs.writeFileSync(p, transformSync(fs.readFileSync(p, 'utf8'), { target: T, loader: 'js' }).code);
}
console.log('legacy ok');
