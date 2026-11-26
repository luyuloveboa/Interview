import { describe, expect, it } from 'vitest';
import { GlyphError } from '../src/errors';
import { parseRenderOptions } from '../src/options';
import { renderText } from '../src/render';
import { decodePng, font } from './helpers';

/** Count pixels darker than mid-grey, optionally only inside a rectangle. */
function darkPixels(img: ReturnType<typeof decodePng>, x0 = 0, y0 = 0, x1 = img.width, y1 = img.height) {
  let n = 0;
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) if (img.data[(y * img.width + x) * 4] < 128) n++;
  }
  return n;
}

describe('renderText', () => {
  it('renders "Hello" into a valid PNG with ink inside and blank padding', () => {
    const r = renderText(font(), parseRenderOptions({ text: 'Hello', padding: 10 }));
    const img = decodePng(r.png);
    expect(img.width).toBe(r.width);
    expect(img.height).toBe(r.height);
    expect(darkPixels(img)).toBeGreaterThan(100);
    // Nothing inside the top and bottom padding strips.
    expect(darkPixels(img, 0, 0, img.width, 10)).toBe(0);
    expect(darkPixels(img, 0, img.height - 10, img.width, img.height)).toBe(0);
  });

  it('has antialiased (in-between) pixels on glyph edges', () => {
    const img = decodePng(renderText(font(), parseRenderOptions({ text: 'O' })).png);
    const greys = new Set<number>();
    for (let i = 0; i < img.data.length; i += 4) greys.add(img.data[i]);
    expect(greys.size).toBeGreaterThan(10);
  });

  it('draws in the requested color', () => {
    const img = decodePng(renderText(font(), parseRenderOptions({ text: 'I', size: 100, color: '#ff0000' })).png);
    // Sample the middle of the stem of 'I'.
    const cx = Math.floor(img.width / 2);
    const cy = Math.floor(img.height / 2);
    const i = (cy * img.width + cx) * 4;
    expect([...img.data.subarray(i, i + 4)]).toEqual([255, 0, 0, 255]);
  });

  it('renders only spaces as a blank image of the right width', () => {
    const r = renderText(font(), parseRenderOptions({ text: '   ', padding: 0 }));
    expect(r.width).toBeGreaterThan(10);
    expect(darkPixels(decodePng(r.png))).toBe(0);
  });

  it('makes multi-line text taller', () => {
    const one = renderText(font(), parseRenderOptions({ text: 'ab' }));
    const two = renderText(font(), parseRenderOptions({ text: 'ab\ncd' }));
    expect(two.height).toBeGreaterThan(one.height);
  });

  it.each(['DejaVuSans', 'DejaVuSansMono', 'DejaVuSerif'])('renders with %s', (name) => {
    const r = renderText(font(name), parseRenderOptions({ text: 'Glyph 123' }));
    expect(darkPixels(decodePng(r.png))).toBeGreaterThan(50);
  });

  it('rejects output larger than the image limit with 413', () => {
    let err: unknown;
    try {
      renderText(font(), parseRenderOptions({ text: 'W'.repeat(500), size: 200 }));
    } catch (e) {
      err = e;
    }
    expect(err).toBeInstanceOf(GlyphError);
    expect((err as GlyphError).status).toBe(413);
  });
});
