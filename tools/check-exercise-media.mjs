import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const mapping = JSON.parse(readFileSync('tools/exercise-media-map.json', 'utf8'));
const seed = readFileSync('domain/catalog/src/main/kotlin/ru/bodybuild/domain/catalog/SeedCatalog.kt', 'utf8');
const extended = readFileSync('domain/catalog/src/main/kotlin/ru/bodybuild/domain/catalog/ExtendedCatalog.kt', 'utf8');
const ids = [...seed.matchAll(/exercise\("([^"]+)"/g), ...extended.matchAll(/variant\("([^"]+)"/g)].map(match => match[1]);
assert.deepEqual(Object.keys(mapping).sort(), ids.sort(), 'Every exercise must have photos');
const catalog = readFileSync('feature/exercise/src/main/kotlin/ru/bodybuild/feature/exercise/ExercisePhotoCatalog.kt', 'utf8').split('val notes')[0];
const kotlin = Object.fromEntries([...catalog.matchAll(/"([^"]+)" to "([^"]+)"/g)].map(match => [match[1], match[2]]));
assert.deepEqual(kotlin, mapping, 'Import and runtime mappings must match');
const root = 'feature/exercise/src/main/assets/exercise-photos';
assert.match(readFileSync(`${root}/LICENSE.txt`, 'utf8'), /public domain/i);
let total = 0;
for (const source of new Set(Object.values(mapping))) {
  for (const frame of [0, 1]) {
    const path = `${root}/${source}/${frame}.jpg`;
    const bytes = readFileSync(path);
    assert.equal(bytes.readUInt16BE(0), 0xffd8, path);
    assert.equal(bytes.readUInt16BE(bytes.length - 2), 0xffd9, `Truncated JPEG: ${path}`);
    execFileSync('magick', ['-regard-warnings', path, 'null:'], { stdio: 'pipe' });
    total++;
  }
}
console.log(`OK: ${ids.length} exercises, ${total} fully decoded JPEGs, matching runtime mapping and license.`);
