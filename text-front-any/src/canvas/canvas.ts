import type { Mask } from '../raster/rasterize';

/** A color as [r, g, b, a], each 0-255. */
export type RGBA = [number, number, number, number];

/** An RGBA8 pixel buffer (non-premultiplied, row-major, 4 bytes per pixel). */
export class Canvas {
  readonly data: Uint8Array;

  constructor(
    readonly width: number,
    readonly height: number,
    background: RGBA = [255, 255, 255, 255],
  ) {
    this.data = new Uint8Array(width * height * 4);
    this.fill(background);
  }

  /** Set every pixel to `color`. */
  fill(color: RGBA): void {
    for (let i = 0; i < this.data.length; i += 4) this.data.set(color, i);
  }

  /** Read one pixel (for tests and debugging). */
  pixel(x: number, y: number): RGBA {
    const i = (y * this.width + x) * 4;
    return [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]];
  }

  /**
   * Paint `color` through a coverage mask the same size as the canvas, using source-over blending.
   * Coverage 0 leaves the pixel alone, 1 paints the full color, and values in between blend.
   * That in-between blending is what makes edges antialiased.
   */
  blendMask(mask: Mask, color: RGBA): void {
    if (mask.width !== this.width || mask.height !== this.height) {
      throw new Error('mask and canvas size differ');
    }
    const [cr, cg, cb, ca] = color;
    const d = this.data;
    for (let p = 0; p < mask.data.length; p++) {
      const cov = mask.data[p];
      if (cov <= 0) continue;
      const sa = (Math.min(1, cov) * ca) / 255; // source alpha 0..1
      if (sa === 0) continue;
      const i = p * 4;
      const da = d[i + 3] / 255;
      const outA = sa + da * (1 - sa);
      // Non-premultiplied source-over: weight each color by its alpha, then divide by the result alpha.
      d[i] = Math.round((cr * sa + d[i] * da * (1 - sa)) / outA);
      d[i + 1] = Math.round((cg * sa + d[i + 1] * da * (1 - sa)) / outA);
      d[i + 2] = Math.round((cb * sa + d[i + 2] * da * (1 - sa)) / outA);
      d[i + 3] = Math.round(outA * 255);
    }
  }
}
