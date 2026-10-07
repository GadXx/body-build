import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { join } from "node:path";
function files(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? files(join(dir, e.name)) : [join(dir, e.name)],
  );
}
const paths = files("dist").filter((p) => !p.endsWith("/sw.js"));
const hash = createHash("sha256").update(
  readFileSync(new URL(import.meta.url)),
);
paths.sort().forEach((p) => hash.update(readFileSync(p)));
const version = hash.digest("hex").slice(0, 16),
  assets = paths.map((p) => "./" + p.slice(5));
writeFileSync(
  "dist/sw.js",
  `
const CACHE='body-build-${version}';
const ASSETS=${JSON.stringify(assets)};
// Only activate a complete cache, with bounded concurrency for Safari and slow connections.
self.addEventListener('install',event=>event.waitUntil((async()=>{
  const cache=await caches.open(CACHE); const queue=[...ASSETS];
  try { await Promise.all(Array.from({length:4},async()=>{while(queue.length){const path=queue.shift();const response=await fetch(new URL(path,self.registration.scope),{cache:'reload'});if(!response.ok)throw Error(path);await cache.put(new URL(path,self.registration.scope),response);}})); }
  catch(error){await caches.delete(CACHE);throw error;}
})()));
// Never force an in-progress workout to reload. New workers wait for old windows to close.
self.addEventListener('activate',event=>event.waitUntil((async()=>{
  for(const key of await caches.keys())if(key.startsWith('body-build-')&&key!==CACHE)await caches.delete(key);
  await self.clients.claim();
})()));
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url);
  if(event.request.method!=='GET'||url.origin!==self.location.origin||!url.href.startsWith(self.registration.scope))return;
  // Assets are public static files. Ignore Vary: Origin added by preview/static hosts:
  // script/link requests use CORS while pre-cache fetches do not carry the same Origin.
  event.respondWith((async()=>{const cache=await caches.open(CACHE);if(event.request.mode==='navigate')return (await cache.match(new URL('./index.html',self.registration.scope),{ignoreVary:true}))||fetch(event.request);return (await cache.match(event.request,{ignoreVary:true}))||fetch(event.request);})());
});
`,
);
console.log(`Offline cache: ${assets.length} assets, version ${version}`);
