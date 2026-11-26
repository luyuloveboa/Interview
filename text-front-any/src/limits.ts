/** Hard limits that keep one request from using unbounded CPU or memory. */
export const LIMITS = {
  maxTextLength: 2000,
  minSize: 4,
  maxSize: 512,
  maxPadding: 256,
  maxImageWidth: 4096,
  maxImageHeight: 4096,
  maxFontBytes: 5 * 1024 * 1024,
} as const;
