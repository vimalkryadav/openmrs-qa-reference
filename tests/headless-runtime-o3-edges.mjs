// Focused schedule-loading and optional structured-input regression. Own fixtures only.
import { chromium, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const out = path.resolve(process.env.REPORTS_EDGE_EVIDENCE || 'test-results/runtime-o3-edges');
fs.mkdirSync(out, { recursive: true });
const prefix = 'QA Reports O3 edge 20261008';
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL: 'http://localhost:8090', httpCredentials: { username: 'admin', password: 'Admin123' }, timezoneId: 'UTC' });
const page = await context.newPage();
page.setDefaultTimeout(20000);
const errors = [], results = [], owned = {};
page.on('pageerror', error => errors.push(error.message));
const rest = '/openmrs/ws/rest/v1/reportingrest/';
const get = async url => { const response = await context.request.get(rest + url); expect(response.ok(), await response.text()).toBeTruthy(); return response.json(); };
let token = {};
async function form(route, values) {
  const response = await context.request.post('/openmrs/module/reporting/' + route, { form: { ...values, ...token }, headers: token, maxRedirects: 0 });
  expect(response.status(), await response.text()).toBeLessThan(400);
  return response;
}
function persist() { fs.writeFileSync(out + '/results.json', JSON.stringify({ results, errors, owned }, null, 2)); }
async function proof(id, action) { await action(); results.push({ id, status: 'pass' }); fs.writeFileSync(out + '/' + id + '.aria.yml', await page.locator('body').ariaSnapshot()); persist(); }
try {
  await page.goto('/openmrs/login.htm');
  await page.locator('#username').fill('admin'); await page.locator('#password').fill('Admin123');
  await page.locator('input[type=submit]').click();
  const csrf = await (await context.request.get('/openmrs/csrfguard')).text();
  token = { [csrf.match(/var tokenName\s*=\s*["']([^"']+)/)[1]]: csrf.match(/var masterTokenValue\s*=\s*["']([^"']+)/)[1] };
  const created = await form('reports/saveBaseParameterizable.form', { uuid: '', type: 'org.openmrs.module.reporting.report.definition.ReportDefinition', name: prefix, description: 'Owned loading/whitespace proof', successUrl: '/module/reporting/reports/reportEditor.form?uuid=uuid' });
  owned.report = new URL(created.headers().location, 'http://localhost:8090').searchParams.get('uuid'); persist();
  for (const [name, label, type] of [['memo', 'Memo', 'java.lang.String'], ['sorts', 'Optional sorts', 'org.openmrs.module.reporting.common.SortCriteria']]) {
    await form('parameters/saveParameter.form', { uuid: owned.report, type: 'org.openmrs.module.reporting.report.definition.ReportDefinition', currentName: '', newName: name, label, parameterType: type });
  }
  const datasetType = 'org.openmrs.module.reporting.dataset.definition.ObsDataSetDefinition';
  const dataset = await form('reports/saveBaseParameterizable.form', { uuid: '', type: datasetType, name: prefix + ' dataset', description: 'Never evaluated: future schedule only', successUrl: '/module/reporting/definition/editAnnotatedDefinition.form?uuid=uuid' });
  owned.dataset = new URL(dataset.headers().location, 'http://localhost:8090').searchParams.get('uuid'); persist();
  await form('definition/saveAnnotatedDefinition.form', { uuid: owned.dataset, type: datasetType, parentType: 'org.openmrs.module.reporting.dataset.definition.DataSetDefinition', name: prefix + ' dataset', 'parameter.columnDefinitions.allowAtEvaluation': 'f', 'parameter.columnDefinitions.value': '[]', 'parameter.sortCriteria.allowAtEvaluation': 't', 'parameter.rowFilters.allowAtEvaluation': 'f', 'parameter.rowFilters.value': '[]' });
  await form('reports/saveMappedProperty.form', { uuid: owned.report, type: 'org.openmrs.module.reporting.report.definition.ReportDefinition', property: 'dataSetDefinitions', newKey: 'rows', mappedUuid: owned.dataset, valueType_sortCriteria: 'mapped', mappedValue_sortCriteria: 'sorts' });
  await form('reports/renderers/saveDelimitedTextReportDesign.form', { name: prefix + ' CSV', reportDefinition: owned.report, rendererType: 'org.openmrs.module.reporting.report.renderer.CsvReportRenderer', successUrl: '/module/reporting/reports/manageReportDesigns.form' });
  fs.writeFileSync(out + '/definition.json', JSON.stringify(await get('reportDefinition/' + owned.report + '?v=full'), null, 2));
  owned.design = (await get('reportDesign?reportDefinitionUuid=' + owned.report + '&v=full')).results[0].uuid; persist();
  const response = await context.request.post(rest + 'reportRequest', { data: { reportDefinition: { parameterizable: { uuid: owned.report }, parameterMappings: { memo: 'retain my saved memo', sorts: [] } }, renderingMode: { argument: owned.design }, schedule: '0 58 23 * * ?' } });
  expect(response.ok(), await response.text()).toBeTruthy(); owned.request = (await response.json()).uuid; persist();
  async function open() {
    await page.goto('/openmrs/spa/reports/scheduled-overview');
    await page.getByRole('row').filter({ has: page.getByRole('cell', { name: prefix, exact: true }) }).getByRole('button', { name: 'Edit', exact: true }).click();
  }
  async function delayed(resource, id) {
    let release, intercepted = false;
    const gate = new Promise(resolve => { release = resolve; });
    const pattern = '**/' + resource + '/' + owned[resource === 'reportDefinition' ? 'report' : 'request'] + '**';
    await page.route(pattern, async route => { intercepted = true; await gate; await route.continue(); });
    try {
      await open(); await expect.poll(() => intercepted).toBeTruthy();
      await page.locator('#scheduleType').selectOption('everyDay'); await page.getByRole('textbox', { name: 'hh:mm', exact: true }).fill('23:58');
      await page.getByLabel('Output format', { exact: true }).selectOption(owned.design);
      await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
      let posts = 0; const listener = request => { if (request.method() === 'POST' && request.url().includes('/reportRequest')) posts++; };
      page.on('request', listener);
      await page.locator('form').filter({ has: page.getByLabel('Output format', { exact: true }) }).evaluate(form => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
      await page.waitForTimeout(100); expect(posts).toBe(0); page.off('request', listener);
      release();
      await expect(page.getByLabel('Memo', { exact: true })).toHaveValue('retain my saved memo');
      await page.getByLabel('Optional sorts', { exact: true }).fill('   \n\t');
      // Stock parameter creation marks this fixture required; whitespace must stay blocked.
      await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
      await page.getByLabel('Optional sorts', { exact: true }).fill('[]');
      await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeEnabled();
      const pending = page.waitForResponse(r => r.request().method() === 'POST' && /\/reportRequest(?:\?|$)/.test(r.url()));
      await page.getByRole('button', { name: 'Save', exact: true }).click(); const saved = await pending;
      expect(saved.ok(), await saved.text()).toBeTruthy();
      expect(saved.request().postDataJSON().reportDefinition.parameterMappings).toEqual({ memo: 'retain my saved memo', sorts: [] });
      const actual = await get('reportRequest/' + owned.request + '?v=full');
      expect(actual.uuid).toBe(owned.request); expect(actual.parameterMappings).toEqual({ memo: 'retain my saved memo', sorts: [] });
      fs.writeFileSync(out + '/' + id + '-request.json', JSON.stringify(actual, null, 2));
    } finally { release(); await page.unrouteAll({ behavior: 'wait' }); }
  }
  await proof('definition-pending-blocks-save-and-rejects-required-whitespace', () => delayed('reportDefinition', 'definition'));
  // A fresh page/context avoids an SWR cache hit masking the request-loading boundary.
  await page.goto('about:blank');
  await proof('request-pending-blocks-save-and-preserves-mappings', () => delayed('reportRequest', 'request'));
  expect(errors).toEqual([]);
} catch (error) {
  results.push({ id: 'interrupted', status: 'fail', error: String(error) });
  fs.writeFileSync(out + '/failure.html', await page.content()); process.exitCode = 1;
} finally {
  if (owned.request) { const r = await context.request.delete(rest + 'reportRequest/' + owned.request); expect([200, 204, 404]).toContain(r.status()); }
  if (owned.design) await form('reports/deleteReportDesign.form', { uuid: owned.design });
  if (owned.report) { const r = await context.request.get('/openmrs/module/reporting/definition/purgeDefinition.form?' + new URLSearchParams({ uuid: owned.report, type: 'org.openmrs.module.reporting.report.definition.ReportDefinition' })); expect(r.status()).toBeLessThan(400); }
  if (owned.dataset) { const r = await context.request.get('/openmrs/module/reporting/definition/purgeDefinition.form?' + new URLSearchParams({ uuid: owned.dataset, type: 'org.openmrs.module.reporting.dataset.definition.ObsDataSetDefinition' })); expect(r.status()).toBeLessThan(400); }
  const cleanup = {};
  for (const [key, resource] of [['request', 'reportRequest'], ['design', 'reportDesign'], ['report', 'reportDefinition'], ['dataset', 'dataSetDefinition']]) if (owned[key]) cleanup[key] = (await context.request.get(rest + resource + '/' + owned[key])).status();
  fs.writeFileSync(out + '/cleanup.json', JSON.stringify(cleanup, null, 2)); expect(Object.values(cleanup).every(status => status === 404)).toBeTruthy();
  persist(); await browser.close();
}
