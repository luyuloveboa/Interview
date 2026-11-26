import type { PathCommand } from '../font/loadFont';

/** A straight edge in pixel space (y down). Direction matters for the winding rule. */
export interface Segment {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

/** Max number of line pieces per curve, so a huge or bad curve can't explode memory. */
const MAX_STEPS = 64;

/**
 * Convert glyph outline commands (font units, y up) into line segments in pixel space.
 * A point (x, y) maps to (dx + x*scale, dy - y*scale). The minus flips y, because fonts
 * measure y upward and images measure it downward. (dx, dy) is the glyph origin on the baseline.
 * Curves are split into straight pieces with an error of about `tolerance` px.
 * Every contour is closed, even if the font left out the final Z.
 */
export function flattenPath(
  commands: PathCommand[],
  scale: number,
  dx: number,
  dy: number,
  tolerance = 0.2,
): Segment[] {
  const segs: Segment[] = [];
  const tx = (x = 0) => dx + x * scale;
  const ty = (y = 0) => dy - y * scale;
  let cx = 0, cy = 0; // current point
  let sx = 0, sy = 0; // start of current contour
  let open = false;

  const lineTo = (x: number, y: number) => {
    if (x !== cx || y !== cy) segs.push({ x0: cx, y0: cy, x1: x, y1: y });
    cx = x;
    cy = y;
  };
  const close = () => {
    if (open) lineTo(sx, sy);
    open = false;
  };
  // Pieces needed so the polyline stays within `tolerance` of the curve.
  // `dd` is the size of the curve's second difference (how far it bends).
  const stepsFor = (dd: number) =>
    Math.min(MAX_STEPS, Math.max(1, Math.ceil(Math.sqrt(dd / (8 * tolerance)))));

  for (const c of commands) {
    switch (c.type) {
      case 'M':
        close();
        cx = sx = tx(c.x);
        cy = sy = ty(c.y);
        open = true;
        break;
      case 'L':
        lineTo(tx(c.x), ty(c.y));
        break;
      case 'Q': {
        const x0 = cx, y0 = cy, x1 = tx(c.x1), y1 = ty(c.y1), x2 = tx(c.x), y2 = ty(c.y);
        const n = stepsFor(2 * Math.hypot(x0 - 2 * x1 + x2, y0 - 2 * y1 + y2));
        for (let i = 1; i <= n; i++) {
          const t = i / n, u = 1 - t;
          lineTo(u * u * x0 + 2 * u * t * x1 + t * t * x2, u * u * y0 + 2 * u * t * y1 + t * t * y2);
        }
        break;
      }
      case 'C': {
        const x0 = cx, y0 = cy;
        const x1 = tx(c.x1), y1 = ty(c.y1), x2 = tx(c.x2), y2 = ty(c.y2), x3 = tx(c.x), y3 = ty(c.y);
        const dd = 6 * Math.max(
          Math.hypot(x0 - 2 * x1 + x2, y0 - 2 * y1 + y2),
          Math.hypot(x1 - 2 * x2 + x3, y1 - 2 * y2 + y3),
        );
        const n = stepsFor(dd);
        for (let i = 1; i <= n; i++) {
          const t = i / n, u = 1 - t;
          const a = u * u * u, b = 3 * u * u * t, d = 3 * u * t * t, e = t * t * t;
          lineTo(a * x0 + b * x1 + d * x2 + e * x3, a * y0 + b * y1 + d * y2 + e * y3);
        }
        break;
      }
      case 'Z':
        close();
        break;
    }
  }
  close();
  return segs;
}
