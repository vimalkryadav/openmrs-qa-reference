import {chromium,expect} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const out=path.resolve(process.env.REPORTS_OBJECT_EVIDENCE||'test-results/runtime-o3');
const ids=JSON.parse(fs.readFileSync(out+'/ref-ids.json'));
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({baseURL:'http://localhost:8090',httpCredentials:{username:'admin',password:'Admin123'},timezoneId:'UTC',viewport:{width:1440,height:1000}});
const page=await context.newPage();page.setDefaultTimeout(20000);
const results=[],events=[];page.on('pageerror',error=>events.push({url:page.url(),message:error.message}));
const persist=()=>fs.writeFileSync(out+'/ref-ids.json',JSON.stringify(ids,null,2));
const rest='/openmrs/ws/rest/v1/reportingrest/';
const get=async url=>await(await context.request.get(rest+url)).json();
const columns=[{name:'Object ID',uuid:ids.obsId,mappings:{},converters:[]},{name:'Age',uuid:ids.age,mappings:{effectiveDate:'2026-09-29'},converters:[{type:'AgeConverter',properties:{}}]}];
const sorts=[{column:'Object ID',direction:'DESC'}];
async function proof(name,fn){const row={name};try{row.details=await fn();row.status='pass';}catch(error){row.status='fail';row.error=error.message;}await page.screenshot({path:out+'/'+name+'.png',fullPage:true});fs.writeFileSync(out+'/'+name+'.aria.yml',await page.locator('body').ariaSnapshot());results.push(row);fs.writeFileSync(out+'/o3-results.json',JSON.stringify({results,events},null,2));console.log(row.status,name,row.error||'');}
async function post(button){const pending=page.waitForResponse(response=>response.request().method()==='POST'&&/reportRequest(?:\?|$)/.test(response.url()));await button.click();const response=await pending;expect(response.ok(),await response.text()).toBeTruthy();const body=await response.text();const request=body?JSON.parse(body):await get('reportRequest/'+response.request().postDataJSON().uuid+'?v=full');if(!ids.requests.includes(request.uuid)){ids.requests.push(request.uuid);persist();}return request;}
async function fill(){await page.getByLabel('runtimeColumns',{exact:true}).fill(JSON.stringify(columns));await page.getByLabel('runtimeSort',{exact:true}).fill(JSON.stringify(sorts));}
await page.goto('/openmrs/login.htm');await page.locator('#username').fill('admin');await page.locator('#password').fill('Admin123');await page.locator('input[type=submit]').click();
const report=await get('reportDefinition/'+ids.obsReport+'?v=full');
await proof('o3-run-validation-output-summary',async()=>{
 await page.goto('/openmrs/spa/reports');await page.getByRole('button',{name:'Run reports',exact:true}).click();await page.getByLabel('Report',{exact:true}).selectOption(ids.obsReport);await page.getByLabel('Output format',{exact:true}).selectOption(ids.obsCsv);
 await page.getByLabel('runtimeColumns',{exact:true}).fill('{bad');await expect(page.getByText('Enter a valid JSON array of objects.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Run',exact:true})).toBeDisabled();await page.screenshot({path:out+'/o3-invalid-json-editor.png',fullPage:true});
 await page.getByLabel('runtimeColumns',{exact:true}).fill('[{"name":"ID","uuid":"missing","unknown":true}]');await expect(page.getByText('Remove unsupported object fields.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Run',exact:true})).toBeDisabled();
 await fill();await page.getByLabel('runtimeColumns',{exact:true}).focus();await page.keyboard.press('Tab');await expect(page.getByLabel('runtimeSort',{exact:true})).toBeFocused();await page.screenshot({path:out+'/o3-structured-editor.png',fullPage:true});const created=await post(page.getByRole('button',{name:'Run',exact:true}));let request;await expect.poll(async()=>{request=await get('reportRequest/'+created.uuid+'?v=full');return request.status;},{timeout:90000,intervals:[1000,2000]}).toBe('COMPLETED');
 expect(request.parameterMappings.runtimeColumns).toHaveLength(2);expect(request.parameterMappings.runtimeSort).toEqual(sorts);fs.writeFileSync(out+'/o3-run-request.json',JSON.stringify(request,null,2));
 const download=await context.request.get(rest+'downloadReport?reportRequestUuid='+created.uuid);const payload=await download.json();const csv=Buffer.from(payload.fileContent,'base64').toString('utf8');const expected=JSON.parse(fs.readFileSync(out+'/expected.json')).obs;expect(csv).toContain(expected[1]);expect(csv.indexOf(expected[1])).toBeLessThan(csv.indexOf(expected[0]));fs.writeFileSync(out+'/o3-run.csv',csv);
 await page.reload();const row=page.getByRole('row').filter({hasText:report.name}).first();await expect(row).toContainText('"name":"Object ID"');await expect(row).not.toContainText('[object Object]');ids.o3Request=created.uuid;persist();return{uuid:created.uuid,rows:2,typedMappings:true};
});
async function schedule(){await page.goto('/openmrs/spa/reports/scheduled-overview');const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:report.name,exact:true})});await row.getByRole('button',{name:'Edit',exact:true}).click();await expect(page.getByLabel('runtimeColumns',{exact:true})).toBeVisible();}
await proof('o3-schedule-save-reopen-unchanged-values',async()=>{
 await schedule();await page.locator('#scheduleType').selectOption('everyDay');await page.getByRole('textbox',{name:'hh:mm',exact:true}).fill('12:00');await fill();await page.getByLabel('Output format',{exact:true}).selectOption(ids.obsCsv);const created=await post(page.getByRole('button',{name:'Save',exact:true}));await expect(page.getByRole('button',{name:'Save',exact:true})).toBeHidden();
 await schedule();await expect(page.getByLabel('runtimeColumns',{exact:true})).not.toHaveValue('');expect(JSON.parse(await page.getByLabel('runtimeColumns',{exact:true}).inputValue())).toHaveLength(2);expect(JSON.parse(await page.getByLabel('runtimeSort',{exact:true}).inputValue())).toEqual(sorts);
 await page.getByLabel('runtimeSort',{exact:true}).fill('{}');await expect(page.getByRole('button',{name:'Save',exact:true})).toBeDisabled();await page.getByLabel('runtimeSort',{exact:true}).fill(JSON.stringify(sorts));const saved=await post(page.getByRole('button',{name:'Save',exact:true}));expect(saved.uuid).toBe(created.uuid);const request=await get('reportRequest/'+created.uuid+'?v=full');expect(request.parameterMappings.runtimeColumns).toHaveLength(2);expect(request.parameterMappings.runtimeSort).toEqual(sorts);fs.writeFileSync(out+'/o3-schedule-request.json',JSON.stringify(request,null,2));return{uuid:created.uuid,schedule:request.schedule};
});
await proof('o3-request-legacy-copy-rerun',async()=>{
 expect(ids.o3Request).toBeTruthy();await page.goto('/openmrs/module/reporting/run/runReport.form?copyRequest='+ids.o3Request);await expect(page.locator('[id^="object-editor-runtime-object-"][id$="columnDefinitions"] > fieldset')).toHaveCount(2);await page.locator('input[value="Request Report"]').click();await page.waitForURL('**/reportHistoryOpen.form?**');const uuid=new URL(page.url()).searchParams.get('uuid');ids.requests.push(uuid);persist();let request;await expect.poll(async()=>{request=await get('reportRequest/'+uuid+'?v=full');return request.status;},{timeout:90000,intervals:[1000,2000]}).toBe('COMPLETED');expect(request.parameterMappings.runtimeColumns).toHaveLength(2);return{uuid};
});
await proof('o3-webview-typed-parameters',async()=>{
 await page.goto('/openmrs/spa/reports/reports-data-overview');await page.getByLabel('Select Report',{exact:true}).selectOption(ids.obsReport);await fill();await page.getByRole('button',{name:'Fetch Report',exact:true}).click();await expect(page.getByRole('columnheader',{name:'Object ID',exact:true})).toBeVisible();const expected=JSON.parse(fs.readFileSync(out+'/expected.json')).obs;await expect(page.getByRole('cell',{name:expected[1],exact:true})).toBeVisible();return{rows:expected.length};
});
await browser.close();if(results.some(result=>result.status!=='pass'))process.exitCode=1;
