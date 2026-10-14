import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendDir = path.resolve(__dirname, '..');

test('OPT-07: @vercel/speed-insights is declared in package.json dependencies', () => {
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(frontendDir, 'package.json'), 'utf8')
  );

  assert.ok(
    packageJson.dependencies['@vercel/speed-insights'],
    '@vercel/speed-insights must be present in dependencies'
  );
});

test('OPT-07: App.jsx imports and mounts SpeedInsights once at root level', () => {
  const appCode = fs.readFileSync(path.join(frontendDir, 'src/App.jsx'), 'utf8');

  // Verify official import
  assert.match(
    appCode,
    /import\s*\{\s*SpeedInsights\s*\}\s*from\s*['"]@vercel\/speed-insights\/react['"]/,
    'Must import SpeedInsights from @vercel/speed-insights/react'
  );

  // Verify single root-level mount inside BrowserRouter
  const matches = appCode.match(/<SpeedInsights\s*\/>/g);
  assert.equal(matches?.length, 1, 'SpeedInsights must be mounted exactly once');

  // Verify it is placed inside BrowserRouter
  const browserRouterIdx = appCode.indexOf('<BrowserRouter>');
  const speedInsightsIdx = appCode.indexOf('<SpeedInsights');
  const closeBrowserRouterIdx = appCode.indexOf('</BrowserRouter>');

  assert.ok(browserRouterIdx !== -1, 'BrowserRouter must exist');
  assert.ok(speedInsightsIdx > browserRouterIdx && speedInsightsIdx < closeBrowserRouterIdx,
    'SpeedInsights must be placed inside BrowserRouter to capture client-side route navigations');
});

test('OPT-07: No private financial data or custom sensitive properties are passed to SpeedInsights', () => {
  const appCode = fs.readFileSync(path.join(frontendDir, 'src/App.jsx'), 'utf8');

  // Extract SpeedInsights tag
  const tagMatch = appCode.match(/<SpeedInsights[^>]*\/>/);
  assert.ok(tagMatch, 'SpeedInsights JSX tag must be found');
  const tag = tagMatch[0];

  assert.doesNotMatch(tag, /token|auth|email|password|amount|income|budget|balance/i,
    'SpeedInsights must not receive sensitive or financial attributes');
});
