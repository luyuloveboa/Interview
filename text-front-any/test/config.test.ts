import { describe, expect, it } from 'vitest';
import { ApiKeyStore } from '../src/auth';
import { loadConfig } from '../src/config';

const KEYS = 'team-a:0123456789abcdef';

describe('loadConfig', () => {
  it('fails closed when no API keys are configured', () => {
    expect(() => loadConfig({})).toThrow(/GLYPH_API_KEYS is required/);
  });

  it('allows disabling auth only explicitly', () => {
    expect(loadConfig({ GLYPH_AUTH_DISABLED: 'true' }).auth).toBe('disabled');
  });

  it('parses API keys and applies defaults', () => {
    const c = loadConfig({ GLYPH_API_KEYS: KEYS });
    expect(c.auth).toBeInstanceOf(ApiKeyStore);
    expect(c).toMatchObject({ port: 3000, host: '127.0.0.1', fontsDir: 'fonts', queueMax: 256, renderTimeoutMs: 5000 });
    expect(c.workers).toBeGreaterThanOrEqual(1);
    expect(c.imageCacheBytes).toBe(64 * 1024 * 1024);
    expect(c.rateLimit).toEqual({ perKey: 1200, perIp: 30 });
  });

  it('reads overrides', () => {
    const c = loadConfig({
      GLYPH_API_KEYS: KEYS,
      PORT: '8080',
      RENDER_WORKERS: '0',
      RENDER_QUEUE_MAX: '10',
      RENDER_TIMEOUT_MS: '250',
      IMAGE_CACHE_MB: '0',
      RATE_LIMIT_PER_KEY: '5',
      RATE_LIMIT_PER_IP: '2',
    });
    expect(c).toMatchObject({ port: 8080, workers: 0, queueMax: 10, renderTimeoutMs: 250, imageCacheBytes: 0 });
    expect(c.rateLimit).toEqual({ perKey: 5, perIp: 2 });
  });

  it('can disable rate limiting', () => {
    expect(loadConfig({ GLYPH_API_KEYS: KEYS, RATE_LIMIT_DISABLED: '1' }).rateLimit).toBe(false);
  });

  it.each([
    ['PORT', 'abc'],
    ['RENDER_WORKERS', '-1'],
    ['RENDER_QUEUE_MAX', '0'],
    ['RENDER_TIMEOUT_MS', '1.5'],
    ['RATE_LIMIT_PER_KEY', '0'],
  ])('rejects %s=%s', (name, value) => {
    expect(() => loadConfig({ GLYPH_API_KEYS: KEYS, [name]: value })).toThrow(name);
  });

  it('rejects malformed API keys', () => {
    expect(() => loadConfig({ GLYPH_API_KEYS: 'team-a:short' })).toThrow();
  });
});
