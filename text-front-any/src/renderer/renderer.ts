import type { CachedImage } from '../cache/imageCache';
import type { FontRegistry, UploadedFontCache } from '../font/registry';
import type { RenderOptions } from '../options';
import { renderText } from '../render';

/** Which font to use: a registry font by name, or uploaded bytes identified by their SHA-256. */
export type FontRef = { kind: 'registry'; name: string } | { kind: 'upload'; hash: string; bytes: Uint8Array };

/** One unit of render work. Plain data, so it can be sent to a worker thread. */
export interface RenderJob {
  font: FontRef;
  options: RenderOptions;
}

/** Something that turns a RenderJob into a PNG: on this thread, or in a worker pool. */
export interface Renderer {
  render(job: RenderJob): Promise<CachedImage>;
  close(): Promise<void>;
  stats?(): Record<string, unknown>;
}

/** Resolve the job's font and render it synchronously. Shared by the inline renderer and the workers. */
export function executeJob(job: RenderJob, registry: FontRegistry, uploads: UploadedFontCache): CachedImage {
  const font = job.font.kind === 'registry' ? registry.get(job.font.name) : uploads.get(job.font.bytes, job.font.hash);
  return renderText(font, job.options);
}

/**
 * Renders on the main thread. Simple and the lowest latency for one request, but a render
 * blocks the event loop. Used in tests and when RENDER_WORKERS=0.
 */
export class InlineRenderer implements Renderer {
  constructor(
    private readonly registry: FontRegistry,
    private readonly uploads: UploadedFontCache,
  ) {}

  async render(job: RenderJob): Promise<CachedImage> {
    return executeJob(job, this.registry, this.uploads);
  }

  async close(): Promise<void> {}
}
