import type { RGBA } from './canvas/canvas';
import { GlyphError } from './errors';
import { LIMITS } from './limits';

/** Fully validated render settings. */
export interface RenderOptions {
  text: string;
  /** Font size in px per em. */
  size: number;
  color: RGBA;
  background: RGBA;
  /** Empty margin around the text, in px. */
  padding: number;
}

export const DEFAULTS = {
  size: 48,
  color: '#000000',
  background: '#ffffff',
  padding: 10,
} as const;

const bad = (message: string) => new GlyphError('invalid_request', 400, message);

/**
 * Parse a color: "#rgb", "#rrggbb" or "#rrggbbaa" (the "#" is optional), or "transparent".
 * Throws GlyphError(400) for anything else.
 */
export function parseColor(value: unknown, field: string): RGBA {
  if (typeof value !== 'string') throw bad(`${field} must be a hex color string`);
  if (value.toLowerCase() === 'transparent') return [0, 0, 0, 0];
  let hex = value.startsWith('#') ? value.slice(1) : value;
  if (!/^([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(hex)) {
    throw bad(`${field} must look like #rgb, #rrggbb or #rrggbbaa`);
  }
  if (hex.length === 3) hex = [...hex].map((c) => c + c).join('');
  if (hex.length === 6) hex += 'ff';
  return [0, 2, 4, 6].map((i) => parseInt(hex.slice(i, i + 2), 16)) as RGBA;
}

/** Read a number that may arrive as a string (e.g. from multipart form fields). */
function parseNumber(value: unknown, field: string, min: number, max: number, integer: boolean): number {
  const n = typeof value === 'string' && value.trim() !== '' ? Number(value) : value;
  if (typeof n !== 'number' || !Number.isFinite(n)) throw bad(`${field} must be a number`);
  if (integer && !Number.isInteger(n)) throw bad(`${field} must be an integer`);
  if (n < min || n > max) throw bad(`${field} must be between ${min} and ${max}`);
  return n;
}

/**
 * Validate raw input (a JSON body or form fields) into RenderOptions, filling in defaults.
 * Throws GlyphError: 400 for bad values, 413 when the text is too long.
 */
export function parseRenderOptions(input: Record<string, unknown>): RenderOptions {
  const { text } = input;
  if (typeof text !== 'string' || text.length === 0) throw bad('text is required and must be a non-empty string');
  if (text.length > LIMITS.maxTextLength) {
    throw new GlyphError('text_too_long', 413, `text must be at most ${LIMITS.maxTextLength} characters`);
  }
  return {
    text,
    size: parseNumber(input.size ?? DEFAULTS.size, 'size', LIMITS.minSize, LIMITS.maxSize, false),
    color: parseColor(input.color ?? DEFAULTS.color, 'color'),
    background: parseColor(input.background ?? DEFAULTS.background, 'background'),
    padding: parseNumber(input.padding ?? DEFAULTS.padding, 'padding', 0, LIMITS.maxPadding, true),
  };
}
