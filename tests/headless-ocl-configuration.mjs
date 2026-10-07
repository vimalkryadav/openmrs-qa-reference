import {chromium,expect} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
const target=process.argv[2];if(!['reference','clone'].includes(target))throw Error('Use reference or clone');
const root=path.resolve(path.dirname(new URL(import.meta.url).pathname),'..'),out=path.join(root,'test-results/ocl-configuration',target);
const origin=target==='reference'?'http://localhost:8090':'http://localhost:8093';
const api=origin+(target==='reference'?'/openmrs':'/api/openmrs')+'/ws/rest/v1/openconceptlab/';
const state=action=>execFileSync(path.join(root,'.venv/bin/python'),[path.join(root,'tests/ocl-configuration-state.py'),target,action],{encoding:'utf8'});
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
try{
 if(target==='reference'){await page.goto(origin+'/openmrs/login.htm');await page.locator('#username').fill('admin');await page.locator('#password').fill('Admin123');await page.locator('input[type=submit]').click();}
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
}catch(error){console.error(error);process.exitCode=1;}
finally{
 try{state('restore');await page.goto(origin+'/openmrs/spa/ocl');await expect(url()).toHaveValue('');await page.getByRole('tab',{name:'Import',exact:true}).click();await expect(page.getByRole('button',{name:'Import from file',exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:'Import from Subscription',exact:true})).toBeDisabled();await page.getByRole('tab',{name:'Previous Imports',exact:true}).click();await expect(page.getByText('Date and Time',{exact:true})).toBeVisible();state('check');expect(external).toEqual([]);expect(errors).toEqual([]);results.push({id:'OCL-C05',name:'Exact settings restored; local upload/history and no outbound activity verified',status:'passed'});}catch(error){results.push({id:'OCL-C05',status:'failed',message:error.message});process.exitCode=1;}
 await fs.promises.writeFile(path.join(out,'configuration-results.json'),JSON.stringify({target,headless:true,results,externalRequests:external,pageErrors:errors},null,2));await browser.close();
}
