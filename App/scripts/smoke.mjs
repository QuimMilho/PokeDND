// Serves the built app (dist/pokednd-app/browser) and checks, over real HTTP, that:
//  - the home page of each language is served and its bundle holds that language's texts,
//  - the web manifest and the service worker file are served.
// Run after `npm run build`:  npm run test:smoke
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)), 'dist/pokednd-app/browser');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.webmanifest': 'application/manifest+json', '.json': 'application/json', '.png': 'image/png', '.ico': 'image/x-icon' };

const server = createServer(async (req, res) => {
  try {
    let path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname));
    let file = join(root, path);
    if (!file.startsWith(root)) throw new Error('outside root');
    if ((await stat(file).catch(() => null))?.isDirectory()) file = join(file, 'index.html');
    res.writeHead(200, { 'content-type': types[extname(file)] ?? 'application/octet-stream' });
    res.end(await readFile(file));
  } catch {
    res.writeHead(404).end('not found');
  }
});
await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
const base = `http://127.0.0.1:${server.address().port}`;

let failed = 0;
const check = (name, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '  ' + detail}`);
  if (!ok) failed++;
};
const get = async (path) => {
  const r = await fetch(base + path);
  return { status: r.status, text: await r.text() };
};

try {
  for (const [lang, bundleText] of [['en', 'Welcome to PokeDnD'], ['pt', 'Bem-vindo ao PokeDnD']]) {
    const home = await get(`/${lang}/`);
    check(`/${lang}/ home page is served`, home.status === 200 && home.text.includes(`<base href="/${lang}/">`), `status ${home.status}`);
    const js = home.text.match(/src="(main-[^"]+\.js)"/)?.[1];
    const main = js ? await get(`/${lang}/${js}`) : { status: 0, text: '' };
    check(`/${lang}/ bundle has the ${lang} texts`, main.text.includes(bundleText));
    const manifest = await get(`/${lang}/manifest.webmanifest`);
    check(`/${lang}/ manifest is served and valid`, manifest.status === 200 && JSON.parse(manifest.text).name === 'PokeDnD');
    const sw = await get(`/${lang}/ngsw-worker.js`);
    check(`/${lang}/ service worker is served`, sw.status === 200 && sw.text.length > 1000);
    const ngsw = await get(`/${lang}/ngsw.json`);
    check(`/${lang}/ service worker config is served`, ngsw.status === 200 && JSON.parse(ngsw.text).index === `/${lang}/index.html`);
  }
} finally {
  server.close();
}
process.exit(failed ? 1 : 0);
