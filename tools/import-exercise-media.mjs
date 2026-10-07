// Download a pinned public-domain photo set. Run from the repository root.
import { readFileSync, mkdirSync, existsSync, renameSync, rmSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const revision = 'f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5';
const base = `https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@${revision}`;
const root = 'feature/exercise/src/main/assets/exercise-photos';
const mapping = JSON.parse(readFileSync('tools/exercise-media-map.json', 'utf8'));
mkdirSync(root, { recursive: true });
const run = promisify(execFile);
async function download(url, path) {
  const alternatives = [url, url.replace(base, `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}`), url.replace('cdn.jsdelivr.net', 'fastly.jsdelivr.net')];
  for (const candidate of alternatives) {
    try {
      await run('curl', ['-4', '-fLsS', '--connect-timeout', '5', '--max-time', '20', candidate, '-o', `${path}.part`]);
      renameSync(`${path}.part`, path);
      return;
    } catch { rmSync(`${path}.part`, { force: true }); }
  }
  throw Error(`Download failed: ${path}`);
}
if (!existsSync(`${root}/LICENSE.txt`)) await download(`${base}/LICENSE.md`, `${root}/LICENSE.txt`);
const queue = [...new Set(Object.values(mapping))];
const failures = [];
async function worker() {
while (queue.length) {
  const source = queue.shift();
  try {
  mkdirSync(`${root}/${source}`, { recursive: true });
  for (const frame of [0, 1]) {
    const path = `${root}/${source}/${frame}.jpg`;
    if (!existsSync(path)) await download(`${base}/exercises/${source}/${frame}.jpg`, path);
    const bytes = readFileSync(path);
    if (bytes[0] !== 0xff || bytes[1] !== 0xd8) throw Error(`Invalid JPEG: ${path}`);
  }
  console.log(source);
  } catch (error) { failures.push(source); console.error(error.message); }
}
}
await Promise.all(Array.from({ length: 6 }, worker));
if (failures.length) { console.error('Retry import for:', failures); process.exitCode = 1; }
