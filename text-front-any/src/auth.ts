import { createHash } from 'node:crypto';
import type { IncomingHttpHeaders } from 'node:http';

/** Keys shorter than this are rejected at startup, because they are too easy to guess. */
export const MIN_KEY_LENGTH = 16;

const sha256 = (s: string) => createHash('sha256').update(s).digest('hex');

/**
 * API keys for internal teams. Each key maps to a client name, which is used for
 * per-client rate limits and logs.
 *
 * Only SHA-256 hashes of the keys are stored, and lookups go by hash. That keeps raw keys
 * out of memory dumps and avoids timing leaks from comparing secrets character by character.
 */
export class ApiKeyStore {
  private readonly byHash = new Map<string, string>();

  /**
   * Parse "name:key,name2:key2" (e.g. from the GLYPH_API_KEYS env var).
   * Throws on malformed entries, duplicate names, or keys shorter than MIN_KEY_LENGTH.
   */
  static parse(spec: string): ApiKeyStore {
    const store = new ApiKeyStore();
    const names = new Set<string>();
    for (const entry of spec.split(',').map((s) => s.trim()).filter(Boolean)) {
      const sep = entry.indexOf(':');
      const name = entry.slice(0, sep).trim();
      const key = entry.slice(sep + 1).trim();
      if (sep <= 0 || !name || !key) throw new Error(`API key entry must be "name:key" (got "${entry.split(':')[0]}:...")`);
      if (key.length < MIN_KEY_LENGTH) throw new Error(`API key for "${name}" must be at least ${MIN_KEY_LENGTH} characters`);
      if (names.has(name)) throw new Error(`duplicate API key name "${name}"`);
      names.add(name);
      store.add(name, key);
    }
    if (names.size === 0) throw new Error('no API keys configured');
    return store;
  }

  add(name: string, key: string): void {
    this.byHash.set(sha256(key), name);
  }

  /** Client name for this key, or null if the key is missing or unknown. */
  identify(key: string | undefined): string | null {
    if (!key) return null;
    return this.byHash.get(sha256(key)) ?? null;
  }

  get size(): number {
    return this.byHash.size;
  }
}

/** Read the API key from "Authorization: Bearer <key>" or "X-API-Key: <key>". */
export function extractApiKey(headers: IncomingHttpHeaders): string | undefined {
  const auth = headers.authorization;
  if (auth) {
    const m = /^Bearer\s+(.+)$/i.exec(auth.trim());
    if (m) return m[1].trim();
  }
  const header = headers['x-api-key'];
  return (Array.isArray(header) ? header[0] : header)?.trim() || undefined;
}
