import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterAll, describe, expect, it } from 'vitest';
import { FontRegistry } from '../../src/font/registry';
import { LIMITS } from '../../src/limits';
import { buildApp } from '../../src/server/app';
import { decodePng } from '../helpers';

const fontsDir = fileURLToPath(new URL('../../fonts/', import.meta.url));
// Auth and rate limits have their own tests (security.test.ts); this file tests routes and validation.
const app = buildApp({ registry: FontRegistry.fromDirectory(fontsDir), auth: 'disabled', imageCache: false });
afterAll(() => app.close());

const sansBytes = readFileSync(`${fontsDir}/DejaVuSans.ttf`);

/** Build a multipart body for /render/upload. */
function uploadForm(fields: Record<string, string>, file?: { name?: string; bytes: Uint8Array }) {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.append(k, v);
  if (file) form.append(file.name ?? 'font', new Blob([new Uint8Array(file.bytes)]), 'font.ttf');
  return form;
}

describe('GET /healthz and /fonts', () => {
  it('reports healthy', async () => {
    const res = await app.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok' });
  });

  it('lists the bundled fonts', async () => {
    const res = await app.inject({ method: 'GET', url: '/fonts' });
    expect(res.json()).toEqual({ fonts: ['DejaVuSans', 'DejaVuSansMono', 'DejaVuSerif'] });
  });
});

describe('POST /render', () => {
  it('returns a PNG for a valid request', async () => {
    const res = await app.inject({ method: 'POST', url: '/render', payload: { text: 'Hello', font: 'DejaVuSans' } });
    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toBe('image/png');
    const img = decodePng(res.rawPayload);
    expect(img.width).toBeGreaterThan(50);
  });

  it('applies size, colors and padding', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render',
      payload: { text: 'Hi', font: 'DejaVuSerif', size: 20, color: '#f00', background: 'transparent', padding: 0 },
    });
    const img = decodePng(res.rawPayload);
    expect([...img.data.subarray(0, 4)]).toEqual([0, 0, 0, 0]); // transparent corner
    expect(img.height).toBeLessThan(30);
  });

  it.each<[string, unknown, number, string]>([
    ['missing text', { font: 'DejaVuSans' }, 400, 'invalid_request'],
    ['missing font', { text: 'a' }, 400, 'invalid_request'],
    ['unknown font', { text: 'a', font: 'Nope' }, 404, 'font_not_found'],
    ['bad size', { text: 'a', font: 'DejaVuSans', size: 9999 }, 400, 'invalid_request'],
    ['bad color', { text: 'a', font: 'DejaVuSans', color: 'blue' }, 400, 'invalid_request'],
    ['text too long', { text: 'a'.repeat(LIMITS.maxTextLength + 1), font: 'DejaVuSans' }, 413, 'text_too_long'],
    ['image too large', { text: 'W'.repeat(500), font: 'DejaVuSans', size: 200 }, 413, 'image_too_large'],
    ['array body', [1, 2], 400, 'invalid_request'],
  ])('rejects %s', async (_label, payload, status, code) => {
    const res = await app.inject({ method: 'POST', url: '/render', payload: payload as object });
    expect(res.statusCode).toBe(status);
    expect(res.json()).toMatchObject({ error: code });
    expect(typeof res.json().message).toBe('string');
  });

  it('rejects malformed JSON with 400', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render',
      headers: { 'content-type': 'application/json' },
      payload: '{"text": ',
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('invalid_request');
  });

  it('rejects an oversized JSON body with 413', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render',
      payload: { text: 'a', font: 'DejaVuSans', junk: 'x'.repeat(100 * 1024) },
    });
    expect(res.statusCode).toBe(413);
    expect(res.json().error).toBe('payload_too_large');
  });

  it('rejects an unsupported content type with 415', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render',
      headers: { 'content-type': 'application/xml' },
      payload: '<text/>',
    });
    expect(res.statusCode).toBe(415);
    expect(res.json().error).toBe('unsupported_media_type');
  });
});

describe('GET /render', () => {
  it('renders from query params', async () => {
    const res = await app.inject({ method: 'GET', url: '/render?text=Hi%20there&font=DejaVuSansMono&size=32' });
    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toBe('image/png');
  });

  it('validates query params the same way', async () => {
    const res = await app.inject({ method: 'GET', url: '/render?text=Hi&font=DejaVuSans&size=abc' });
    expect(res.statusCode).toBe(400);
  });
});

describe('POST /render/upload', () => {
  it('renders with an uploaded font', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render/upload',
      payload: uploadForm({ text: 'Uploaded', size: '40' }, { bytes: sansBytes }),
    });
    expect(res.statusCode).toBe(200);
    expect(decodePng(res.rawPayload).width).toBeGreaterThan(100);
  });

  it('rejects garbage font bytes with 422, not 500', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render/upload',
      payload: uploadForm({ text: 'x' }, { bytes: new TextEncoder().encode('not a font') }),
    });
    expect(res.statusCode).toBe(422);
    expect(res.json().error).toBe('invalid_font');
  });

  it('rejects a missing font file', async () => {
    const res = await app.inject({ method: 'POST', url: '/render/upload', payload: uploadForm({ text: 'x' }) });
    expect(res.statusCode).toBe(400);
  });

  it('rejects a file under the wrong field name', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render/upload',
      payload: uploadForm({ text: 'x' }, { name: 'file', bytes: sansBytes }),
    });
    expect(res.statusCode).toBe(400);
  });

  it('rejects fonts over the upload size limit with 413', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render/upload',
      payload: uploadForm({ text: 'x' }, { bytes: new Uint8Array(LIMITS.maxFontBytes + 1) }),
    });
    expect(res.statusCode).toBe(413);
    expect(res.json().error).toBe('payload_too_large');
  });

  it('rejects a non-multipart request with 415', async () => {
    const res = await app.inject({ method: 'POST', url: '/render/upload', payload: { text: 'x' } });
    expect(res.statusCode).toBe(415);
    expect(res.json().error).toBe('unsupported_media_type');
  });

  it('validates form fields', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/render/upload',
      payload: uploadForm({ text: '' }, { bytes: sansBytes }),
    });
    expect(res.statusCode).toBe(400);
  });
});

describe('unknown routes', () => {
  it('returns JSON 404', async () => {
    const res = await app.inject({ method: 'GET', url: '/nope' });
    expect(res.statusCode).toBe(404);
    expect(res.json().error).toBe('not_found');
  });
});
