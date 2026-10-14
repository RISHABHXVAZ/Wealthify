import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, '../src');

test('OPT-03: AddExpense does not issue unused /api/categories request', () => {
  const addExpenseCode = fs.readFileSync(path.join(srcDir, 'pages/AddExpense.jsx'), 'utf8');

  // Verify /api/categories is NOT called
  assert.doesNotMatch(addExpenseCode, /\/api\/categories/, 'AddExpense must not call /api/categories');

  // Verify categories state is NOT maintained
  assert.doesNotMatch(addExpenseCode, /setCategories/, 'AddExpense must not maintain categories state');
  assert.doesNotMatch(addExpenseCode, /const\s+\[categories/, 'AddExpense must not declare categories state');

  // Verify form uses description-based AI categorization and submits expected payload
  assert.match(addExpenseCode, /API\.post\('\/api\/expenses'/, 'AddExpense must submit expense to /api/expenses');
  assert.match(addExpenseCode, /fetchBudget/, 'AddExpense must trigger fetchBudget from useBudget on success');
});

test('OPT-03: Budget.jsx does not issue duplicate GET /api/budget/allocation request on mount', () => {
  const budgetCode = fs.readFileSync(path.join(srcDir, 'pages/Budget.jsx'), 'utf8');

  // Verify Budget.jsx does NOT issue GET /api/budget/allocation
  assert.doesNotMatch(budgetCode, /API\.get\('\/api\/budget\/allocation'\)/,
    'Budget.jsx must not directly call API.get("/api/budget/allocation") on mount');

  // Verify Budget.jsx consumes budget and setBudget from useBudget
  assert.match(budgetCode, /useBudget\(\)/, 'Budget.jsx must consume useBudget() hook');
  assert.match(budgetCode, /const\s*\{\s*budget,/, 'Budget.jsx must extract budget from useBudget()');

  // Verify Budget.jsx keeps budget setup mutation
  assert.match(budgetCode, /API\.post\('\/api\/budget\/setup'/,
    'Budget.jsx must still submit budget configuration to /api/budget/setup');
});

test('OPT-03: BudgetContext is the sole canonical fetcher of /api/budget/allocation', () => {
  const contextCode = fs.readFileSync(path.join(srcDir, 'context/BudgetContext.jsx'), 'utf8');

  // BudgetContext fetches /api/budget/allocation
  assert.match(contextCode, /API\.get\('\/api\/budget\/allocation'\)/,
    'BudgetContext must be the canonical provider querying /api/budget/allocation');

  // BudgetContext exposes setBudget, budget, loading, and fetchBudget
  assert.match(contextCode, /setBudget/, 'BudgetContext must expose setBudget');
  assert.match(contextCode, /fetchBudget/, 'BudgetContext must expose fetchBudget');
  assert.match(contextCode, /loading/, 'BudgetContext must expose loading');

  // Search entire frontend/src for any other occurrences of /api/budget/allocation
  const pagesDir = path.join(srcDir, 'pages');
  const pageFiles = fs.readdirSync(pagesDir).filter(f => f.endsWith('.jsx'));

  for (const pageFile of pageFiles) {
    const content = fs.readFileSync(path.join(pagesDir, pageFile), 'utf8');
    assert.doesNotMatch(content, /API\.get\(['"`]\/api\/budget\/allocation['"`]\)/,
      `${pageFile} must not duplicate GET /api/budget/allocation; it must use useBudget() instead`);
  }
});
