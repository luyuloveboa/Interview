import type { Segment } from './flatten';

/** Per-pixel coverage in [0, 1] (values above 1 can build up where glyphs overlap; clamp on use). */
export interface Mask {
  width: number;
  height: number;
  data: Float32Array;
}

/** Sub-scanlines sampled per pixel row. More means smoother vertical antialiasing but more work. */
export const SUBSAMPLES = 4;

export function createMask(width: number, height: number): Mask {
  return { width, height, data: new Float32Array(width * height) };
}

/** Fill a closed outline into a new mask. Convenience wrapper around rasterizeInto. */
export function rasterize(segments: Segment[], width: number, height: number): Mask {
  const mask = createMask(width, height);
  rasterizeInto(segments, mask);
  return mask;
}

/**
 * Scanline-fill the outline made of `segments` and ADD its coverage into `mask`.
 *
 * Antialiasing: each pixel row is sampled at SUBSAMPLES evenly spaced horizontal lines.
 * On each line we find where the edges cross it, then fill the inside spans with exact
 * fractional coverage at the left and right ends. Inside means nonzero winding: edges
 * going down count +1, edges going up count -1, and a point is inside when the running
 * sum is not 0. That is the TrueType fill rule, so holes like the inside of 'o' stay empty.
 * Anything outside the mask is clipped.
 */
export function rasterizeInto(segments: Segment[], mask: Mask): void {
  if (segments.length === 0) return;
  const { width, height, data } = mask;

  // Only visit rows the outline actually touches.
  let minY = Infinity, maxY = -Infinity;
  for (const s of segments) {
    minY = Math.min(minY, s.y0, s.y1);
    maxY = Math.max(maxY, s.y0, s.y1);
  }
  const rowStart = Math.max(0, Math.floor(minY));
  const rowEnd = Math.min(height, Math.ceil(maxY));
  const weight = 1 / SUBSAMPLES;
  const xs: number[] = [];
  const dirs: number[] = [];
  const order: number[] = [];

  for (let row = rowStart; row < rowEnd; row++) {
    const rowOffset = row * width;
    for (let sub = 0; sub < SUBSAMPLES; sub++) {
      const y = row + (sub + 0.5) / SUBSAMPLES;
      xs.length = dirs.length = order.length = 0;

      for (const s of segments) {
        // Half-open test [top, bottom) so a shared vertex isn't counted twice.
        const down = s.y1 > s.y0;
        const top = down ? s.y0 : s.y1;
        const bottom = down ? s.y1 : s.y0;
        if (y < top || y >= bottom) continue;
        xs.push(s.x0 + ((y - s.y0) * (s.x1 - s.x0)) / (s.y1 - s.y0));
        dirs.push(down ? 1 : -1);
        order.push(order.length);
      }
      if (xs.length < 2) continue;
      order.sort((a, b) => xs[a] - xs[b]);

      // Walk crossings left to right and fill wherever the winding number is nonzero.
      let winding = 0;
      let prevX = 0;
      for (const i of order) {
        if (winding !== 0) addSpan(data, rowOffset, width, prevX, xs[i], weight);
        winding += dirs[i];
        prevX = xs[i];
      }
    }
  }
}

/** Add `weight` times the horizontal coverage of [xa, xb) to one mask row, clipped to [0, width). */
function addSpan(data: Float32Array, rowOffset: number, width: number, xa: number, xb: number, weight: number) {
  xa = Math.max(0, xa);
  xb = Math.min(width, xb);
  if (xb <= xa) return;
  const ia = Math.floor(xa);
  const ib = Math.floor(xb);
  if (ia === ib) {
    data[rowOffset + ia] += (xb - xa) * weight;
    return;
  }
  data[rowOffset + ia] += (ia + 1 - xa) * weight; // partial left pixel
  for (let i = ia + 1; i < ib; i++) data[rowOffset + i] += weight; // fully covered middle
  if (ib < width) data[rowOffset + ib] += (xb - ib) * weight; // partial right pixel
}
