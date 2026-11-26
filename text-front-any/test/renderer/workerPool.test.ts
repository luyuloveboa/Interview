import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterAll, describe, expect, it } from 'vitest';
import { GlyphError } from '../../src/errors';
import { FontRegistry, hashBytes, UploadedFontCache } from '../../src/font/registry';
import { parseRenderOptions } from '../../src/options';
import { InlineRenderer, type RenderJob } from '../../src/renderer/renderer';
import { WorkerPoolRenderer } from '../../src/renderer/workerPool';

const fontsDir = fileURLToPath(new URL('../../fonts/', import.meta.url));
const pool = new WorkerPoolRenderer({ fontsDir, threads: 2, maxQueue: 64, timeoutMs: 10_000 });
const inline = new InlineRenderer(FontRegistry.fromDirectory(fontsDir), new UploadedFontCache());
afterAll(() => pool.close());

const job = (text: string, font = 'DejaVuSans'): RenderJob => ({
  font: { kind: 'registry', name: font },
  options: parseRenderOptions({ text }),
});

async function errorOf(p: Promise<unknown>): Promise<GlyphError> {
  try {
    await p;
  } catch (e) {
    if (e instanceof GlyphError) return e;
    throw e;
  }
  throw new Error('expected a GlyphError');
}

describe('WorkerPoolRenderer', () => {
  it('produces byte-identical output to the inline renderer', async () => {
    const [a, b] = await Promise.all([pool.render(job('Hello workers')), inline.render(job('Hello workers'))]);
    expect(a.width).toBe(b.width);
    expect(a.height).toBe(b.height);
    expect(Buffer.compare(a.png, b.png)).toBe(0);
  });

  it('renders uploaded font bytes', async () => {
    const bytes = readFileSync(`${fontsDir}/DejaVuSerif.ttf`);
    const r = await pool.render({ font: { kind: 'upload', hash: hashBytes(bytes), bytes }, options: parseRenderOptions({ text: 'x' }) });
    expect(r.png.subarray(1, 4).toString('ascii')).toBe('PNG');
  });

  it('handles many concurrent jobs', async () => {
    const results = await Promise.all(Array.from({ length: 40 }, (_, i) => pool.render(job(`job ${i}`))));
    expect(results).toHaveLength(40);
    expect(pool.stats().completed).toBeGreaterThanOrEqual(40);
  });

  it.each<[string, RenderJob, number, string]>([
    ['unknown font', job('x', 'Nope'), 404, 'font_not_found'],
    [
      'garbage upload',
      { font: { kind: 'upload', hash: 'h', bytes: new Uint8Array([1, 2, 3]) }, options: parseRenderOptions({ text: 'x' }) },
      422,
      'invalid_font',
    ],
    ['too-large image', { ...job('x'), options: parseRenderOptions({ text: 'W'.repeat(500), size: 200 }) }, 413, 'image_too_large'],
  ])('turns a worker-side %s back into a GlyphError', async (_label, j, status, code) => {
    const err = await errorOf(pool.render(j));
    expect(err.status).toBe(status);
    expect(err.code).toBe(code);
  });

  it('sheds load with 503 overloaded when the queue is full', async () => {
    const tiny = new WorkerPoolRenderer({ fontsDir, threads: 1, maxQueue: 1, timeoutMs: 10_000 });
    try {
      const big = parseRenderOptions({ text: 'W'.repeat(8), size: 400 });
      const results = await Promise.allSettled(Array.from({ length: 6 }, () => tiny.render({ ...job('x'), options: big })));
      const rejected = results.filter((r) => r.status === 'rejected').map((r) => (r as PromiseRejectedResult).reason as GlyphError);
      expect(rejected.length).toBeGreaterThan(0);
      expect(rejected[0].code).toBe('overloaded');
      expect(rejected[0].status).toBe(503);
      expect(rejected[0].headers['retry-after']).toBe('1');
    } finally {
      await tiny.close();
    }
  });

  it('kills a render that runs past the timeout with 503 render_timeout', async () => {
    const strict = new WorkerPoolRenderer({ fontsDir, threads: 1, maxQueue: 4, timeoutMs: 1 });
    try {
      const err = await errorOf(strict.render({ ...job('x'), options: parseRenderOptions({ text: 'W'.repeat(8), size: 400 }) }));
      expect(err.code).toBe('render_timeout');
      expect(err.status).toBe(503);
    } finally {
      await strict.close();
    }
  });
});
