import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';
import Fastify, { type FastifyReply, type FastifyRequest } from 'fastify';
import { type ApiKeyStore, extractApiKey } from '../auth';
import { ImageCache, renderCacheKey } from '../cache/imageCache';
import { GlyphError } from '../errors';
import { type FontRegistry, hashBytes, UploadedFontCache } from '../font/registry';
import { LIMITS } from '../limits';
import { parseRenderOptions } from '../options';
import { type FontRef, InlineRenderer, type Renderer } from '../renderer/renderer';

export interface AppOptions {
  registry: FontRegistry;
  /** API keys, or 'disabled' to turn auth off (local dev and tests). Must be chosen explicitly. */
  auth: ApiKeyStore | 'disabled';
  /** Where renders run. Defaults to the main thread. The app closes it on shutdown. */
  renderer?: Renderer;
  /** Finished-image cache. Defaults to 64 MB. `false` turns caching off. */
  imageCache?: ImageCache | false;
  /** Requests per window: per valid API key, and per IP for requests without one. `false` = off. */
  rateLimit?: { perKey: number; perIp: number; windowMs?: number } | false;
  /** Pass `true` (or a pino config) for JSON logs. Tests leave it off. */
  logger?: boolean | object;
}

/** Fields a render request may carry, in the JSON body, the query string or the form fields. */
const RENDER_FIELDS = ['text', 'font', 'size', 'color', 'background', 'padding'] as const;

declare module 'fastify' {
  interface FastifyRequest {
    /** Name of the authenticated API client ("anonymous" when auth is disabled). */
    client?: string;
  }
  interface FastifyContextConfig {
    /** Set to false on routes that need no API key (e.g. health checks). */
    auth?: boolean;
  }
}

/**
 * Build the HTTP app. It doesn't listen, so tests can call it with `app.inject()`.
 *
 * Each request goes through these steps, in order:
 *   1. rate limit: per API key, or per IP when the key is missing or wrong (onRequest)
 *   2. auth: a valid API key is required, except on /healthz (preParsing, before any body is read)
 *   3. validate the input, then check the ETag / image cache, then render on the Renderer
 *
 * Routes:
 *   POST /render         JSON {text, font, size?, color?, background?, padding?} -> image/png
 *   GET  /render?...     same fields as query params
 *   POST /render/upload  multipart: "font" file + the same fields -> image/png
 *   GET  /fonts          {fonts: [...names]}
 *   GET  /stats          cache and renderer counters
 *   GET  /healthz        {status: "ok"} (no auth, no rate limit)
 * Every error returns JSON {error: code, message} with a matching status code.
 */
export function buildApp({ registry, auth, renderer, imageCache, rateLimit: rl = false, logger = false }: AppOptions) {
  const app = Fastify({
    logger,
    bodyLimit: 64 * 1024, // JSON bodies are small. Font uploads have their own limit below.
    requestTimeout: 10_000,
  });
  const uploads = new UploadedFontCache();
  const render: Renderer = renderer ?? new InlineRenderer(registry, uploads);
  const cache = imageCache === false ? null : (imageCache ?? new ImageCache(64 * 1024 * 1024));
  app.addHook('onClose', () => render.close());

  /** Client name for the request's key, or null. */
  const identify = (req: FastifyRequest) => (auth === 'disabled' ? 'anonymous' : auth.identify(extractApiKey(req.headers)));

  app.register(multipart, {
    limits: { fileSize: LIMITS.maxFontBytes, files: 1, fields: RENDER_FIELDS.length, fieldSize: 16 * 1024 },
  });

  if (rl) {
    app.register(rateLimit, {
      timeWindow: rl.windowMs ?? 60_000,
      // Valid keys get their own bucket. Everything else shares a much smaller per-IP bucket,
      // so guessing keys is throttled and invented keys can't each get a fresh bucket.
      keyGenerator: (req) => {
        const client = identify(req);
        return client && auth !== 'disabled' ? `key:${client}` : `ip:${req.ip}`;
      },
      max: (_req, key) => (key.startsWith('key:') ? rl.perKey : rl.perIp),
      errorResponseBuilder: (_req, ctx) =>
        new GlyphError('rate_limited', 429, `rate limit of ${ctx.max} requests per window exceeded, retry in ${ctx.after}`),
    });
  }

  // Routes live in a child plugin so they're added after the rate-limit plugin has loaded
  // (it attaches itself to each route as the route is created).
  app.register(async (api) => {
    // Auth runs in preParsing, before any request body is read or buffered.
    api.addHook('preParsing', async (req) => {
      if (req.routeOptions.config?.auth === false) return;
      const client = identify(req);
      if (!client) {
        throw new GlyphError('unauthorized', 401, 'missing or invalid API key (use "Authorization: Bearer <key>" or "X-API-Key")', {
          'www-authenticate': 'Bearer',
        });
      }
      req.client = client;
    });

    /**
     * Shared by every render route: validate, then answer from the ETag or the cache, or render.
     * The ETag is a hash of all the inputs, so a client that already has the image gets a 304
     * without the server even looking in the cache.
     */
    async function sendRender(req: FastifyRequest, reply: FastifyReply, font: FontRef, fontId: string, fields: Record<string, unknown>) {
      const options = parseRenderOptions(fields);
      const key = renderCacheKey(fontId, options);
      const etag = `"${key}"`;
      reply.header('etag', etag).header('cache-control', 'private, max-age=86400');
      if (matchesEtag(req.headers['if-none-match'], etag)) return reply.code(304).send();

      const started = performance.now();
      const job = { font, options };
      const { image, status } = cache
        ? await cache.getOrCreate(key, () => render.render(job))
        : { image: await render.render(job), status: 'BYPASS' };
      req.log.info(
        { client: req.client, font: fontId.slice(0, 18), chars: options.text.length, width: image.width, height: image.height, cache: status, ms: Math.round(performance.now() - started) },
        'rendered',
      );
      return reply.header('x-cache', status).type('image/png').send(image.png);
    }

    /** Resolve a registry font from the request fields (404 if unknown). */
    function namedFont(fields: Record<string, unknown>): { ref: FontRef; id: string } {
      if (typeof fields.font !== 'string' || fields.font === '') {
        throw new GlyphError('invalid_request', 400, `font is required; available: ${registry.names().join(', ')}`);
      }
      registry.get(fields.font); // throws font_not_found
      return { ref: { kind: 'registry', name: fields.font }, id: `r:${fields.font.toLowerCase()}` };
    }

    api.post('/render', async (req, reply) => {
      const body = req.body;
      if (!body || typeof body !== 'object' || Array.isArray(body)) {
        throw new GlyphError('invalid_request', 400, 'body must be a JSON object');
      }
      const fields = body as Record<string, unknown>;
      const { ref, id } = namedFont(fields);
      return sendRender(req, reply, ref, id, fields);
    });

    api.get('/render', async (req, reply) => {
      const fields = req.query as Record<string, unknown>;
      const { ref, id } = namedFont(fields);
      return sendRender(req, reply, ref, id, fields);
    });

    api.post('/render/upload', async (req, reply) => {
      if (!req.isMultipart()) {
        throw new GlyphError('unsupported_media_type', 415, 'expected multipart/form-data with a "font" file field');
      }
      const fields: Record<string, unknown> = {};
      let fontBytes: Buffer | undefined;
      for await (const part of req.parts()) {
        if (part.type === 'file') {
          if (part.fieldname !== 'font') {
            await part.toBuffer(); // drain it so the stream doesn't stall
            throw new GlyphError('invalid_request', 400, `unexpected file field "${part.fieldname}"; use "font"`);
          }
          fontBytes = await part.toBuffer(); // throws 413 past LIMITS.maxFontBytes
        } else if ((RENDER_FIELDS as readonly string[]).includes(part.fieldname)) {
          fields[part.fieldname] = part.value;
        }
      }
      if (!fontBytes || fontBytes.length === 0) {
        throw new GlyphError('invalid_request', 400, 'missing "font" file field');
      }
      const hash = hashBytes(fontBytes);
      return sendRender(req, reply, { kind: 'upload', hash, bytes: fontBytes }, `u:${hash}`, fields);
    });

    api.get('/fonts', async () => ({ fonts: registry.names() }));

    api.get('/stats', async () => ({
      cache: cache?.stats() ?? null,
      renderer: render.stats?.() ?? { mode: 'inline' },
    }));

    api.get('/healthz', { config: { auth: false, rateLimit: false } }, async () => ({ status: 'ok' }));
  });

  app.setNotFoundHandler((req, reply) => {
    reply.code(404).send({ error: 'not_found', message: `no route for ${req.method} ${req.url}` });
  });

  // Turn every error into the same JSON shape. Expected errors keep their status, and
  // anything unexpected becomes a 500 without internal details.
  app.setErrorHandler((err: unknown, req, reply) => {
    if (err instanceof GlyphError) {
      if (err.status >= 500) req.log.warn({ code: err.code }, err.message);
      return reply.code(err.status).headers(err.headers).send({ error: err.code, message: err.message });
    }
    const e = err as { statusCode?: number; code?: string; message?: string };
    if (e.statusCode && e.statusCode >= 400 && e.statusCode < 500) {
      // Fastify and plugin errors: bad JSON, body too large, wrong content type, file too large...
      return reply.code(e.statusCode).send({ error: fastifyErrorCode(e.code), message: e.message ?? 'bad request' });
    }
    req.log.error({ err }, 'unhandled error');
    return reply.code(500).send({ error: 'internal_error', message: 'internal server error' });
  });

  return app;
}

/** True if an If-None-Match header (which may list several ETags, or "*") matches `etag`. */
function matchesEtag(header: string | undefined, etag: string): boolean {
  if (!header) return false;
  return header.split(',').some((t) => {
    const tag = t.trim().replace(/^W\//, '');
    return tag === etag || tag === '*';
  });
}

/** Map Fastify's internal error codes to our short public ones. */
function fastifyErrorCode(code?: string): string {
  switch (code) {
    case 'FST_ERR_CTP_BODY_TOO_LARGE':
    case 'FST_REQ_FILE_TOO_LARGE':
    case 'FST_FILES_LIMIT':
    case 'FST_FIELDS_LIMIT':
    case 'FST_PARTS_LIMIT':
      return 'payload_too_large';
    case 'FST_ERR_CTP_INVALID_MEDIA_TYPE':
      return 'unsupported_media_type';
    default:
      return 'invalid_request';
  }
}
