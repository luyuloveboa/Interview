import { describe, expect, it } from 'vitest';
import type { PathCommand } from '../../src/font/loadFont';
import { flattenPath } from '../../src/raster/flatten';
import { font } from '../helpers';

const square: PathCommand[] = [
  { type: 'M', x: 0, y: 0 },
  { type: 'L', x: 10, y: 0 },
  { type: 'L', x: 10, y: 10 },
  { type: 'L', x: 0, y: 10 },
  { type: 'Z' },
];

describe('flattenPath', () => {
  it('turns a closed square into 4 segments', () => {
    expect(flattenPath(square, 1, 0, 0)).toHaveLength(4);
  });

  it('closes a contour that is missing Z', () => {
    const segs = flattenPath(square.slice(0, 4), 1, 0, 0);
    expect(segs).toHaveLength(4);
    expect(segs[3]).toEqual({ x0: 0, y0: -10, x1: 0, y1: 0 });
  });

  it('scales, flips y and offsets to the origin', () => {
    const [first] = flattenPath(square, 2, 100, 50);
    // (0,0) -> (100, 50); (10,0) -> (120, 50)
    expect(first).toEqual({ x0: 100, y0: 50, x1: 120, y1: 50 });
    const up = flattenPath(square, 2, 100, 50)[1];
    // font y=10 (up) becomes screen y=50-20=30 (higher on screen)
    expect(up.y1).toBe(30);
  });

  it('skips zero-length segments', () => {
    const cmds: PathCommand[] = [{ type: 'M', x: 0, y: 0 }, { type: 'L', x: 0, y: 0 }, ...square.slice(1)];
    expect(flattenPath(cmds, 1, 0, 0)).toHaveLength(4);
  });

  it.each<[string, PathCommand]>([
    ['quadratic', { type: 'Q', x1: 50, y1: 100, x: 100, y: 0 }],
    ['cubic', { type: 'C', x1: 0, y1: 100, x2: 100, y2: 100, x: 100, y: 0 }],
  ])('splits a %s curve into connected pieces ending on its endpoint', (_name, curve) => {
    const segs = flattenPath([{ type: 'M', x: 0, y: 0 }, curve], 1, 0, 0);
    expect(segs.length).toBeGreaterThan(3);
    for (let i = 1; i < segs.length; i++) {
      expect(segs[i].x0).toBe(segs[i - 1].x1);
      expect(segs[i].y0).toBe(segs[i - 1].y1);
    }
    // The last curve piece lands on (100, 0); then the closing edge returns to the start.
    const end = segs[segs.length - 2];
    expect(end.x1).toBeCloseTo(100);
    expect(end.y1).toBeCloseTo(0);
  });

  it('uses more pieces for a bigger curve', () => {
    const q: PathCommand[] = [{ type: 'M', x: 0, y: 0 }, { type: 'Q', x1: 50, y1: 100, x: 100, y: 0 }];
    expect(flattenPath(q, 10, 0, 0).length).toBeGreaterThan(flattenPath(q, 0.1, 0, 0).length);
  });

  it('produces only closed contours for a real glyph', () => {
    const f = font();
    const segs = flattenPath(f.glyphFor('o').commands, 0.05, 0, 100);
    // In a closed outline, each point is an end of exactly as many edges as it is a start of.
    const key = (x: number, y: number) => `${x.toFixed(6)},${y.toFixed(6)}`;
    const balance = new Map<string, number>();
    for (const s of segs) {
      balance.set(key(s.x0, s.y0), (balance.get(key(s.x0, s.y0)) ?? 0) + 1);
      balance.set(key(s.x1, s.y1), (balance.get(key(s.x1, s.y1)) ?? 0) - 1);
    }
    expect([...balance.values()].every((v) => v === 0)).toBe(true);
  });
});
