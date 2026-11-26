import { Canvas } from './canvas/canvas';
import { encodePng } from './encode/png';
import { GlyphError } from './errors';
import type { Font } from './font/loadFont';
import { layoutText } from './layout/layout';
import { LIMITS } from './limits';
import type { RenderOptions } from './options';
import { flattenPath } from './raster/flatten';
import { createMask, rasterizeInto } from './raster/rasterize';

export interface RenderResult {
  png: Buffer;
  width: number;
  height: number;
}

/**
 * The whole pipeline, in order:
 * lay out the glyphs, size the canvas, rasterize every glyph into one coverage mask,
 * paint the mask in the text color over the background, then encode a PNG.
 * Throws GlyphError('image_too_large', 413) if the result would go over the size limit.
 */
export function renderText(font: Font, opts: RenderOptions): RenderResult {
  const layout = layoutText(font, opts.text, opts.size);
  const width = Math.max(1, Math.ceil(layout.width) + 2 * opts.padding);
  const height = Math.max(1, Math.ceil(layout.height) + 2 * opts.padding);
  if (width > LIMITS.maxImageWidth || height > LIMITS.maxImageHeight) {
    throw new GlyphError(
      'image_too_large',
      413,
      `rendered image would be ${width}x${height}; max is ${LIMITS.maxImageWidth}x${LIMITS.maxImageHeight}`,
    );
  }

  // One shared mask for all glyphs; each glyph only touches the rows it covers.
  const mask = createMask(width, height);
  for (const g of layout.glyphs) {
    const segs = flattenPath(g.glyph.commands, layout.scale, opts.padding + g.x, opts.padding + g.y);
    rasterizeInto(segs, mask);
  }

  const canvas = new Canvas(width, height, opts.background);
  canvas.blendMask(mask, opts.color);
  return { png: encodePng(width, height, canvas.data), width, height };
}
