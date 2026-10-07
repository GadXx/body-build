import { cpSync, readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const dest = resolve(root, "web/public");
mkdirSync(dest, { recursive: true });
cpSync(
  resolve(root, "feature/exercise/src/main/assets/exercise-photos"),
  resolve(dest, "exercise-photos"),
  { recursive: true },
);
const sources = JSON.parse(
  readFileSync(resolve(root, "tools/exercise-media-map.json"), "utf8"),
);
const kotlin = readFileSync(
  resolve(
    root,
    "feature/exercise/src/main/kotlin/ru/bodybuild/feature/exercise/ExercisePhotoCatalog.kt",
  ),
  "utf8",
);
const notes = Object.fromEntries(
  [...kotlin.split("val notes")[1].matchAll(/"([^"]+)" to "([^"]+)"/g)].map(
    (m) => [m[1], m[2]],
  ),
);
writeFileSync(
  resolve(dest, "media.json"),
  JSON.stringify({ sources, notes }, null, 2),
);
console.log(`Synced photos for ${Object.keys(sources).length} exercises`);
