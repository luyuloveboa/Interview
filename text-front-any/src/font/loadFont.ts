import opentype, { type OtFont, type OtGlyph } from 'opentype.js';
import { GlyphError } from '../errors';

/** One outline command in font units (y points UP, as in the font file). */
export interface PathCommand {
  type: 'M' | 'L' | 'Q' | 'C' | 'Z';
  x?: number;
  y?: number;
  x1?: number;
  y1?: number;
  x2?: number;
  y2?: number;
}

/** What layout and rasterizing need to know about one glyph. */
export interface GlyphInfo {
  index: number;
  /** Horizontal distance to the next glyph's origin, in font units. */
  advanceWidth: number;
  commands: PathCommand[];
}

/** Vertical font metrics, in font units. `descender` is negative. */
export interface FontMetrics {
  unitsPerEm: number;
  ascender: number;
  descender: number;
  lineGap: number;
}

/** A parsed font. Wraps opentype.js so the rest of the code doesn't depend on it. */
export class Font {
  readonly metrics: FontMetrics;
  readonly familyName: string;
  // Glyph lookups are cached per character, because the same font renders many requests.
  private readonly glyphCache = new Map<string, GlyphInfo>();
  private readonly otGlyphs = new Map<number, OtGlyph>();

  constructor(private readonly ot: OtFont) {
    this.metrics = {
      unitsPerEm: ot.unitsPerEm,
      ascender: ot.ascender,
      descender: ot.descender,
      lineGap: ot.tables.hhea?.lineGap ?? 0,
    };
    const family = ot.names.windows?.fontFamily ?? ot.names.macintosh?.fontFamily ?? ot.names.unicode?.fontFamily;
    this.familyName = family?.en ?? Object.values(family ?? {})[0] ?? 'unknown';
  }

  /** Glyph for one character. A character the font lacks maps to glyph 0 (.notdef, usually a box). */
  glyphFor(char: string): GlyphInfo {
    let info = this.glyphCache.get(char);
    if (!info) {
      const g = this.ot.charToGlyph(char);
      info = { index: g.index, advanceWidth: g.advanceWidth ?? 0, commands: g.path.commands };
      this.glyphCache.set(char, info);
      this.otGlyphs.set(g.index, g);
    }
    return info;
  }

  /** Kerning adjustment between two glyphs, in font units (usually negative or 0). */
  kerning(left: GlyphInfo, right: GlyphInfo): number {
    const l = this.otGlyphs.get(left.index);
    const r = this.otGlyphs.get(right.index);
    if (!l || !r) return 0;
    const fromGpos = this.ot.getKerningValue(l, r);
    if (fromGpos) return fromGpos;
    // Some fonts (e.g. DejaVuSans) have pairs opentype.js's GPOS lookup misses; use the pair table.
    return this.ot.kerningPairs?.[`${left.index},${right.index}`] ?? 0;
  }
}

/** Parse raw TTF/OTF bytes. Throws GlyphError('invalid_font') if the bytes aren't a usable font. */
export function loadFont(bytes: Uint8Array): Font {
  let ot: OtFont;
  try {
    // opentype.js wants an ArrayBuffer that covers exactly these bytes.
    const ab = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    ot = opentype.parse(ab);
  } catch (err) {
    throw new GlyphError('invalid_font', 422, `Could not parse font: ${(err as Error).message}`);
  }
  if (!(ot.unitsPerEm > 0)) {
    throw new GlyphError('invalid_font', 422, 'Font has no valid unitsPerEm');
  }
  return new Font(ot);
}
