import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import { basename, extname, join } from 'node:path';
import { GlyphError } from '../errors';
import { type Font, loadFont } from './loadFont';

/**
 * Fonts the service knows by name. Every font in the folder is parsed once at startup and kept
 * in memory, so requests never re-read or re-parse a font file. Names match case-insensitively.
 */
export class FontRegistry {
  private readonly fonts = new Map<string, { name: string; font: Font }>();

  /** Load every .ttf/.otf in `dir`. The name is the file name without its extension (e.g. "DejaVuSans"). */
  static fromDirectory(dir: string): FontRegistry {
    const reg = new FontRegistry();
    for (const file of readdirSync(dir)) {
      const ext = extname(file).toLowerCase();
      if (ext !== '.ttf' && ext !== '.otf') continue;
      reg.add(basename(file, extname(file)), loadFont(readFileSync(join(dir, file))));
    }
    return reg;
  }

  has(name: string): boolean {
    return this.fonts.has(name.toLowerCase());
  }

  add(name: string, font: Font): void {
    this.fonts.set(name.toLowerCase(), { name, font });
  }

  /** Look up a font by name. Throws GlyphError(404) listing the valid names. */
  get(name: string): Font {
    const entry = this.fonts.get(name.toLowerCase());
    if (!entry) {
      throw new GlyphError('font_not_found', 404, `unknown font "${name}"; available: ${this.names().join(', ')}`);
    }
    return entry.font;
  }

  names(): string[] {
    return [...this.fonts.values()].map((e) => e.name).sort();
  }
}

/** SHA-256 hex digest. Identifies an uploaded font by its content. */
export function hashBytes(bytes: Uint8Array): string {
  return createHash('sha256').update(bytes).digest('hex');
}

/**
 * A small LRU cache of uploaded fonts, keyed by a SHA-256 hash of the bytes. A team that
 * uploads the same font on every request then only pays the parse cost once.
 */
export class UploadedFontCache {
  // A Map keeps insertion order. Deleting and re-inserting a key moves it to the newest end.
  private readonly entries = new Map<string, Font>();

  constructor(private readonly maxEntries = 32) {}

  /** Return the parsed font for these bytes, parsing and caching it on a miss. Pass `hash` if already known. */
  get(bytes: Uint8Array, hash = hashBytes(bytes)): Font {
    const key = hash;
    let font = this.entries.get(key);
    if (font) {
      this.entries.delete(key);
    } else {
      font = loadFont(bytes); // throws for invalid fonts, which are therefore never cached
      if (this.entries.size >= this.maxEntries) {
        this.entries.delete(this.entries.keys().next().value!); // evict least recently used
      }
    }
    this.entries.set(key, font);
    return font;
  }

  get size(): number {
    return this.entries.size;
  }
}
