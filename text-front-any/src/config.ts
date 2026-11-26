import { availableParallelism } from 'node:os';
import { ApiKeyStore } from './auth';

/** Everything the server process reads from the environment, validated once at startup. */
export interface Config {
  port: number;
  host: string;
  fontsDir: string;
  /** Configured API keys, or 'disabled' (only when explicitly opted out). */
  auth: ApiKeyStore | 'disabled';
  /** Render worker threads. 0 = render on the main thread. */
  workers: number;
  queueMax: number;
  renderTimeoutMs: number;
  imageCacheBytes: number;
  /** Requests per minute: per valid API key, and per IP for requests without a valid key. */
  rateLimit: { perKey: number; perIp: number } | false;
}

function int(env: NodeJS.ProcessEnv, name: string, fallback: number, min: number): number {
  const raw = env[name];
  if (raw === undefined || raw === '') return fallback;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < min) throw new Error(`${name} must be an integer >= ${min} (got "${raw}")`);
  return n;
}

const truthy = (v?: string) => v === '1' || v?.toLowerCase() === 'true';

/**
 * Build the Config from env vars. Throws with a readable message on bad values.
 *
 * Auth fails closed: with no GLYPH_API_KEYS the server refuses to start, unless
 * GLYPH_AUTH_DISABLED=true is set (meant for local development only).
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  let auth: Config['auth'];
  if (env.GLYPH_API_KEYS) {
    auth = ApiKeyStore.parse(env.GLYPH_API_KEYS);
  } else if (truthy(env.GLYPH_AUTH_DISABLED)) {
    auth = 'disabled';
  } else {
    throw new Error('GLYPH_API_KEYS is required (format "team:key,team2:key2"); set GLYPH_AUTH_DISABLED=true for local dev only');
  }

  const rateLimitOff = truthy(env.RATE_LIMIT_DISABLED);
  return {
    port: int(env, 'PORT', 3000, 0),
    host: env.HOST || '127.0.0.1',
    fontsDir: env.FONTS_DIR || 'fonts',
    auth,
    // Leave one core for the HTTP event loop.
    workers: int(env, 'RENDER_WORKERS', Math.max(1, availableParallelism() - 1), 0),
    queueMax: int(env, 'RENDER_QUEUE_MAX', 256, 1),
    renderTimeoutMs: int(env, 'RENDER_TIMEOUT_MS', 5000, 1),
    imageCacheBytes: int(env, 'IMAGE_CACHE_MB', 64, 0) * 1024 * 1024,
    rateLimit: rateLimitOff
      ? false
      : { perKey: int(env, 'RATE_LIMIT_PER_KEY', 1200, 1), perIp: int(env, 'RATE_LIMIT_PER_IP', 30, 1) },
  };
}
