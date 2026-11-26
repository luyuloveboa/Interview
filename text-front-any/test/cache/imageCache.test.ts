import { describe, expect, it, vi } from 'vitest';
import { type CachedImage, ImageCache, renderCacheKey } from '../../src/cache/imageCache';
import { parseRenderOptions } from '../../src/options';

const img = (bytes: number): CachedImage => ({ png: Buffer.alloc(bytes), width: 1, height: 1 });

describe('renderCacheKey', () => {
  const base = parseRenderOptions({ text: 'hi' });

  it('is stable for identical inputs', () => {
    expect(renderCacheKey('r:dejavusans', base)).toBe(renderCacheKey('r:dejavusans', parseRenderOptions({ text: 'hi' })));
  });

  it.each([
    ['font', 'r:dejavuserif', base],
    ['text', 'r:dejavusans', { ...base, text: 'ho' }],
    ['size', 'r:dejavusans', { ...base, size: 49 }],
    ['color', 'r:dejavusans', { ...base, color: [1, 0, 0, 255] }],
    ['background', 'r:dejavusans', { ...base, background: [0, 0, 0, 0] }],
    ['padding', 'r:dejavusans', { ...base, padding: 0 }],
  ] as const)('changes when %s changes', (_label, fontId, opts) => {
    expect(renderCacheKey(fontId, opts as typeof base)).not.toBe(renderCacheKey('r:dejavusans', base));
  });
});

describe('ImageCache', () => {
  it('misses once, then hits', async () => {
    const cache = new ImageCache(1000);
    const create = vi.fn(async () => img(10));
    expect((await cache.getOrCreate('k', create)).status).toBe('MISS');
    expect((await cache.getOrCreate('k', create)).status).toBe('HIT');
    expect(create).toHaveBeenCalledTimes(1);
    expect(cache.stats()).toMatchObject({ entries: 1, bytes: 10, hits: 1, misses: 1 });
  });

  it('coalesces concurrent identical requests into one render', async () => {
    const cache = new ImageCache(1000);
    let resolve!: (v: CachedImage) => void;
    const create = vi.fn(() => new Promise<CachedImage>((r) => (resolve = r)));
    const all = Promise.all([1, 2, 3].map(() => cache.getOrCreate('k', create)));
    resolve(img(10));
    const results = await all;
    expect(create).toHaveBeenCalledTimes(1);
    expect(results.map((r) => r.status)).toEqual(['MISS', 'COALESCED', 'COALESCED']);
    expect(results[1].image).toBe(results[0].image);
  });

  it('does not cache failures, so the next call retries', async () => {
    const cache = new ImageCache(1000);
    await expect(cache.getOrCreate('k', async () => Promise.reject(new Error('boom')))).rejects.toThrow('boom');
    expect((await cache.getOrCreate('k', async () => img(10))).status).toBe('MISS');
  });

  it('evicts least recently used entries to stay under the byte budget', async () => {
    const cache = new ImageCache(100);
    await cache.getOrCreate('a', async () => img(25));
    await cache.getOrCreate('b', async () => img(25));
    await cache.getOrCreate('c', async () => img(25));
    await cache.getOrCreate('a', async () => img(25)); // touch a: b is now the oldest
    await cache.getOrCreate('d', async () => img(25));
    await cache.getOrCreate('e', async () => img(25)); // over budget: evicts b
    expect(cache.stats().bytes).toBeLessThanOrEqual(100);
    expect((await cache.getOrCreate('a', async () => img(25))).status).toBe('HIT');
    expect((await cache.getOrCreate('b', async () => img(25))).status).toBe('MISS');
  });

  it('skips caching an image bigger than a quarter of the budget', async () => {
    const cache = new ImageCache(100);
    await cache.getOrCreate('big', async () => img(26));
    expect(cache.stats().entries).toBe(0);
  });
});
