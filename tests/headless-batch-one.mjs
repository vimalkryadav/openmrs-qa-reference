import { chromium, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const out = path.resolve((process.env.REPORTS_EVIDENCE||'test-results/batch-one'));
const ids = JSON.parse(fs.readFileSync(path.join(out, 'ref-ids.json')));
const prefix = 'QA Reports Fix R1 20261008';
const base = '/openmrs/module/reporting';
const rest = '/openmrs/ws/rest/v1/reportingrest';
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL: 'http://localhost:8090', httpCredentials: { username: 'admin', password: 'Admin123' }, timezoneId: 'UTC', viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(15000);
const prior = fs.existsSync(path.join(out, 'browser-results.json')) ? JSON.parse(fs.readFileSync(path.join(out, 'browser-results.json'))) : {};
const results = [], events = [], requests = prior.requests || [];
let active = 'login';
function persist() { fs.writeFileSync(path.join(out, 'browser-results.json'), JSON.stringify({ results, events, requests }, null, 2)); }
page.on('pageerror', e => events.push({ active, kind: 'pageerror', message: e.message }));
page.on('console', e => { if (e.type() === 'error') events.push({ active, kind: 'console', message: e.text() }); });
page.on('response', r => { if (r.status() >= 400) events.push({ active, kind: 'http', status: r.status(), url: r.url() }); });
page.on('requestfailed', r => events.push({ active, kind: 'requestfailed', url: r.url(), error: r.failure()?.errorText }));
async function check(name, fn) {
  if (process.env.CASE && !name.includes(process.env.CASE)) return;
  active = name; const row = { name, status: 'running' }; results.push(row); persist();
  try { Object.assign(row, await fn() || {}); row.status = 'pass'; }
  catch (e) { row.status = 'fail'; row.error = e.message; row.body = (await page.locator('body').innerText()).slice(-5000); }
  for (const [index, frame] of page.frames().entries()) fs.writeFileSync(path.join(out, name + '-frame' + index + '.html'), await frame.content());
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage: true });
  fs.writeFileSync(path.join(out, name + '.aria.yml'), await page.locator('body').ariaSnapshot());
  persist(); console.log(row.status, name, row.error?.slice(0, 150) || '');
}
async function json(url) { const r = await context.request.get(url); if (!r.ok()) throw Error(url + ' HTTP ' + r.status()); return r.json(); }
async function waitRequest(uuid) {
  if (!requests.includes(uuid)) requests.push(uuid); persist();
  await expect.poll(async () => (await json(rest + '/reportRequest/' + uuid + '?v=full')).status, { timeout: 90000, intervals: [500, 1000] }).toMatch(/COMPLETED|FAILED/);
  return json(rest + '/reportRequest/' + uuid + '?v=full');
}
async function runLegacy(report, design) {
  await page.goto(base + '/run/runReport.form?reportId=' + report);
  const select = page.locator('[name=selectedRenderer]'); await select.waitFor();
  const option = await select.locator('option').evaluateAll((xs, id) => xs.find(x => x.value.includes(id))?.value, design);
  await select.selectOption(option);
  await page.locator('input[type=submit],button[type=submit]').first().click();
  await page.waitForURL('**/reportHistoryOpen.form?**');
  const request = await waitRequest(new URL(page.url()).searchParams.get('uuid'));
  await page.reload(); return request;
}
async function choose(label, value) {
  const control = page.getByRole('combobox', { name: label, exact: true });
  if (await control.evaluate(e => e.tagName) === 'SELECT') await control.selectOption({ label: value });
  else { await control.click(); await page.getByRole('option', { name: value, exact: true }).click(); }
}
async function sticker(patient) {
  await page.goto('/openmrs/spa/reports');
  await page.getByRole('button', { name: 'Run reports', exact: true }).click();
  await choose('Report', 'Patient Identifier Sticker');
  await page.getByLabel('Patient UUID', { exact: true }).fill(patient);
  await choose('Output format', 'Patient ID Sticker PDF');
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().split('?')[0].endsWith('/reportRequest'));
  await page.getByRole('button', { name: 'Run', exact: true }).click();
  const r = await response; expect(r.ok()).toBeTruthy();
  const request = await waitRequest((await r.json()).uuid); expect(request.status).toBe('COMPLETED');
  const download = await json(rest + '/downloadReport?reportRequestUuid=' + request.uuid);
  return { request: request.uuid, xml: Buffer.from(download.fileContent, 'base64').toString() };
}
async function sql(value) {
  await page.goto(base + '/datasets/iterableSqlDataSetEditor.form?uuid=' + ids.dataset);
  await page.locator('#editBox').fill(value);
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await page.waitForTimeout(250); await page.reload();
  await expect(page.locator('#editBox')).toHaveValue(value);
  await page.goto(base + '/reports/reportEditor.form?uuid=' + ids.report);
  await page.evaluate(async ({ base, ids }) => {
    const guard = await (await fetch('/openmrs/csrfguard')).text();
    const tokenName = guard.match(/var tokenName\s*=\s*["']([^"']+)/)[1];
    const token = guard.match(/var masterTokenValue\s*=\s*["']([^"']+)/)[1];
    const response = await fetch(base + '/reports/saveMappedProperty.form', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded', [tokenName]:token}, body:new URLSearchParams({type:'org.openmrs.module.reporting.report.definition.ReportDefinition',uuid:ids.report,property:'dataSetDefinitions',currentKey:'result',newKey:'result',mappedUuid:ids.dataset,[tokenName]:token})});
    if (!response.ok) throw Error('Remap failed ' + response.status);
  }, { base, ids });
}
async function previewIterable(dataset = ids.dataset) {
  await page.goto(base + '/definition/manageDefinitions.form?type=org.openmrs.module.reporting.dataset.definition.DataSetDefinition');
  const row = page.locator('tr').filter({ has: page.locator('a[href*="' + dataset + '"]') }).last();
  await row.locator('img[src*="play"]').click();
  await expect.poll(() => page.frames().some(f => f.url().includes('/parameters/queryParameter.form'))).toBeTruthy();
  const frame = page.frames().find(f => f.url().includes('/parameters/queryParameter.form'));
  await expect(frame.locator('body')).toContainText(/Evaluation Result|Unable|Error evaluating/);
  return frame.locator('body').innerText();
}
await page.goto('/openmrs/login.htm');
await page.locator('#username').fill('admin'); await page.locator('#password').fill('Admin123');
await page.locator('input[type=submit]').click();
await check('clock-login', async () => { await page.goto('/openmrs/spa/reports'); await expect(page.getByRole('button', { name: 'Run reports', exact: true })).toBeVisible(); expect(await page.evaluate(() => new Date().toISOString())).toBe('2026-09-29T09:00:00.000Z'); });
await check('sticker-valid-identity', async () => { const r = await sticker('5eed5eed-6759-4f8c-8021-cd1f670d536a'); expect(r.xml).toContain('Aarav Abhyankar'); expect(r.xml).toContain('900001L'); fs.writeFileSync(path.join(out, 'valid-sticker.xml'), r.xml); return { request: r.request }; });
await check('sticker-unknown-empty', async () => { const r = await sticker('QA-Reports-Fix-R1-missing'); expect(r.xml).toMatch(/<fields\s*\/>/); expect(r.xml).not.toContain('900001L'); fs.writeFileSync(path.join(out, 'unknown-sticker.xml'), r.xml); return { request: r.request }; });
await check('iterable-sql-preview-two-rows', async () => { const text = await previewIterable(); expect(text).toMatch(/qa_value/); expect(text).toMatch(/\b7\b/); expect(text).toMatch(/\b9\b/); expect(text).not.toMatch(/Unable|Exception/); return { text }; });
await check('iterable-sql-csv-two-rows', async () => { const r = await runLegacy(ids.report, ids.csv); expect(r.status).toBe('COMPLETED'); ids.completed = r.uuid; const csv = await (await context.request.get(base + '/reports/viewReport.form?uuid=' + r.uuid)).text(); expect(csv.replaceAll('\r', '')).toBe('"qa_value"\n"7"\n"9"\n'); fs.writeFileSync(path.join(out, 'iterable.csv'), csv); return { request: r.uuid, csv }; });
await check('saved-report-on-demand-logging', async () => { await page.goto(base + '/reports/reportHistoryOpen.form?uuid=' + ids.completed); await page.getByText('Process Report: ' + prefix + ' Logging', { exact: true }).click(); await page.waitForTimeout(400); await page.reload(); await page.getByText('View Log', { exact: true }).click(); const text = await page.locator('body').innerText(); expect(text).toContain('Processing Report with ' + prefix + ' Logging'); expect(text).not.toContain('Report Processor Failed: ' + prefix + ' Logging'); return { request: ids.completed, log: text }; });
await check('iterable-preview-cap-and-full-export', async () => {
  const text = await previewIterable(ids.capDataset); expect(text).toContain('limited to 50 rows');
  const frame = page.frames().find(f => f.url().includes('/parameters/queryParameter.form'));
  expect(await frame.locator('table').last().locator('tr').count()).toBe(51);
  const request = await runLegacy(ids.capReport, ids.capCsv); expect(request.status).toBe('COMPLETED');
  const csv = await (await context.request.get(base + '/reports/viewReport.form?uuid=' + request.uuid)).text();
  expect(csv.trim().split('\n')).toHaveLength(61);
  return { previewRows: 50, exportRows: 60, request: request.uuid };
});
await check('iterable-sql-empty-and-invalid', async () => {
  await sql('SELECT 7 AS qa_value WHERE 1=0'); const empty = await previewIterable(); expect(empty).toContain('Evaluation Result'); expect(empty).not.toMatch(/Unable|Exception/);
  await sql('SELECT missing_r1_column AS qa_value'); const invalid = await previewIterable(); expect(invalid).toMatch(/Unable|Error evaluating/); expect(invalid).not.toContain('dataSetRowIterator');
  await sql('SELECT 7 AS qa_value UNION ALL SELECT 9 AS qa_value'); return { empty, invalid };
});
const textEditor = base + '/reports/renderers/textTemplateReportRenderer.form?reportDefinitionUuid=' + ids.textReport + '&type=org.openmrs.module.reporting.report.renderer.TextTemplateRenderer&successUrl=/module/reporting/reports/manageReportDesigns.form';
async function setScript(value) { await page.frameLocator('#textarea-container iframe').locator('body').click(); await page.keyboard.press('ControlOrMeta+A'); await page.keyboard.press('Backspace'); if (value) await page.keyboard.type(value); }
await check('text-preview-never-saves-and-submit-does', async () => {
  await page.goto(textEditor + (ids.textDesign ? '&reportDesignUuid=' + ids.textDesign : '')); await page.locator('#name').fill(prefix + ' Text'); await page.locator('[name=scriptType]').selectOption('Velocity'); await setScript('SAVED $data.get("result.qa_value.1")'); await page.locator('#submitButton').click(); await page.waitForURL('**/manageReportDesigns.form*');
  const design = (await json(rest + '/reportDesign?reportDefinitionUuid=' + ids.textReport + '&v=full')).results.find(d => d.name === prefix + ' Text'); ids.textDesign = design.uuid; fs.writeFileSync(path.join(out, 'ref-ids.json'), JSON.stringify(ids, null, 2));
  const editor = textEditor + '&reportDesignUuid=' + design.uuid;
  await page.goto(editor); await setScript('UNSAVED $data.get("result.qa_value.1")'); await page.getByText('PREVIEW', { exact: true }).first().click();
  const preview = page.frameLocator('#previewFrame'); await preview.locator('input[type=submit][value=Preview]').click();
  await expect(preview.locator('#templateResult')).toHaveValue('UNSAVED 7');
  await page.goto(editor); await expect(page.locator('[name=script]')).toHaveValue('SAVED $data.get("result.qa_value.1")');
  await setScript(''); await page.getByText('PREVIEW', { exact: true }).first().click(); await preview.locator('input[type=submit][value=Preview]').click(); await expect(preview.locator('#templateResult')).toHaveValue(''); await page.goto(editor); await expect(page.locator('[name=script]')).toHaveValue('SAVED $data.get("result.qa_value.1")');
  await setScript('#if($broken'); await page.getByText('PREVIEW', { exact: true }).first().click(); await preview.locator('input[type=submit][value=Preview]').click(); await expect(preview.locator('[role=alert]')).toContainText('Unable to compile Velocity template');
  await page.goto(editor); await expect(page.locator('[name=script]')).toHaveValue('SAVED $data.get("result.qa_value.1")');
  await setScript('SUBMITTED $data.get("result.qa_value.1")'); await page.locator('#submitButton').click(); await page.waitForURL('**/manageReportDesigns.form*'); await page.goto(editor); await expect(page.locator('[name=script]')).toHaveValue('SUBMITTED $data.get("result.qa_value.1")'); return { design: design.uuid };
});
persist(); await browser.close();
if (results.some(r => r.status !== 'pass')) process.exitCode = 1;
