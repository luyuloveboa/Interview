import { describe, expect, it } from 'vitest';
import { loadFont } from '../../src/font/loadFont';
import { GlyphError } from '../../src/errors';
import { font } from '../helpers';

describe('loadFont', () => {
  it.each(['DejaVuSans', 'DejaVuSansMono', 'DejaVuSerif'])('parses %s with sane metrics', (name) => {
    const m = font(name).metrics;
    expect(m.unitsPerEm).toBe(2048);
    expect(m.ascender).toBeGreaterThan(0);
    expect(m.descender).toBeLessThan(0);
  });

  it('reads the family name', () => {
    expect(font('DejaVuSans').familyName).toBe('DejaVu Sans');
  });

  it.each(['A', 'g', '0', '@'])('gives %s a positive advance and an outline', (ch) => {
    const g = font().glyphFor(ch);
    expect(g.index).toBeGreaterThan(0);
    expect(g.advanceWidth).toBeGreaterThan(0);
    expect(g.commands.length).toBeGreaterThan(0);
  });

  it('gives space an advance but no outline', () => {
    const g = font().glyphFor(' ');
    expect(g.advanceWidth).toBeGreaterThan(0);
    expect(g.commands).toHaveLength(0);
  });

  it('falls back to .notdef (glyph 0) for a character the font lacks', () => {
    expect(font().glyphFor('\u{10FFFD}').index).toBe(0);
  });

  it('gives every monospace glyph the same advance', () => {
    const f = font('DejaVuSansMono');
    expect(f.glyphFor('i').advanceWidth).toBe(f.glyphFor('W').advanceWidth);
  });

  it.each([
    ['DejaVuSerif', 'A', 'V'], // GPOS kerning
    ['DejaVuSans', 'A', 'V'], // only in the pair table
    ['DejaVuSans', 'T', 'o'],
  ])('%s kerns %s%s negatively', (name, l, r) => {
    const f = font(name);
    expect(f.kerning(f.glyphFor(l), f.glyphFor(r))).toBeLessThan(0);
  });

  it('does not kern a pair with no entry', () => {
    const f = font();
    expect(f.kerning(f.glyphFor('o'), f.glyphFor('o'))).toBe(0);
  });

  it.each([
    ['empty bytes', new Uint8Array(0)],
    ['garbage bytes', new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])],
    ['text', new TextEncoder().encode('definitely not a font file')],
  ])('rejects %s with invalid_font', (_label, bytes) => {
    let err: unknown;
    try {
      loadFont(bytes);
    } catch (e) {
      err = e;
    }
    expect(err).toBeInstanceOf(GlyphError);
    expect((err as GlyphError).code).toBe('invalid_font');
    expect((err as GlyphError).status).toBe(422);
  });
});
