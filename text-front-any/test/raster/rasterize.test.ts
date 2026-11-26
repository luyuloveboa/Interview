import { describe, expect, it } from 'vitest';
import { createMask, rasterize, rasterizeInto } from '../../src/raster/rasterize';
import { rect } from '../helpers';

const at = (m: ReturnType<typeof rasterize>, x: number, y: number) => m.data[y * m.width + x];

describe('rasterize', () => {
  it('fills a pixel-aligned square fully inside and leaves outside empty', () => {
    const m = rasterize(rect(2, 2, 6, 6), 8, 8);
    expect(at(m, 2, 2)).toBeCloseTo(1);
    expect(at(m, 5, 5)).toBeCloseTo(1);
    expect(at(m, 1, 2)).toBe(0);
    expect(at(m, 6, 6)).toBe(0);
    // Total coverage equals the area.
    expect(m.data.reduce((a, b) => a + b, 0)).toBeCloseTo(16);
  });

  it('gives partial coverage to half-covered edge pixels (antialiasing)', () => {
    const m = rasterize(rect(1.5, 0, 3, 4), 4, 4);
    expect(at(m, 1, 1)).toBeCloseTo(0.5);
    expect(at(m, 2, 1)).toBeCloseTo(1);
  });

  it('gives partial coverage vertically via sub-scanlines', () => {
    const m = rasterize(rect(0, 0.5, 4, 4), 4, 4);
    expect(at(m, 0, 0)).toBeCloseTo(0.5);
  });

  it.each([true, false])('fills the same regardless of contour direction (clockwise=%s)', (cw) => {
    const m = rasterize(rect(1, 1, 3, 3, cw), 4, 4);
    expect(at(m, 1, 1)).toBeCloseTo(1);
  });

  it('leaves a hole empty when the inner contour winds the other way', () => {
    const segs = [...rect(0, 0, 9, 9, true), ...rect(3, 3, 6, 6, false)];
    const m = rasterize(segs, 9, 9);
    expect(at(m, 1, 1)).toBeCloseTo(1);
    expect(at(m, 4, 4)).toBe(0);
  });

  it('nonzero rule: two overlapping same-direction squares do not cancel', () => {
    const segs = [...rect(0, 0, 4, 4), ...rect(2, 2, 6, 6)];
    const m = rasterize(segs, 6, 6);
    expect(at(m, 3, 3)).toBeCloseTo(1); // overlap stays filled, not doubled
  });

  it('clips shapes that go outside the mask without crashing', () => {
    const m = rasterize(rect(-10, -10, 2, 2), 4, 4);
    expect(at(m, 0, 0)).toBeCloseTo(1);
    expect(at(m, 3, 3)).toBe(0);
    expect(() => rasterize(rect(100, 100, 200, 200), 4, 4)).not.toThrow();
  });

  it('does nothing for an empty outline', () => {
    const m = rasterize([], 4, 4);
    expect(m.data.every((v) => v === 0)).toBe(true);
  });

  it('rasterizeInto adds coverage onto an existing mask', () => {
    const m = createMask(4, 1);
    rasterizeInto(rect(0, 0, 2, 1), m);
    rasterizeInto(rect(2, 0, 4, 1), m);
    expect([...m.data].map((v) => +v.toFixed(3))).toEqual([1, 1, 1, 1]);
  });
});
