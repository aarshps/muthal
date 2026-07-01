/* App-shell service worker for Muthal.
 *
 * Design rules (learned across the family):
 *  - A navigation handler must NEVER resolve to `undefined` — that makes the
 *    top-level response fail ("This page couldn't load" on iOS standalone PWAs).
 *  - Serve fresh navigation HTML from the network (cached HTML references hashed
 *    chunks that vanish after a deploy); keep only an install-time shell offline.
 *  - Only intercept what we understand (navigations + immutable static assets);
 *    let everything else (Firebase, Google) pass straight through.
 */
const CACHE = "muthal-v1";
const SHELL = ["/", "/manifest.json", "/icon-192.png", "/icon-512.png"];

const OFFLINE_HTML = `<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Muthal</title>
<body style="font-family:system-ui,-apple-system,sans-serif;background:#fafafa;color:#1c1b1f;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center">
<div><h1 style="font-weight:800">You're offline</h1>
<p style="opacity:.7">Reconnect and reload to use Muthal.</p>
<button onclick="location.reload()" style="margin-top:12px;padding:10px 20px;border:0;border-radius:999px;background:#1c1b1f;color:#fafafa;font-weight:700">Reload</button></div>`;

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((c) => c.addAll(SHELL))
      .catch(() => {})
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request).catch(async () => {
        const cached = await caches.match("/", { ignoreSearch: true });
        return (
          cached ||
          new Response(OFFLINE_HTML, {
            status: 200,
            headers: { "Content-Type": "text/html; charset=utf-8" },
          })
        );
      }),
    );
    return;
  }

  if (
    url.pathname.startsWith("/_next/static/") ||
    url.pathname.startsWith("/icon")
  ) {
    event.respondWith(
      caches.match(request).then(
        (cached) =>
          cached ||
          fetch(request).then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(CACHE).then((c) => c.put(request, copy));
            }
            return res;
          }),
      ),
    );
    return;
  }
});
