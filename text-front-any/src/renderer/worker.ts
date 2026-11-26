// Runs inside each worker thread. Every worker loads its own copy of the bundled fonts once,
// then renders jobs sent by the main thread's pool.
import { workerData } from 'node:worker_threads';
import { GlyphError } from '../errors';
import { FontRegistry, UploadedFontCache } from '../font/registry';
import { executeJob, type RenderJob } from './renderer';

/** What a worker sends back. Errors are plain data, because Error subclasses don't survive the thread boundary. */
export type WorkerReply =
  | { ok: true; png: Uint8Array; width: number; height: number }
  | { ok: false; code: string; status: number; message: string };

const registry = FontRegistry.fromDirectory((workerData as { fontsDir: string }).fontsDir);
const uploads = new UploadedFontCache(8);

export default function renderTask(job: RenderJob): WorkerReply {
  try {
    const r = executeJob(job, registry, uploads);
    return { ok: true, png: r.png, width: r.width, height: r.height };
  } catch (err) {
    if (err instanceof GlyphError) return { ok: false, code: err.code, status: err.status, message: err.message };
    throw err; // unexpected: let the pool report it, and the app turns it into a 500
  }
}
