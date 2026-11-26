// Minimal typings for the parts of opentype.js 2.x we use (the package ships none).
// We only use it to PARSE fonts: outlines, advance widths, metrics and kerning.
declare module 'opentype.js' {
  export interface OtPathCommand {
    type: 'M' | 'L' | 'Q' | 'C' | 'Z';
    x?: number;
    y?: number;
    x1?: number;
    y1?: number;
    x2?: number;
    y2?: number;
  }

  export interface OtGlyph {
    index: number;
    advanceWidth?: number;
    path: { commands: OtPathCommand[] };
  }

  export interface OtFont {
    unitsPerEm: number;
    ascender: number;
    descender: number;
    /** Name table, grouped by platform in 2.x (e.g. names.windows.fontFamily.en). */
    names: Record<string, { fontFamily?: Record<string, string> } | undefined>;
    tables: { hhea?: { lineGap?: number }; [name: string]: unknown };
    /** Legacy pair table: "leftIndex,rightIndex" -> adjustment in font units. */
    kerningPairs?: Record<string, number>;
    charToGlyph(char: string): OtGlyph;
    getKerningValue(left: OtGlyph, right: OtGlyph): number;
  }

  const opentype: { parse(buffer: ArrayBuffer): OtFont };
  export default opentype;
}
