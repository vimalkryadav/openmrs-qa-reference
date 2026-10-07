import { chromium, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(path.dirname(new URL(import.meta.url).pathname),'..');
const evidence=path.join(root,'test-results');await fs.mkdir(evidence,{recursive:true});
const owned=JSON.parse(await fs.readFile(path.join(root,'.build/test-fixtures/ref-ids.json'),'utf8'));
const fixtures=owned;
const browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true});
const page=await context.newPage();const errors=[],failedRequests=[],consoleErrors=[],results=[];
page.on('pageerror',e=>errors.push(e.message));page.on('requestfailed',r=>failedRequests.push({url:r.url(),error:r.failure()?.errorText}));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});
const origin=process.env.REFERENCE_ORIGIN || 'http://localhost:8090';
async function check(name,fn){try{await fn();results.push({name,status:'passed'});console.log('PASS '+name);}catch(e){results.push({name,status:'failed',error:e.message});await page.screenshot({path:path.join(evidence,'failure-'+results.length+'.png'),fullPage:true});throw e;}}
try{
await check('Legacy login with fixed admin credentials',async()=>{await page.goto(origin+'/openmrs/login.htm');await page.locator('#username').fill('admin');await page.locator('#password').fill('Admin123');await page.locator('input[type=submit]').click();await expect(page.locator('#userLoggedInAs')).toContainText('System Administrator');});
const route=origin+'/openmrs/module/reporting/definition/editAnnotatedDefinition.form?uuid='+fixtures.converted+'&type=org.openmrs.module.reporting.data.person.definition.ConvertedPersonDataDefinition&parentType=org.openmrs.module.reporting.data.person.definition.PersonDataDefinition';
await check('Converter edit survives hard reload',async()=>{await page.goto(route);await expect(page.locator('#definitionToConvert-select')).toHaveValue(fixtures.source);await page.getByLabel('unspecifiedValue',{exact:true}).fill('Headless verified fallback');await page.locator('#save-button').click();await page.waitForURL('**/manageDefinitions.form?**');await page.goto(route);await page.reload();await expect(page.getByLabel('unspecifiedValue',{exact:true})).toHaveValue('Headless verified fallback');await page.screenshot({path:path.join(evidence,'converter-populated.png'),fullPage:true});});
await check('Malformed converter map is rejected without saving',async()=>{const input=page.getByLabel('conversions (JSON)',{exact:true});await input.fill('{bad');await page.locator('#save-button').click();await expect(page.locator('#converters-error')).toContainText('Enter valid JSON');expect(await input.evaluate(e=>e.validity.valid)).toBe(false);await page.screenshot({path:path.join(evidence,'converter-invalid.png'),fullPage:true});await input.fill('{"F":"Female","M":"Male"}');});
await check('Converter preview produces database-backed values',async()=>{await page.locator('#previewButton').click();const frame=page.frameLocator('iframe').last();await expect(frame.getByText('Evaluation Result',{exact:true})).toBeVisible();await expect(frame.locator('body')).toContainText('Female');await page.screenshot({path:path.join(evidence,'converter-preview.png'),fullPage:true});});
await check('Row-per-patient columns save and reload',async()=>{
 await page.goto(origin+'/openmrs/module/reporting/datasets/logicDataSetEditor.form?uuid='+owned.dataset);
 await page.locator('input[name=columnLabel]').first().fill('Headless gender');
 await page.locator('input[type=submit][value=Save]').click();await page.waitForLoadState('domcontentloaded');await page.reload();
 await expect(page.locator('input[name=columnLabel]').first()).toHaveValue('Headless gender');
 await page.screenshot({path:path.join(evidence,'row-patient-editor.png'),fullPage:true});
 if(process.env.VERIFY_DATASET_PREVIEW==='1') {
  await page.locator('#previewButton').click();const frame=page.frameLocator('iframe').last();
  await expect(frame.locator('body')).toContainText('Headless gender',{timeout:60000});
  await expect(frame.locator('body')).toContainText('preview cohort of 50 patients');
 } else {
  results.push({name:'Row-per-patient dataset preview',status:'blocked',reason:'Unbounded preview confirmed; source fix compiles but image rebuild is paused by user'});
 }
});
await check('Period label edit preserves embedded indicator',async()=>{
 await page.goto(origin+'/openmrs/module/reporting/reports/periodIndicatorReport.form?uuid='+owned.period);
 await page.locator('#editIndicator0').click();await page.locator('#labelField').fill('Headless one patient');
 await page.locator('#addColumnDialog input[type=submit]').click();await page.waitForLoadState('domcontentloaded');await page.reload();
 await expect(page.locator('body')).toContainText('Headless one patient');
 await page.screenshot({path:path.join(evidence,'period-label.png'),fullPage:true});
});
await check('Run report, reload history and download CSV',async()=>{
 await page.goto(origin+'/openmrs/module/reporting/run/runReport.form?reportId='+owned.row);
 const select=page.locator('select[name=selectedRenderer]');const choices=await select.locator('option').evaluateAll(es=>es.map(e=>({value:e.value,text:e.textContent})));
 const csv=choices.find(x=>x.text.includes('CSV'));if(!csv)throw new Error('CSV renderer missing: '+JSON.stringify(choices));await select.selectOption(csv.value);
 await page.locator('input[type=submit]').click();await page.waitForURL('**/reportHistoryOpen.form?**');
 owned.run=new URL(page.url()).searchParams.get('uuid');await fs.writeFile(path.join(root,'.build/test-fixtures/ref-ids.json'),JSON.stringify(owned,null,2));
 await expect.poll(async()=>{const r=await context.request.get(origin+'/openmrs/ws/rest/v1/reportingrest/reportRequest/'+owned.run+'?v=full');return (await r.json()).status;},{timeout:60000}).toBe('COMPLETED');await page.reload();
 const link=page.locator('a[href*="viewReport.form?uuid="]').first();const downloadPromise=page.waitForEvent('download');await link.click();const download=await downloadPromise;await download.saveAs(path.join(evidence,download.suggestedFilename()));
 await page.screenshot({path:path.join(evidence,'report-completed.png'),fullPage:true});
});
await check('Schedule once persists after reload',async()=>{
 await page.goto(origin+'/openmrs/module/reporting/run/runReport.form?reportId='+owned.row);
 await page.locator('#runReportshowSelectScheduleDialog').click();await page.locator('#runReportselectScheduleType').selectOption('once');
 await page.locator('#runReportonce-scheduleDate').fill('10/08/2026');await page.locator('#runReportonce-hours').selectOption('10');await page.locator('#runReportonce-minutes').selectOption('0');
 await page.getByRole('dialog').getByRole('button',{name:'Save',exact:true}).click();
 await expect(page.locator('#runReportscheduleExpression')).not.toHaveValue('');await page.locator('input[type=submit]').click();await page.waitForURL('**/reportHistoryOpen.form?**');
 owned.schedule=new URL(page.url()).searchParams.get('uuid');await fs.writeFile(path.join(root,'.build/test-fixtures/ref-ids.json'),JSON.stringify(owned,null,2));await page.reload();
 await expect(page.locator('body')).toContainText('Scheduled');await page.screenshot({path:path.join(evidence,'report-scheduled.png'),fullPage:true});
});
await check('SPA Reports dashboard loads',async()=>{await page.goto(origin+'/openmrs/spa/reports');await expect(page.getByText('Reports',{exact:true}).first()).toBeVisible({timeout:60000});await page.screenshot({path:path.join(evidence,'reports-dashboard.png'),fullPage:true});});
}catch(error){console.error(error.stack);process.exitCode=1;}finally{await fs.writeFile(path.join(evidence,'results.json'),JSON.stringify({headless:true,origin,results,pageErrors:errors,consoleErrors,failedRequests},null,2));await browser.close();}
