import { describe, expect, it } from 'vitest';
import { layoutText } from '../../src/layout/layout';
import { font } from '../helpers';

describe('layoutText', () => {
  const f = font();
  const scale = 48 / f.metrics.unitsPerEm;

  it('lays out nothing for empty text but keeps one line of height', () => {
    const l = layoutText(f, '', 48);
    expect(l.glyphs).toHaveLength(0);
    expect(l.width).toBe(0);
    expect(l.height).toBeCloseTo((f.metrics.ascender - f.metrics.descender) * scale);
  });

  it('places the first glyph at x=0 on the ascender baseline', () => {
    const [g] = layoutText(f, 'H', 48).glyphs;
    expect(g.x).toBe(0);
    expect(g.y).toBeCloseTo(f.metrics.ascender * scale);
  });

  it('advances each glyph by its advance width (no kerning pair)', () => {
    const l = layoutText(f, 'HH', 48);
    const adv = f.glyphFor('H').advanceWidth * scale;
    expect(l.glyphs[1].x).toBeCloseTo(adv);
    expect(l.width).toBeCloseTo(2 * adv);
  });

  it('applies kerning between a kerned pair', () => {
    const l = layoutText(f, 'AV', 48);
    const plain = f.glyphFor('A').advanceWidth * scale;
    expect(l.glyphs[1].x).toBeLessThan(plain);
  });

  it('handles characters outside the BMP as one glyph', () => {
    expect(layoutText(f, 'a\u{1F600}b', 48).glyphs).toHaveLength(3);
  });

  it.each(['a\nb', 'a\r\nb', 'a\rb'])('starts a new line on %j', (text) => {
    const l = layoutText(f, text, 48);
    expect(l.glyphs).toHaveLength(2);
    expect(l.glyphs[1].x).toBe(0);
    expect(l.glyphs[1].y - l.glyphs[0].y).toBeCloseTo(l.lineHeight);
  });

  it('uses the widest line as the width and grows height per line', () => {
    const one = layoutText(f, 'WWWW', 48);
    const two = layoutText(f, 'WWWW\nW', 48);
    expect(two.width).toBeCloseTo(one.width);
    expect(two.height - one.height).toBeCloseTo(two.lineHeight);
  });

  it.each([12, 24, 96])('scales width linearly with size (%i px)', (size) => {
    const base = layoutText(f, 'Hello', 48).width;
    expect(layoutText(f, 'Hello', size).width).toBeCloseTo((base * size) / 48);
  });
});
