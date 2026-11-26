import { describe, expect, it } from 'vitest';
import { encodePng } from '../../src/encode/png';
import { decodePng } from '../helpers';

describe('encodePng', () => {
  it('writes the PNG signature and IHDR dimensions', () => {
    const png = encodePng(7, 3, new Uint8Array(7 * 3 * 4));
    expect([...png.subarray(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    expect(png.subarray(12, 16).toString('ascii')).toBe('IHDR');
    expect(png.readUInt32BE(16)).toBe(7);
    expect(png.readUInt32BE(20)).toBe(3);
  });

  it.each([
    [1, 1],
    [5, 4],
    [64, 17],
  ])('round-trips %ix%i pixels through an independent decoder', (w, h) => {
    const rgba = new Uint8Array(w * h * 4).map((_, i) => (i * 37) % 256);
    const decoded = decodePng(encodePng(w, h, rgba));
    expect(decoded.width).toBe(w);
    expect(decoded.height).toBe(h);
    expect([...decoded.data]).toEqual([...rgba]);
  });

  it('rejects a buffer that does not match the dimensions', () => {
    expect(() => encodePng(2, 2, new Uint8Array(3))).toThrow();
  });
});
