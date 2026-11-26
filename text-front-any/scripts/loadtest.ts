// Load test against a running Glyph server.
// Usage: npm run loadtest -- [--url http://127.0.0.1:3000] [--key <api key>] [--duration 10] [--connections 50]
// The key can also come from GLYPH_KEY. Start the server with a high RATE_LIMIT_PER_KEY (or
// RATE_LIMIT_DISABLED=true) first, or the rate limiter will answer most requests with 429.
import autocannon from 'autocannon';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    url: { type: 'string', default: 'http://127.0.0.1:3000' },
    key: { type: 'string', default: process.env.GLYPH_KEY ?? '' },
    duration: { type: 'string', default: '10' },
    connections: { type: 'string', default: '50' },
  },
});

const headers = { 'content-type': 'application/json', ...(values.key ? { 'x-api-key': values.key } : {}) };
let counter = 0;

/** Two scenarios: the same request every time (cache hits), and unique text each time (real renders). */
const scenarios = [
  {
    name: 'cached (same request)',
    requests: [{ method: 'POST' as const, path: '/render', headers, body: JSON.stringify({ text: 'Hello, load test!', font: 'DejaVuSans', size: 48 }) }],
  },
  {
    name: 'uncached (unique text)',
    requests: [
      {
        method: 'POST' as const,
        path: '/render',
        headers,
        // Build a fresh body for every request so each one is a cache miss and really renders.
        setupRequest: (req: Record<string, unknown>) => ({
          ...req,
          body: JSON.stringify({ text: `The quick brown fox #${counter++}`, font: 'DejaVuSerif', size: 48 }),
        }),
      },
    ],
  },
];

const rows = [];
for (const s of scenarios) {
  process.stdout.write(`running "${s.name}" for ${values.duration}s with ${values.connections} connections...\n`);
  const r = await autocannon({
    url: values.url,
    duration: Number(values.duration),
    connections: Number(values.connections),
    requests: s.requests as autocannon.Request[],
  });
  const codes = Object.entries(r.statusCodeStats ?? {}).map(([code, v]) => `${code}:${(v as { count: number }).count}`).join(' ');
  rows.push({
    scenario: s.name,
    'req/s': Math.round(r.requests.average),
    'p50 ms': r.latency.p50,
    'p99 ms': r.latency.p99,
    requests: r.requests.total,
    'status codes': codes,
    errors: r.errors + r.timeouts,
  });
}
console.table(rows);

// Show the server's own view: cache hit rate, pool usage, shed jobs.
const stats = await fetch(`${values.url}/stats`, { headers }).then((res) => res.json()).catch(() => null);
if (stats) console.log('server /stats:', JSON.stringify(stats));
