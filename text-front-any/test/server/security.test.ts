// Tests for auth, rate limiting, image caching/ETags and overload handling at the HTTP layer.
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it } from 'vitest';
import { ApiKeyStore } from '../../src/auth';
import { ImageCache } from '../../src/cache/imageCache';
import { GlyphError } from '../../src/errors';
import { FontRegistry } from '../../src/font/registry';
import type { Renderer } from '../../src/renderer/renderer';
import { buildApp, type AppOptions } from '../../src/server/app';

const registry = FontRegistry.fromDirectory(fileURLToPath(new URL('../../fonts/', import.meta.url)));
const KEY_A = 'team-a-secret-key-0001';
const KEY_B = 'team-b-secret-key-0002';
const keys = ApiKeyStore.parse(`team-a:${KEY_A},team-b:${KEY_B}`);

let apps: ReturnType<typeof buildApp>[] = [];
afterEach(async () => {
  await Promise.all(apps.map((a) => a.close()));
  apps = [];
});
function makeApp(opts: Partial<AppOptions> = {}) {
  const app = buildApp({ registry, auth: keys, ...opts });
  apps.push(app);
  return app;
}

const renderReq = (key?: string, body: object = { text: 'hi', font: 'DejaVuSans' }, headers: Record<string, string> = {}) => ({
  method: 'POST' as const,
  url: '/render',
  payload: body,
  headers: { ...(key ? { authorization: `Bearer ${key}` } : {}), ...headers },
});

describe('authentication', () => {
  it.each([
    ['no key', {}],
    ['wrong bearer key', { authorization: 'Bearer not-a-real-key-at-all' }],
    ['wrong x-api-key', { 'x-api-key': 'not-a-real-key-at-all' }],
    ['non-bearer scheme', { authorization: `Basic ${KEY_A}` }],
  ])('rejects %s with 401', async (_label, headers) => {
    const res = await makeApp().inject({ method: 'POST', url: '/render', payload: { text: 'hi', font: 'DejaVuSans' }, headers });
    expect(res.statusCode).toBe(401);
    expect(res.json().error).toBe('unauthorized');
    expect(res.headers['www-authenticate']).toBe('Bearer');
  });

  it.each([
    ['Authorization: Bearer', { authorization: `Bearer ${KEY_A}` }],
    ['X-API-Key', { 'x-api-key': KEY_B }],
  ])('accepts a valid key via %s', async (_label, headers) => {
    const res = await makeApp().inject({ method: 'POST', url: '/render', payload: { text: 'hi', font: 'DejaVuSans' }, headers });
    expect(res.statusCode).toBe(200);
  });

  it.each(['/fonts', '/stats', '/render?text=a&font=DejaVuSans'])('protects GET %s', async (url) => {
    expect((await makeApp().inject({ method: 'GET', url })).statusCode).toBe(401);
  });

  it('rejects before reading an upload body', async () => {
    const form = new FormData();
    form.append('text', 'x');
    form.append('font', new Blob([new Uint8Array(1024)]), 'f.ttf');
    const res = await makeApp().inject({ method: 'POST', url: '/render/upload', payload: form });
    expect(res.statusCode).toBe(401);
  });

  it('leaves /healthz open', async () => {
    expect((await makeApp().inject({ method: 'GET', url: '/healthz' })).statusCode).toBe(200);
  });
});

describe('rate limiting', () => {
  const limited = () => makeApp({ rateLimit: { perKey: 3, perIp: 2 } });

  it('allows perKey requests per key, then 429 with rate-limit headers', async () => {
    const app = limited();
    for (let i = 0; i < 3; i++) {
      const res = await app.inject(renderReq(KEY_A));
      expect(res.statusCode).toBe(200);
      expect(res.headers['x-ratelimit-remaining']).toBe(String(2 - i));
    }
    const res = await app.inject(renderReq(KEY_A));
    expect(res.statusCode).toBe(429);
    expect(res.json().error).toBe('rate_limited');
    expect(res.headers['retry-after']).toBeDefined();
  });

  it('gives each key its own bucket', async () => {
    const app = limited();
    for (let i = 0; i < 3; i++) await app.inject(renderReq(KEY_A));
    expect((await app.inject(renderReq(KEY_A))).statusCode).toBe(429);
    expect((await app.inject(renderReq(KEY_B))).statusCode).toBe(200);
  });

  it('limits bad-key guessing per IP, even with a different fake key each time', async () => {
    const app = limited();
    const codes = [];
    for (let i = 0; i < 4; i++) codes.push((await app.inject(renderReq(`guess-${i}-xxxxxxxxxxxxxxxx`))).statusCode);
    expect(codes).toEqual([401, 401, 429, 429]);
  });

  it("does not let guessers use up a real key's budget", async () => {
    const app = limited();
    for (let i = 0; i < 3; i++) await app.inject(renderReq('bad-key-bad-key-bad-key'));
    expect((await app.inject(renderReq(KEY_A))).statusCode).toBe(200);
  });

  it('never rate limits /healthz', async () => {
    const app = limited();
    for (let i = 0; i < 5; i++) expect((await app.inject({ method: 'GET', url: '/healthz' })).statusCode).toBe(200);
  });
});

describe('image cache and ETags', () => {
  it('renders once (MISS), then serves identical requests from cache (HIT)', async () => {
    const app = makeApp();
    const first = await app.inject(renderReq(KEY_A));
    const second = await app.inject(renderReq(KEY_A));
    expect(first.headers['x-cache']).toBe('MISS');
    expect(second.headers['x-cache']).toBe('HIT');
    expect(Buffer.compare(first.rawPayload, second.rawPayload)).toBe(0);
    expect((await app.inject({ method: 'GET', url: '/stats', headers: { 'x-api-key': KEY_A } })).json().cache).toMatchObject({ hits: 1, misses: 1 });
  });

  it('shares the cache across clients, since output does not depend on who asked', async () => {
    const app = makeApp();
    await app.inject(renderReq(KEY_A));
    expect((await app.inject(renderReq(KEY_B))).headers['x-cache']).toBe('HIT');
  });

  it('treats a font name case-insensitively for caching', async () => {
    const app = makeApp();
    await app.inject(renderReq(KEY_A, { text: 'hi', font: 'DejaVuSans' }));
    expect((await app.inject(renderReq(KEY_A, { text: 'hi', font: 'dejavusans' }))).headers['x-cache']).toBe('HIT');
  });

  it('misses when any option changes', async () => {
    const app = makeApp();
    await app.inject(renderReq(KEY_A));
    const res = await app.inject(renderReq(KEY_A, { text: 'hi', font: 'DejaVuSans', size: 20 }));
    expect(res.headers['x-cache']).toBe('MISS');
  });

  it('answers 304 with no body when If-None-Match matches', async () => {
    const app = makeApp();
    const first = await app.inject(renderReq(KEY_A));
    const etag = first.headers.etag as string;
    expect(etag).toMatch(/^"[0-9a-f]{64}"$/);
    const again = await app.inject(renderReq(KEY_A, undefined, { 'if-none-match': `W/"other", ${etag}` }));
    expect(again.statusCode).toBe(304);
    expect(again.rawPayload.length).toBe(0);
  });

  it('still validates input before answering 304', async () => {
    const res = await makeApp().inject(renderReq(KEY_A, { text: '', font: 'DejaVuSans' }, { 'if-none-match': '*' }));
    expect(res.statusCode).toBe(400);
  });

  it('caches uploaded-font renders by font content', async () => {
    const app = makeApp();
    const { readFileSync } = await import('node:fs');
    const bytes = readFileSync(fileURLToPath(new URL('../../fonts/DejaVuSans.ttf', import.meta.url)));
    const send = () => {
      const form = new FormData();
      form.append('text', 'upload cache');
      form.append('font', new Blob([new Uint8Array(bytes)]), 'f.ttf');
      return app.inject({ method: 'POST', url: '/render/upload', payload: form, headers: { 'x-api-key': KEY_A } });
    };
    expect((await send()).headers['x-cache']).toBe('MISS');
    expect((await send()).headers['x-cache']).toBe('HIT');
  });

  it('can be turned off', async () => {
    const res = await makeApp({ imageCache: false }).inject(renderReq(KEY_A));
    expect(res.headers['x-cache']).toBe('BYPASS');
  });

  it('does not let one failing render poison the cache', async () => {
    let calls = 0;
    const flaky: Renderer = {
      async render() {
        calls++;
        if (calls === 1) throw new GlyphError('overloaded', 503, 'busy', { 'retry-after': '1' });
        return { png: Buffer.from('fake'), width: 1, height: 1 };
      },
      async close() {},
    };
    const app = makeApp({ renderer: flaky, imageCache: new ImageCache(1024) });
    const first = await app.inject(renderReq(KEY_A));
    expect(first.statusCode).toBe(503);
    expect(first.headers['retry-after']).toBe('1');
    expect(first.json().error).toBe('overloaded');
    expect((await app.inject(renderReq(KEY_A))).statusCode).toBe(200);
  });
});
