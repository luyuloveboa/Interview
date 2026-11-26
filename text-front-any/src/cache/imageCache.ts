import { createHash } from 'node:crypto';
import type { RenderOptions } from '../options';

/**
 * Bump this whenever rendering output changes, so old cached images and old ETags held by
 * clients stop matching.
 */
export const RENDER_VERSION = 1;

/** A finished render. */
export interface CachedImage {
  png: Buffer;
  width: number;
  height: number;
}

/** HIT = served from the cache, MISS = rendered now, COALESCED = waited on an identical in-flight render. */
export type CacheStatus = 'HIT' | 'MISS' | 'COALESCED';

/**
 * Cache key for one render: a hash of everything that affects the output.
 * `fontId` identifies the font: "r:<name>" for registry fonts, "u:<sha256>" for uploaded ones.
 */
export function renderCacheKey(fontId: string, o: RenderOptions): string {
  const input = JSON.stringify([RENDER_VERSION, fontId, o.text, o.size, o.color, o.background, o.padding]);
  return createHash('sha256').update(input).digest('hex');
}

/**
 * An in-memory LRU cache of finished PNGs, bounded by total bytes.
 *
 * It also coalesces in-flight work: if N identical requests arrive while the first is still
 * rendering, all N share that one render instead of doing N renders.
 * Failed renders are never cached.
 */
export class ImageCache {
  private readonly entries = new Map<string, CachedImage>(); // insertion order = LRU order
  private readonly inflight = new Map<string, Promise<CachedImage>>();
  private bytes = 0;
  private hits = 0;
  private misses = 0;

  /** @param maxBytes total PNG bytes to keep. A single image over maxBytes/4 is not cached. */
  constructor(private readonly maxBytes: number) {}

  /** Return the cached image for `key`, or run `create` once and cache its result. */
  async getOrCreate(key: string, create: () => Promise<CachedImage>): Promise<{ image: CachedImage; status: CacheStatus }> {
    const cached = this.entries.get(key);
    if (cached) {
      // Re-insert to mark as most recently used.
      this.entries.delete(key);
      this.entries.set(key, cached);
      this.hits++;
      return { image: cached, status: 'HIT' };
    }

    const pending = this.inflight.get(key);
    if (pending) {
      this.hits++;
      return { image: await pending, status: 'COALESCED' };
    }

    this.misses++;
    const promise = create();
    this.inflight.set(key, promise);
    try {
      const image = await promise;
      this.store(key, image);
      return { image, status: 'MISS' };
    } finally {
      this.inflight.delete(key);
    }
  }

  private store(key: string, image: CachedImage): void {
    const size = image.png.length;
    if (size > this.maxBytes / 4) return; // too big to be worth evicting many others for
    this.entries.set(key, image);
    this.bytes += size;
    // Evict least recently used entries until we're back under budget.
    for (const [oldKey, old] of this.entries) {
      if (this.bytes <= this.maxBytes) break;
      this.entries.delete(oldKey);
      this.bytes -= old.png.length;
    }
  }

  stats() {
    return { entries: this.entries.size, bytes: this.bytes, maxBytes: this.maxBytes, hits: this.hits, misses: this.misses };
  }
}
