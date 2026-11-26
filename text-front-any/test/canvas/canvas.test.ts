import { describe, expect, it } from 'vitest';
import { Canvas, type RGBA } from '../../src/canvas/canvas';
import { createMask } from '../../src/raster/rasterize';

function maskOf(values: number[]) {
  const m = createMask(values.length, 1);
  m.data.set(values);
  return m;
}

describe('Canvas', () => {
  it('fills with the background color', () => {
    const c = new Canvas(3, 2, [10, 20, 30, 255]);
    expect(c.pixel(2, 1)).toEqual([10, 20, 30, 255]);
  });

  it.each<[number, RGBA]>([
    [0, [255, 255, 255, 255]],
    [1, [0, 0, 0, 255]],
    [0.5, [128, 128, 128, 255]],
    [2, [0, 0, 0, 255]], // coverage above 1 is clamped
  ])('blends black on white at coverage %d', (cov, expected) => {
    const c = new Canvas(1, 1, [255, 255, 255, 255]);
    c.blendMask(maskOf([cov]), [0, 0, 0, 255]);
    expect(c.pixel(0, 0)).toEqual(expected);
  });

  it('keeps the text color on a transparent background and uses coverage as alpha', () => {
    const c = new Canvas(2, 1, [0, 0, 0, 0]);
    c.blendMask(maskOf([1, 0.5]), [255, 0, 0, 255]);
    expect(c.pixel(0, 0)).toEqual([255, 0, 0, 255]);
    expect(c.pixel(1, 0)).toEqual([255, 0, 0, 128]);
  });

  it('respects the text color alpha', () => {
    const c = new Canvas(1, 1, [255, 255, 255, 255]);
    c.blendMask(maskOf([1]), [0, 0, 0, 0]);
    expect(c.pixel(0, 0)).toEqual([255, 255, 255, 255]);
  });

  it('rejects a mask of a different size', () => {
    expect(() => new Canvas(2, 2).blendMask(createMask(3, 3), [0, 0, 0, 255])).toThrow();
  });
});
