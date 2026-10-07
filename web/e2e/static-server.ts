import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname } from "node:path";
/** An isolated origin we can actually shut down to prove offline reload without protocol emulation. */
export async function staticServer() {
  const root = resolve("dist");
  const mime: Record<string, string> = {
    ".html": "text/html",
    ".js": "text/javascript",
    ".css": "text/css",
    ".json": "application/json",
    ".webmanifest": "application/manifest+json",
    ".jpg": "image/jpeg",
    ".png": "image/png",
    ".svg": "image/svg+xml",
  };
  const server = createServer(async (req, res) => {
    const path = resolve(
      root,
      "." + new URL(req.url!, "http://localhost").pathname,
    );
    if (path !== root && !path.startsWith(root + "/")) {
      res.writeHead(403).end();
      return;
    }
    const file = path === root ? resolve(root, "index.html") : path;
    try {
      const buffer = await readFile(file);
      res
        .writeHead(200, {
          "Content-Type": mime[extname(file)] ?? "text/plain",
          Vary: "Origin",
        })
        .end(buffer);
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address() as { port: number };
  return {
    url: `http://127.0.0.1:${address.port}`,
    close: () => {
      server.closeAllConnections();
      server.close();
    },
  };
}
