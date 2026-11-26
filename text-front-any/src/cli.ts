// Manual check: render text to a PNG file.
// Usage: npm run render -- "Hello, world" [fontNameOrPath] [out.png] [size]
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { loadFont } from './font/loadFont';
import { parseRenderOptions } from './options';
import { renderText } from './render';

const [text = 'Hello, Glyph!', fontArg = 'DejaVuSans', out = 'out.png', size] = process.argv.slice(2);

// Accept a path to a TTF, or a bare name from the fonts/ folder.
const fontPath = existsSync(fontArg) ? fontArg : join('fonts', `${fontArg}.ttf`);
const font = loadFont(readFileSync(fontPath));
const result = renderText(font, parseRenderOptions({ text: text.replace(/\\n/g, '\n'), size }));
writeFileSync(out, result.png);
console.log(`wrote ${out} (${result.width}x${result.height})`);
