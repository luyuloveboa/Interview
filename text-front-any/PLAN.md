# Glyph implementation plan

## Running it

### Unit tests

```sh
npm install
npm test                                  # all 206 tests (unit + HTTP + worker pool)
npx vitest run test/server/security.test.ts   # one file
npx vitest run -t "rate limiting"             # tests whose name matches
npm run test:watch                        # re-run on save
npm run typecheck                         # TypeScript check
```

### Start the service locally

The server needs API keys (`name:key`, each key at least 16 characters). It refuses to start without them.

PowerShell:

```powershell
$env:GLYPH_API_KEYS = "dev:dev-key-0123456789abcdef"
npm start
```

Git Bash / macOS / Linux:

```sh
GLYPH_API_KEYS="dev:dev-key-0123456789abcdef" npm start
```

For quick local poking without keys only: `GLYPH_AUTH_DISABLED=true`.

| Env var | Default | Meaning |
|---|---|---|
| `GLYPH_API_KEYS` | required | `team-a:key1,team-b:key2` |
| `GLYPH_AUTH_DISABLED` | off | `true` = no auth (dev only) |
| `PORT` / `HOST` | 3000 / 127.0.0.1 | listen address |
| `FONTS_DIR` | `fonts` | fonts served by name |
| `RENDER_WORKERS` | CPU cores − 1 | worker threads; `0` = render on the main thread |
| `RENDER_QUEUE_MAX` | 256 | jobs allowed to wait; beyond this → 503 `overloaded` |
| `RENDER_TIMEOUT_MS` | 5000 | longer renders are killed → 503 `render_timeout` |
| `IMAGE_CACHE_MB` | 64 | finished-PNG cache; `0` = off |
| `RATE_LIMIT_PER_KEY` | 1200 | requests per minute per API key |
| `RATE_LIMIT_PER_IP` | 30 | per minute per IP for requests with a missing or bad key |
| `RATE_LIMIT_DISABLED` | off | `true` = no rate limiting |

### Call the API

Git Bash (or `curl.exe` in PowerShell, since plain `curl` there is a different command):

```sh
KEY=dev-key-0123456789abcdef

curl localhost:3000/healthz                                   # no key needed
curl -H "x-api-key: $KEY" localhost:3000/fonts
curl -H "x-api-key: $KEY" localhost:3000/stats                # cache + worker counters

# Render with a bundled font (JSON). Open hi.png to see the result.
curl -o hi.png -H "Authorization: Bearer $KEY" -H 'content-type: application/json'   -d '{"text":"Hello
World","font":"DejaVuSerif","size":64,"color":"#1d4ed8","background":"#fef3c7","padding":20}'   localhost:3000/render

# Same thing with query params
curl -o hi.png -H "x-api-key: $KEY" "localhost:3000/render?text=Hello&font=DejaVuSansMono&size=40"

# Upload your own font
curl -o up.png -H "x-api-key: $KEY" -F font=@fonts/DejaVuSans.ttf -F "text=My font" -F size=48 localhost:3000/render/upload

# See caching: first call says x-cache: MISS, second says HIT
curl -s -o /dev/null -D - -H "x-api-key: $KEY" "localhost:3000/render?text=cache&font=DejaVuSans" | grep -i -E "x-cache|etag"

# See the errors
curl -i localhost:3000/fonts                                              # 401 unauthorized
curl -i -H "x-api-key: $KEY" "localhost:3000/render?text=a&font=Arial"   # 404 font_not_found
```

PowerShell without curl (JSON route):

```powershell
$h = @{ "x-api-key" = "dev-key-0123456789abcdef" }
$body = @{ text = "Hello"; font = "DejaVuSans"; size = 48 } | ConvertTo-Json
Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://127.0.0.1:3000/render -Headers $h -ContentType "application/json" -Body $body -OutFile hi.png
Invoke-RestMethod -Uri http://127.0.0.1:3000/stats -Headers $h
```

To see rate limiting, start the server with `RATE_LIMIT_PER_KEY=5` and call the same route 6 times: the 6th returns `429 rate_limited` with a `retry-after` header.

### Load test

Start the server with rate limiting relaxed, then run the load test in a second terminal:

```powershell
$env:GLYPH_API_KEYS = "dev:dev-key-0123456789abcdef"; $env:RATE_LIMIT_PER_KEY = "1000000"; npm start
# second terminal:
npm run loadtest -- --key dev-key-0123456789abcdef --duration 10 --connections 50
```

It runs a cached scenario (the same request every time) and an uncached one (unique text on every request), then prints req/s, p50 and p99 latency, and status codes. Measured on this machine (24 cores, 50 connections, 8 s):

| Mode | Cached req/s | Uncached req/s | Uncached p50 / p99 |
|---|---|---|---|
| 23 worker threads | 13,212 | **1,048** | 39 / 146 ms |
| Inline (`RENDER_WORKERS=0`) | 13,947 | 222 | 222 / 269 ms |

### Errors

Errors always come back as JSON `{"error": "<code>", "message": "..."}`:

| Status | Code | When |
|---|---|---|
| 304 | (no body) | `If-None-Match` matches the image's ETag |
| 400 | `invalid_request` | Bad input |
| 401 | `unauthorized` | Missing or wrong API key |
| 404 | `font_not_found` | Unknown font name |
| 404 | `not_found` | Unknown route |
| 413 | `text_too_long` | Text over the length limit |
| 413 | `image_too_large` | Output over 4096×4096 |
| 413 | `payload_too_large` | Body or upload too big |
| 415 | `unsupported_media_type` | Wrong content type |
| 422 | `invalid_font` | Upload isn't a usable font |
| 429 | `rate_limited` | Over the per-key or per-IP limit (`retry-after` header) |
| 503 | `overloaded` | Render queue full (`retry-after` header) |
| 503 | `render_timeout` | Render took longer than `RENDER_TIMEOUT_MS` |
| 500 | `internal_error` | Anything unexpected; no internal details |

Text plus a TTF font goes in, and a PNG comes out. The core pipeline comes first, then an HTTP service.

**Decisions:** TypeScript on Node. Callers either name a preloaded font or upload their own. Antialiasing, kerning, multi-line text, colors and padding are all included. Service hardening stays light: basic limits, a font cache and a health check.

## 1. Stack and the "build it yourself" line

| Concern | Choice | Notes |
|---|---|---|
| Font parsing | `opentype.js` | Used **only** for outlines (`glyph.path.commands`), advance widths, kerning, `unitsPerEm`, ascender and descender. We never call its `draw()` or any canvas rendering. |
| Rasterizing | **Our own code** | Flatten curves into line segments, then do a scanline fill with the nonzero winding rule. Antialiasing samples 4 lines per pixel row and measures exact partial coverage at the left and right ends of each filled run. |
| Layout | **Our own code** | Advance widths plus kerning, line breaks on `\n`, baseline from the ascender. |
| Image output | **Our own PNG encoder** | Uses Node's built-in `zlib.deflateSync` and `zlib.crc32`, with no extra package. |
| HTTP | Fastify, plus `@fastify/multipart` for uploads | Has built-in limits on request body size, timeouts and logging. |
| Tests | Vitest | |

## 2. Project layout

```
src/
  font/loadFont.ts      parse TTF bytes → Font wrapper (metrics + glyph lookup)
  font/registry.ts      preload fonts/ at startup, lookup by name (cache)
  layout/layout.ts      text + font + size → positioned glyphs + bounding box
  raster/flatten.ts     path commands (M/L/Q/C/Z) → line segments in pixel space
  raster/rasterize.ts   segments → coverage mask (Float32Array, 0..1) with AA
  canvas/canvas.ts      RGBA canvas: fill background, blend coverage in a color
  encode/png.ts         RGBA buffer → PNG bytes
  render.ts             pipeline: renderText(opts) → PNG Buffer
  options.ts            validate/normalize render options (shared by CLI + HTTP)
  server/app.ts         buildApp(): Fastify instance, routes, error handler
  server/index.ts       start listening (PORT env, default 3000)
  cli.ts                `npm run render -- "Hello" DejaVuSans out.png`
test/                   mirrors src/, one test file per module
```

## 3. Key functions, in build order

**Core pipeline:**

1. **`loadFont(bytes)`** wraps an opentype.js `Font` and exposes `glyphFor(char)`, `kerning(a, b)` and `metrics`. A character the font doesn't have falls back to glyph 0, the font's "missing glyph" symbol.
2. **`layoutText(font, text, size)`** calculates `scale = size / unitsPerEm` and walks the characters. For each one it adds the kerning against the previous glyph, records the glyph position, and moves right by the glyph's advance width. On `\n` it resets x and moves down one line height. It returns the glyph positions and the overall text width and height.
3. **`flattenPath(commands, scale, dx, dy)`** turns the outline into straight line segments. It flips y, because fonts measure y upward and images measure it downward, and splits curves into small straight pieces.
4. **`rasterize(segments, w, h)`** fills the outline with the nonzero winding rule and supersampling. It returns a coverage value per pixel: the fraction of the pixel inside the glyph.
5. **`Canvas.fill(bg)`** and **`Canvas.blendMask(mask, color)`** create the RGBA buffer and mix the text color over the background according to each pixel's coverage.
6. **`encodePng(w, h, rgba)`** writes the PNG signature and the IHDR, IDAT and IEND chunks.
7. **`renderText(font, options)`** chains steps 2 to 6 and returns a PNG `Buffer`.

**Service:**

8. **`FontRegistry`** loads every font in `fonts/` once at startup and keeps the parsed fonts in memory.
9. **`parseRenderOptions(input)`** validates and normalizes requests. The CLI and both HTTP routes share it.
10. **Routes:**
    - `POST /render` takes JSON `{text, font, size?, color?, background?, padding?}` and returns `image/png`.
    - `POST /render/upload` takes a multipart form with a `font` file field plus the same fields.
    - `GET /fonts` lists the available font names.
    - `GET /healthz` returns `{status: "ok"}`.
11. **Limits and errors:**
    - **Limits:** text up to 2,000 characters, size from 4 to 512 px, output up to 4096×4096 pixels, uploads up to 5 MB, and a 10 s request timeout.
    - **Errors:** every error returns the same JSON shape, `{error, message}`. The status codes are 400, 404, 413, 422 and 500.
    - **Logging:** JSON logs with a request ID.

## 4. Tests

There is one Vitest file per module under `test/`. The input/expected tests are written as `it.each([...])` tables, so a new case is one more row. The server is tested with `app.inject()`, so no real port is needed.

## 5. Code comments

Each exported function gets a short comment. Short inline comments go only on the parts that aren't obvious: the y-flip, the winding rule, supersampling and PNG filter bytes.

## 6. Deliberately left out

- ~~A worker-thread pool~~, ~~caching rendered images and ETags~~, ~~load testing~~, ~~rate limiting and authentication~~: done (see "Running it").
- Shared state across processes: the cache and rate-limit counters are in memory, so each process has its own. Running several processes would need Redis or similar.
- Per-team quotas beyond requests per minute, and key rotation without a restart.
- Complex text shaping: right-to-left scripts, ligatures, combining marks.
- Font hinting.
- Text wrapping to a set width.
- Deployment, which the README excludes.

## 7. Cut order if time runs short

1. Kerning.
2. Upload caching.
3. Multipart upload.

The core pipeline and its tests always come first.
