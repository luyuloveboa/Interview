/**
 * An expected, caller-facing error. `code` is a stable machine-readable string, `status` is the
 * HTTP status the service should answer with, and `headers` are extra response headers
 * (e.g. Retry-After on 503).
 */
export class GlyphError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
    public readonly headers: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'GlyphError';
  }
}
