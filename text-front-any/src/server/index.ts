// Entry point: read config, load fonts, start the worker pool, then listen.
// See loadConfig() in src/config.ts for all env vars (GLYPH_API_KEYS is required).
import { ImageCache } from '../cache/imageCache';
import { loadConfig } from '../config';
import { FontRegistry } from '../font/registry';
import { WorkerPoolRenderer } from '../renderer/workerPool';
import { buildApp } from './app';

let config;
try {
  config = loadConfig();
} catch (err) {
  console.error(`config error: ${(err as Error).message}`);
  process.exit(1);
}

// Fail fast at startup if a bundled font is broken, instead of on the first request.
const registry = FontRegistry.fromDirectory(config.fontsDir);
const renderer =
  config.workers > 0
    ? new WorkerPoolRenderer({
        fontsDir: config.fontsDir,
        threads: config.workers,
        maxQueue: config.queueMax,
        timeoutMs: config.renderTimeoutMs,
      })
    : undefined; // undefined = render on the main thread

const app = buildApp({
  registry,
  auth: config.auth,
  renderer,
  imageCache: config.imageCacheBytes > 0 ? new ImageCache(config.imageCacheBytes) : false,
  rateLimit: config.rateLimit,
  logger: true,
});

app.log.info(
  {
    fonts: registry.names(),
    workers: config.workers,
    auth: config.auth === 'disabled' ? 'DISABLED' : `${config.auth.size} keys`,
    rateLimit: config.rateLimit,
    imageCacheMB: config.imageCacheBytes / 1024 / 1024,
  },
  'starting',
);
if (config.auth === 'disabled') app.log.warn('authentication is DISABLED; do not expose this process');
await app.listen({ port: config.port, host: config.host });

// Graceful shutdown: stop accepting connections, let in-flight requests finish, stop workers.
for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, async () => {
    app.log.info({ signal }, 'shutting down');
    await app.close();
    process.exit(0);
  });
}
