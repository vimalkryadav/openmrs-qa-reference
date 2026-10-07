import {chromium,expect} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const target=process.argv[2];if(!['reference','clone'].includes(target))throw Error('Use reference or clone');
const surface=process.argv[3]??'o3';if(!['o3','owa'].includes(surface))throw Error('Use o3 or owa');
const root=path.resolve(path.dirname(new URL(import.meta.url).pathname),'..'),out=path.join(root,surface==='owa'?'test-results/ocl-configuration-owa':'test-results/ocl-configuration',target);
const origin=target==='reference'?'http://localhost:8090':'http://localhost:8093';
const api=origin+(target==='reference'?'/openmrs':'/api/openmrs')+'/ws/rest/v1/openconceptlab/';
const state=action=>execFileSync(path.join(root,'.venv/bin/python'),[path.join(root,'tests/ocl-configuration-state.py'),target,action,surface],{encoding:'utf8'});
state('snapshot');
const browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();
page.setDefaultTimeout(20000);const results=[],external=[],errors=[];let writes=0;
await context.route('**/*',route=>{const u=new URL(route.request().url());if(['http:','https:'].includes(u.protocol)&&!['localhost','127.0.0.1'].includes(u.hostname)){external.push(u.origin+u.pathname);return route.abort();}return route.continue();});
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/openconceptlab/subscription'))writes++;});
const subscription=async()=>{const r=await context.request.get(api+'subscription?v=full');expect(r.ok(),await r.text()).toBeTruthy();return(await r.json()).results[0];};
async function proof(id,name,fn){try{await fn();results.push({id,name,status:'passed'});console.log('PASS',id,name);}catch(error){results.push({id,name,status:'failed',message:error.message});await page.screenshot({path:path.join(out,id+'-failure.png'),fullPage:true});throw error;}}
async function save(){const pending=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().includes('/openconceptlab/subscription'));await page.getByRole('button',{name:'Save changes',exact:true}).click();const r=await pending;expect(r.ok(),await r.text()).toBeTruthy();}
const snapshotLabel='Subscribe to SNAPSHOT versions (not recommended)',validationLabel='Disable validation (should be used with care for well curated collections or sources)';
const url=()=>page.getByLabel('Subscription URL',{exact:true}),token=()=>page.getByLabel('Token',{exact:true});
const home=origin+'/openmrs/owa/openconceptlab/index.html#/',settings=home+'subscription';
const owaUrl=()=>page.locator('#subscription-url'),owaToken=()=>page.locator('#subscription-token');
async function owaOpen(){await page.goto(settings);await expect(owaUrl()).toBeVisible();}
async function owaAdvanced(){await page.getByText('Show/hide advanced...',{exact:true}).click();}
async function owaProof(){
 await owaOpen();
 await proof('OWA-C01','Required fields block saving and corrected configuration survives reload',async()=>{
  const count=writes;await expect(page.getByRole('button',{name:'Save changes',exact:true})).toBeDisabled();
  await owaUrl().fill('https://example.invalid/orgs/QAConfig/collections/Offline');await expect(page.getByRole('button',{name:'Save changes',exact:true})).toBeDisabled();expect(writes).toBe(count);
  await owaToken().fill('qa-offline-config-proof');await save();await owaOpen();await page.reload();await expect(owaUrl()).toHaveValue('https://api.example.invalid/orgs/QAConfig/collections/Offline');
 });
 await proof('OWA-C02','Cancel navigates home without persisting unsaved edits',async()=>{
  const before=await subscription(),count=writes;await owaUrl().fill('https://example.invalid/orgs/QAConfig/collections/Cancelled');await owaToken().fill('qa-offline-config-proof-cancel');await owaAdvanced();await page.locator('input[type=checkbox]').nth(0).check();await page.getByRole('button',{name:'Cancel Changes',exact:true}).click();await expect(page.getByText('Previous imports',{exact:true})).toBeVisible();await owaOpen();await expect(owaUrl()).toHaveValue(before.url);await expect(owaToken()).toHaveValue(before.token);expect(writes).toBe(count);expect(await subscription()).toEqual(before);
 });
 await proof('OWA-C03','Edited values and advanced flags persist after reload',async()=>{
  await owaUrl().fill('https://example.invalid/orgs/QAConfig/collections/Edited');await owaToken().fill('qa-offline-config-proof-edited');await owaAdvanced();await page.locator('input[type=checkbox]').nth(0).check();await page.locator('input[type=checkbox]').nth(1).check();await save();await owaOpen();await page.reload();await expect(owaUrl()).toHaveValue('https://api.example.invalid/orgs/QAConfig/collections/Edited');await expect(owaToken()).toHaveValue('qa-offline-config-proof-edited');await owaAdvanced();await expect(page.locator('input[type=checkbox]').nth(0)).toBeChecked();await expect(page.locator('input[type=checkbox]').nth(1)).toBeChecked();const stored=await subscription();expect(stored.subscribedToSnapshot).toBe(true);expect(stored.validationType).toBe('NONE');await fs.promises.writeFile(path.join(out,'configuration-saved.html'),await page.content());await fs.promises.writeFile(path.join(out,'configuration-saved.aria.yml'),await page.locator('body').ariaSnapshot());
 });
 await proof('OWA-C04','Saved subscription leaves remote disabled and local upload/history available',async()=>{
  const history=await(await context.request.get(api+'import?v=full')).json();await page.goto(home);await expect(page.getByRole('button',{name:'Import from subscription server',exact:true})).toBeDisabled();await expect(page.locator('input[type=file]')).toBeEnabled();await expect(page.getByText('Previous imports',{exact:true})).toBeVisible();const rejected=await context.request.post(api+'import',{data:{}});expect(rejected.status()).toBe(400);expect(await rejected.text()).toContain('Remote OCL imports are disabled');expect(await(await context.request.get(api+'import?v=full')).json()).toEqual(history);
 });
 await proof('OWA-C06','Malformed URL rejects without persistence and can be corrected',async()=>{
  await owaOpen();const before=await subscription();await owaUrl().fill('/QAConfig/not-a-url');const pending=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().includes('/openconceptlab/subscription'),{timeout:2500}).catch(()=>null);await page.getByRole('button',{name:'Save changes',exact:true}).click({timeout:2500}).catch(()=>{});const response=await pending;const after=await subscription();const rejected=after.url===before.url&&(!response||response.status()>=400&&response.status()<500);await fs.promises.writeFile(path.join(out,'invalid-url.json'),JSON.stringify({status:response?.status()??null,storedUrl:after.url,rejected},null,2));await expect(owaUrl()).toHaveValue('/QAConfig/not-a-url');await expect(page.getByText(/Wrong url address/).last()).toBeVisible();await fs.promises.writeFile(path.join(out,'invalid-url.aria.yml'),await page.locator('body').ariaSnapshot());await owaUrl().fill(before.url);await save();await owaOpen();await expect(owaUrl()).toHaveValue(before.url);expect(rejected,'Malformed URL must be rejected while retaining the saved URL').toBe(true);
 });
}
try{
 if(target==='reference'){await page.goto(origin+'/openmrs/login.htm');await page.locator('#username').fill('admin');await page.locator('#password').fill('Admin123');await page.locator('input[type=submit]').click();}
 if(surface==='owa')await owaProof();else {
 await page.goto(origin+'/openmrs/spa/ocl');await expect(url()).toBeVisible({timeout:60000});
 await proof('OCL-C01','Required and malformed fields reject without writes, then recover',async()=>{
  const count=writes;await page.getByRole('button',{name:'Save changes',exact:true}).click();expect(await url().evaluate(e=>e.validity.valid)).toBe(false);expect(writes).toBe(count);
  await url().fill('https://example.invalid/orgs/QAConfig/collections/Offline');await page.getByRole('button',{name:'Save changes',exact:true}).click();expect(await token().evaluate(e=>e.validity.valid)).toBe(false);expect(writes).toBe(count);
  await url().fill('not a URL');await token().fill('qa-offline-config-proof');await page.getByRole('button',{name:'Save changes',exact:true}).click();expect(await url().evaluate(e=>e.validity.valid)).toBe(false);expect(writes).toBe(count);await expect(url()).toHaveValue('not a URL');
  await url().fill('https://example.invalid/orgs/QAConfig/collections/Offline');await save();await page.reload();await expect(url()).toHaveValue('https://api.example.invalid/orgs/QAConfig/collections/Offline');expect((await subscription()).validationType).toBe('FULL');
 });
 await proof('OCL-C02','Cancel edits preserves stored values and resets inputs',async()=>{
  const before=await subscription(),count=writes;await url().fill('https://example.invalid/orgs/QAConfig/collections/Cancelled');await token().fill('qa-offline-config-proof-cancel');await page.getByText(snapshotLabel,{exact:true}).click();await page.getByRole('button',{name:'Cancel changes',exact:true}).click();await expect(url()).toHaveValue(before.url);await expect(token()).toHaveValue(before.token);expect(writes).toBe(count);expect(await subscription()).toEqual(before);
 });
 await proof('OCL-C03','Edit URL token and validation flags survives hard reload',async()=>{
  await url().fill('https://example.invalid/orgs/QAConfig/collections/Edited');await token().fill('qa-offline-config-proof-edited');await page.getByText(snapshotLabel,{exact:true}).click();await page.getByText(validationLabel,{exact:true}).click();await save();await page.reload();await expect(url()).toHaveValue('https://api.example.invalid/orgs/QAConfig/collections/Edited');await expect(token()).toHaveValue('qa-offline-config-proof-edited');await expect(page.getByRole('checkbox',{name:snapshotLabel})).toBeChecked();await expect(page.getByRole('checkbox',{name:validationLabel})).toBeChecked();const saved=await subscription();expect(saved.validationType).toBe('NONE');expect(saved.subscribedToSnapshot).toBe(true);await fs.promises.writeFile(path.join(out,'configuration-saved.html'),await page.content());await fs.promises.writeFile(path.join(out,'configuration-saved.aria.yml'),await page.locator('body').ariaSnapshot());await page.screenshot({path:path.join(out,'configuration-saved.png'),fullPage:true});
 });
 await proof('OCL-C04','Offline policy stays active with a saved subscription',async()=>{
  const history=await(await context.request.get(api+'import?v=full')).json();await page.getByRole('tab',{name:'Import',exact:true}).click();await expect(page.getByRole('button',{name:'Import from Subscription',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Import from file',exact:true})).toBeEnabled();const rejected=await context.request.post(api+'import',{data:{}});expect(rejected.status()).toBe(400);expect(await rejected.text()).toContain('Remote OCL imports are disabled');expect(await(await context.request.get(api+'import?v=full')).json()).toEqual(history);
 });
 }
}catch(error){console.error(error);process.exitCode=1;}
finally{
 try{state('restore');if(surface==='owa'){await owaOpen();await page.reload();await expect(owaUrl()).toHaveValue('');await page.goto(home);await expect(page.locator('input[type=file]')).toBeEnabled();await expect(page.getByRole('button',{name:'Import from subscription server',exact:true})).toBeDisabled();await expect(page.getByText('Previous imports',{exact:true})).toBeVisible();}else {await page.goto(origin+'/openmrs/spa/ocl');await expect(url()).toHaveValue('');await page.getByRole('tab',{name:'Import',exact:true}).click();await expect(page.getByRole('button',{name:'Import from file',exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:'Import from Subscription',exact:true})).toBeDisabled();await page.getByRole('tab',{name:'Previous Imports',exact:true}).click();await expect(page.getByText('Date and Time',{exact:true})).toBeVisible();}state('check');expect(external).toEqual([]);expect(errors).toEqual([]);results.push({id:surface==='owa'?'OWA-C05':'OCL-C05',name:'Exact settings restored; local upload/history and no outbound activity verified',status:'passed'});}catch(error){results.push({id:surface==='owa'?'OWA-C05':'OCL-C05',status:'failed',message:error.message});process.exitCode=1;}
 await fs.promises.writeFile(path.join(out,'configuration-results.json'),JSON.stringify({target,headless:true,results,externalRequests:external,pageErrors:errors},null,2));await browser.close();
}
