import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendDir = path.resolve(__dirname, '..');

test('OPT-04: vercel.json is located at the frontend project root and not in src/', () => {
  const rootVercelJson = path.join(frontendDir, 'vercel.json');
  const srcVercelJson = path.join(frontendDir, 'src/vercel.json');

  assert.ok(fs.existsSync(rootVercelJson), 'vercel.json must exist at frontend/vercel.json');
  assert.ok(!fs.existsSync(srcVercelJson), 'frontend/src/vercel.json must not exist');
});

test('OPT-04: vercel.json configures long-lived immutable caching for hashed /assets/*', () => {
  const configContent = fs.readFileSync(path.join(frontendDir, 'vercel.json'), 'utf8');
  const config = JSON.parse(configContent);

  assert.ok(Array.isArray(config.headers), 'vercel.json must define headers array');

  const assetHeaderRule = config.headers.find((rule) => rule.source === '/assets/(.*)' || rule.source === '/assets/:match*');
  assert.ok(assetHeaderRule, 'Must define a header rule targeting /assets/(.*)');

  const cacheControl = assetHeaderRule.headers?.find(
    (h) => h.key.toLowerCase() === 'cache-control'
  );
  assert.ok(cacheControl, 'Asset header rule must set Cache-Control');
  assert.match(cacheControl.value, /public/, 'Cache-Control must include public');
  assert.match(cacheControl.value, /max-age=31536000/, 'Cache-Control must specify max-age=31536000 (1 year)');
  assert.match(cacheControl.value, /immutable/, 'Cache-Control must include immutable');

  // Ensure index.html and root are NOT given long-lived cache headers
  const rootHeaderRule = config.headers.find((rule) => rule.source === '/' || rule.source === '/index.html' || rule.source === '/(.*)');
  assert.ok(!rootHeaderRule, 'Must not assign long-lived cache headers to root or all paths');
});

test('OPT-04: vercel.json preserves client-side SPA routing fallback', () => {
  const configContent = fs.readFileSync(path.join(frontendDir, 'vercel.json'), 'utf8');
  const config = JSON.parse(configContent);

  assert.ok(Array.isArray(config.rewrites), 'vercel.json must define rewrites array');

  const spaRewrite = config.rewrites.find(
    (r) => r.destination === '/index.html'
  );
  assert.ok(spaRewrite, 'Must define an SPA rewrite pointing to /index.html');
  assert.equal(spaRewrite.source, '/(.*)', 'SPA rewrite should match /(.*)');
});
