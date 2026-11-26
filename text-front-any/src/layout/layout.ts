import type { Font, GlyphInfo } from '../font/loadFont';

/** A glyph placed in pixel space. (x, y) is its origin: left edge of the advance, on the baseline. */
export interface PositionedGlyph {
  glyph: GlyphInfo;
  x: number;
  y: number;
}

export interface Layout {
  glyphs: PositionedGlyph[];
  /** Width of the widest line in px. */
  width: number;
  /** Height from the top of the first line to the bottom of the last, in px. */
  height: number;
  /** Pixels per font unit. */
  scale: number;
  lineHeight: number;
}

/**
 * Lay out `text` left to right at `size` px per em.
 * Uses each glyph's advance width plus pair kerning, and starts a new line on "\n".
 * Coordinates are relative to the text block's top-left corner, with y pointing down.
 */
export function layoutText(font: Font, text: string, size: number): Layout {
  const m = font.metrics;
  const scale = size / m.unitsPerEm;
  const ascent = m.ascender * scale;
  const descent = -m.descender * scale;
  const lineHeight = (m.ascender - m.descender + m.lineGap) * scale;

  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  const glyphs: PositionedGlyph[] = [];
  let width = 0;

  lines.forEach((line, lineIndex) => {
    const baseline = ascent + lineIndex * lineHeight;
    let penX = 0;
    let prev: GlyphInfo | null = null;
    // for...of walks code points, so characters outside the BMP (emoji etc.) stay whole.
    for (const ch of line) {
      const glyph = font.glyphFor(ch);
      if (prev) penX += font.kerning(prev, glyph) * scale;
      glyphs.push({ glyph, x: penX, y: baseline });
      penX += glyph.advanceWidth * scale;
      prev = glyph;
    }
    width = Math.max(width, penX);
  });

  const height = ascent + descent + (lines.length - 1) * lineHeight;
  return { glyphs, width, height, scale, lineHeight };
}
