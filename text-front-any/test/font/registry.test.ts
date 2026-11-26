import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { GlyphError } from '../../src/errors';
import { FontRegistry, UploadedFontCache } from '../../src/font/registry';

const FONTS_DIR = new URL('../../fonts/', import.meta.url);
const fontBytes = (name: string) => readFileSync(new URL(`${name}.ttf`, FONTS_DIR));

describe('FontRegistry', () => {
  const reg = FontRegistry.fromDirectory(fileURLToPath(FONTS_DIR));

  it('loads every TTF in the folder (and skips LICENSE.txt)', () => {
    expect(reg.names()).toEqual(['DejaVuSans', 'DejaVuSansMono', 'DejaVuSerif']);
  });

  it.each(['DejaVuSans', 'dejavusans', 'DEJAVUSERIF'])('finds %s case-insensitively', (name) => {
    expect(reg.get(name).metrics.unitsPerEm).toBe(2048);
  });

  it('returns the same parsed instance each time (no re-parse)', () => {
    expect(reg.get('DejaVuSans')).toBe(reg.get('DejaVuSans'));
  });

  it('throws font_not_found (404) for unknown names and lists the valid ones', () => {
    try {
      reg.get('ComicSans');
      expect.unreachable();
    } catch (e) {
      expect(e).toBeInstanceOf(GlyphError);
      expect((e as GlyphError).status).toBe(404);
      expect((e as GlyphError).message).toContain('DejaVuSans');
    }
  });
});

describe('UploadedFontCache', () => {
  it('parses once and reuses the result for identical bytes', () => {
    const cache = new UploadedFontCache();
    const a = cache.get(fontBytes('DejaVuSans'));
    const b = cache.get(fontBytes('DejaVuSans'));
    expect(a).toBe(b);
    expect(cache.size).toBe(1);
  });

  it('evicts the least recently used font when full', () => {
    const cache = new UploadedFontCache(2);
    const sans = cache.get(fontBytes('DejaVuSans'));
    cache.get(fontBytes('DejaVuSerif'));
    cache.get(fontBytes('DejaVuSans')); // touch: Sans is now most recent
    cache.get(fontBytes('DejaVuSansMono')); // evicts Serif
    expect(cache.size).toBe(2);
    expect(cache.get(fontBytes('DejaVuSans'))).toBe(sans);
  });

  it('does not cache invalid fonts', () => {
    const cache = new UploadedFontCache();
    expect(() => cache.get(new Uint8Array([1, 2, 3]))).toThrow(GlyphError);
    expect(cache.size).toBe(0);
  });
});
