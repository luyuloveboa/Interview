import { Piscina } from 'piscina';
import type { CachedImage } from '../cache/imageCache';
import { GlyphError } from '../errors';
import type { Renderer, RenderJob } from './renderer';
import type { WorkerReply } from './worker';

export interface WorkerPoolOptions {
  fontsDir: string;
  /** Worker threads to run (all started up front so the first requests aren't slow). */
  threads: number;
  /** Jobs allowed to wait for a free worker. Beyond this, requests get 503 right away. */
  maxQueue: number;
  /** A render running longer than this is killed (its worker is replaced) and returns 503. */
  timeoutMs: number;
}

/**
 * Renders on a pool of worker threads, so CPU-heavy rasterizing runs on all cores and never
 * blocks the HTTP event loop.
 *
 * Backpressure: at most threads + maxQueue jobs may be running or waiting at once. Past that
 * we shed load with 503 + Retry-After instead of building an unbounded backlog that ends in
 * timeouts or running out of memory. We count jobs ourselves instead of using Piscina's
 * maxQueue, which didn't reject in testing (its scheduler can bypass the queue it limits).
 */
export class WorkerPoolRenderer implements Renderer {
  private readonly pool: Piscina;
  /** Jobs currently running or waiting for a worker. */
  private inFlight = 0;
  private rejected = 0;

  constructor(private readonly opts: WorkerPoolOptions) {
    this.pool = new Piscina({
      filename: new URL('./worker-entry.mjs', import.meta.url).href,
      minThreads: opts.threads,
      maxThreads: opts.threads,
      idleTimeout: Infinity,
      workerData: { fontsDir: opts.fontsDir },
    });
  }

  async render(job: RenderJob): Promise<CachedImage> {
    if (this.inFlight >= this.opts.threads + this.opts.maxQueue) {
      this.rejected++;
      throw new GlyphError('overloaded', 503, 'render queue is full, retry shortly', { 'retry-after': '1' });
    }
    let reply: WorkerReply;
    this.inFlight++;
    try {
      // The timeout covers time spent waiting in the queue too. It's a deadline for the whole job.
      reply = await this.pool.run(job, { signal: AbortSignal.timeout(this.opts.timeoutMs) });
    } catch (err) {
      const e = err as Error;
      if (e.name === 'AbortError' || e.name === 'TimeoutError') {
        throw new GlyphError('render_timeout', 503, `render took longer than ${this.opts.timeoutMs} ms`, { 'retry-after': '1' });
      }
      throw err;
    } finally {
      this.inFlight--;
    }
    if (!reply.ok) throw new GlyphError(reply.code, reply.status, reply.message);
    // Structured clone turns the worker's Buffer into a Uint8Array. Wrap it without copying.
    return { png: Buffer.from(reply.png.buffer, reply.png.byteOffset, reply.png.byteLength), width: reply.width, height: reply.height };
  }

  async close(): Promise<void> {
    await this.pool.destroy();
  }

  stats() {
    return {
      mode: 'workers',
      threads: this.pool.threads.length,
      inFlight: this.inFlight,
      maxInFlight: this.opts.threads + this.opts.maxQueue,
      rejected: this.rejected,
      completed: this.pool.completed,
      utilization: Math.round(this.pool.utilization * 1000) / 1000,
    };
  }
}
