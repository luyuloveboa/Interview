import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { type Font, loadFont } from '../src/font/loadFont';
import type { Segment } from '../src/raster/flatten';

const FONTS_DIR = fileURLToPath(new URL('../fonts/', import.meta.url));
const cache = new Map<string, Font>();

/** Load a font from fonts/ by name (cached across tests). */
export function font(name = 'DejaVuSans'): Font {
  let f = cache.get(name);
  if (!f) {
    f = loadFont(readFileSync(join(FONTS_DIR, `${name}.ttf`)));
    cache.set(name, f);
  }
  return f;
}

/** Closed axis-aligned rectangle as segments. `clockwise` (on screen) gives the opposite winding. */
export function rect(x0: number, y0: number, x1: number, y1: number, clockwise = true): Segment[] {
  const pts = clockwise
    ? [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]
    : [[x0, y0], [x0, y1], [x1, y1], [x1, y0]];
  return pts.map(([x, y], i) => {
    const [nx, ny] = pts[(i + 1) % pts.length];
    return { x0: x, y0: y, x1: nx, y1: ny };
  });
}

/** Decode a PNG with an independent decoder (pngjs), so we're not only checking our encoder against itself. */
export function decodePng(buf: Buffer) {
  return PNG.sync.read(buf);
}
