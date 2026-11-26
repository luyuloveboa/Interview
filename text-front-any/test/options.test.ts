import { describe, expect, it } from 'vitest';
import { GlyphError } from '../src/errors';
import { LIMITS } from '../src/limits';
import { parseColor, parseRenderOptions } from '../src/options';

function errorOf(fn: () => unknown): GlyphError {
  try {
    fn();
  } catch (e) {
    if (e instanceof GlyphError) return e;
    throw e;
  }
  throw new Error('expected a GlyphError');
}

describe('parseColor', () => {
  it.each([
    ['#000', [0, 0, 0, 255]],
    ['#ff0000', [255, 0, 0, 255]],
    ['00ff00', [0, 255, 0, 255]],
    ['#0000ff80', [0, 0, 255, 128]],
    ['#ABCDEF', [171, 205, 239, 255]],
    ['transparent', [0, 0, 0, 0]],
  ])('parses %s', (input, expected) => {
    expect(parseColor(input, 'color')).toEqual(expected);
  });

  it.each(['red', '#12', '#12345', '#gggggg', 42, null])('rejects %j', (input) => {
    expect(errorOf(() => parseColor(input, 'color')).status).toBe(400);
  });
});

describe('parseRenderOptions', () => {
  it('fills in defaults', () => {
    expect(parseRenderOptions({ text: 'hi' })).toEqual({
      text: 'hi',
      size: 48,
      color: [0, 0, 0, 255],
      background: [255, 255, 255, 255],
      padding: 10,
    });
  });

  it('accepts numbers sent as strings (multipart form fields)', () => {
    const o = parseRenderOptions({ text: 'hi', size: '24', padding: '0' });
    expect(o.size).toBe(24);
    expect(o.padding).toBe(0);
  });

  it.each<[string, Record<string, unknown>]>([
    ['missing text', {}],
    ['empty text', { text: '' }],
    ['non-string text', { text: 5 }],
    ['size too small', { text: 'a', size: LIMITS.minSize - 1 }],
    ['size too big', { text: 'a', size: LIMITS.maxSize + 1 }],
    ['size not a number', { text: 'a', size: 'big' }],
    ['size NaN', { text: 'a', size: Number.NaN }],
    ['negative padding', { text: 'a', padding: -1 }],
    ['fractional padding', { text: 'a', padding: 1.5 }],
    ['padding too big', { text: 'a', padding: LIMITS.maxPadding + 1 }],
    ['bad color', { text: 'a', color: 'blue' }],
  ])('rejects %s with 400', (_label, input) => {
    const err = errorOf(() => parseRenderOptions(input));
    expect(err.status).toBe(400);
    expect(err.code).toBe('invalid_request');
  });

  it('rejects text over the length limit with 413', () => {
    const err = errorOf(() => parseRenderOptions({ text: 'a'.repeat(LIMITS.maxTextLength + 1) }));
    expect(err.status).toBe(413);
    expect(err.code).toBe('text_too_long');
  });
});
