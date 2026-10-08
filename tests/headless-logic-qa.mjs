import { chromium, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const out = path.resolve(process.env.LOGIC_EVIDENCE || 'test-results/logic-qa');
fs.mkdirSync(out, { recursive: true });
const base = '/openmrs/module/logic/';
const prefix = `QA_LOGIC_REGRESSION_${Date.now()}`;
const owned = {}, checks = [], cleanup = [];
const db = sql => execFileSync('docker', ['exec', process.env.REFERENCE_DB_CONTAINER || 'openmrs-qa-db-1',
  'mariadb', '-uopenmrs', '-popenmrs', 'openmrs', '-N', '--raw', '-e', sql], { encoding: 'utf8' }).trim();
const patient = JSON.parse(db(`SELECT JSON_OBJECT('id',p.patient_id,'gender',n.gender,'birthdate',DATE_FORMAT(n.birthdate,'%m/%d/%Y'),'identifier',i.identifier)
 FROM patient p JOIN person n ON n.person_id=p.patient_id JOIN patient_identifier i ON i.patient_id=p.patient_id AND i.voided=0
 WHERE p.voided=0 ORDER BY p.patient_id,i.preferred DESC LIMIT 1`));
const snapshot = () => db('SELECT * FROM logic_token_registration ORDER BY token_registration_id');
const baseline = snapshot();
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL: process.env.REFERENCE_ORIGIN || 'http://localhost:8090', timezoneId: 'Asia/Kolkata', viewport: { width:1366,height:900 } });
const page = await context.newPage(); page.setDefaultTimeout(10000);
let csrf;
const go = route => page.goto(base + route);
const persist = () => fs.writeFileSync(path.join(out,'owned.json'), JSON.stringify(owned,null,2));
async function check(name, fn) {
  const result = {name};
  try { result.detail = await fn(); result.status = 'pass'; }
  catch(error) { result.status = 'fail'; result.error = error.message; }
  await page.screenshot({path:path.join(out,`${name}.png`),fullPage:true}).catch(()=>{});
  checks.push(result); fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(checks,null,2));
  console.log(result.status,name,result.error || '');
}
async function evaluate(expression, expected) {
  await go(`logic.form?patientId=${patient.id}`);
  await page.locator('#logicRuleField').fill(expression);
  await page.locator('input[type=submit]').click(); await page.waitForURL('**/run.form');
  await expect(page.locator('body')).toContainText(expected);
  return (await page.locator('body').innerText()).split('Test Results')[1].split('Run another test')[0].trim();
}
async function saveRule(language, content, name=prefix+'_rule') {
  await go('editRuleDefinition.form'+(owned.rule?'?id='+owned.rule:''));
  await page.locator('[name=name]').fill(name);
  await page.locator('[name=description]').fill('Owned literal & "quoted" <description>');
  await page.locator('[name=language]').selectOption(language);
  await page.locator('[name=ruleContent]').fill(content);
  await page.getByRole('button',{name:'Save',exact:true}).click();
  await page.waitForURL('**/manageRuleDefinitions.list');
  await page.getByRole('link',{name,exact:true}).click();
  owned.rule = new URL(page.url()).searchParams.get('id'); persist();
  await expect(page.locator('[name=ruleContent]')).toHaveValue(content);
  await expect(page.locator('[name=description]')).toHaveValue('Owned literal & "quoted" <description>');
}
async function deleteOwned(kind,id) {
  const route = kind==='rule'?'deleteRuleDefinition.form':'deleteToken.form';
  const response = await context.request.post(base+route,{form:{id:String(id),'OWASP-CSRFTOKEN':csrf}});
  cleanup.push({kind,id,status:response.status()});
}
try {
  await page.goto('/openmrs/login.htm'); await page.locator('#username').fill('admin');
  await page.locator('#password').fill('Admin123'); await page.locator('input[type=submit]').click();
  await go('editTokenRegistration.form'); csrf=await page.locator('[name="OWASP-CSRFTOKEN"]').first().inputValue();
  await check('admin-pages',async()=>{
    for(const route of ['manageTokens.list','manageRuleDefinitions.list','logic.form','init.form']) {
      const response=await go(route); expect(response.status()).toBe(200);
      await expect(page.locator('body')).not.toContainText('HTTP Status 500');
    }
  });
  await check('token-list-search-pagination',async()=>{
    const errors=[]; const errorListener=error=>errors.push(error.message);page.on('pageerror',errorListener);
    await go('manageTokens.list');await expect(page.locator('.datatable > tbody > tr')).toHaveCount(20);
    await page.locator('.dataTables_filter input').fill('AGE');await page.locator('.dataTables_filter input').press('End');
    await expect(page.getByRole('link',{name:'AGE',exact:true})).toBeVisible();
    expect(errors).toEqual([]);page.off('pageerror',errorListener);
    const result=await context.request.get(base+'listTokensQuery.form?iDisplayLength=10000&iDisplayStart=0&sEcho=1');
    const data=await result.json();expect(data.aaData.length).toBeLessThanOrEqual(100);return{total:data.iTotalRecords};
  });
  await check('token-required-validation',async()=>{
    await go('editTokenRegistration.form');await page.getByRole('button',{name:'Save',exact:true}).click();
    await expect(page.locator('span.error').first()).toBeVisible();
  });
  await check('token-create-reload',async()=>{
    await go('manageTokens.list');await page.getByRole('link',{name:'Add New Token',exact:true}).click();
    for(const [field,value] of Object.entries({token:prefix+'_alias',providerClassName:'org.openmrs.logic.datasource.PersonDataSource',configuration:'gender',providerToken:prefix+'_alias'}))
      await page.locator(`[name=${field}]`).fill(value);
    await page.getByRole('button',{name:'Save',exact:true}).click();await page.waitForURL('**/manageTokens.list');
    await page.locator('.dataTables_filter input').fill(prefix);await page.locator('.dataTables_filter input').press('End');
    await page.getByRole('link',{name:prefix+'_alias',exact:true}).click();owned.token=new URL(page.url()).searchParams.get('id');persist();
    await expect(page.locator('[name=configuration]')).toHaveValue('gender');
  });
  await check('token-alias-evaluation',()=>evaluate('"'+prefix+'_alias"',': '+patient.gender));
  await check('token-update-and-rename',async()=>{
    await go('editTokenRegistration.form?id='+owned.token);
    await page.locator('[name=token]').fill(prefix+'_renamed');await page.locator('[name=configuration]').fill('birthdate');
    await page.getByRole('button',{name:'Save',exact:true}).click();await page.waitForURL('**/manageTokens.list');
    await evaluate('"'+prefix+'_renamed"',patient.birthdate);
    const r=await context.request.get(base+'listTokensQuery.form?sSearch='+prefix+'_alias&iDisplayLength=20&iDisplayStart=0');
    expect((await r.json()).aaData).toHaveLength(0);
    await evaluate('"'+prefix+'_alias"','Invalid Logic Rule.');
  });
  await check('rule-required-validation',async()=>{
    await go('editRuleDefinition.form');await page.getByRole('button',{name:'Save',exact:true}).click();
    await expect(page.locator('span.error').first()).toBeVisible();
  });
  await check('groovy-literal-source-roundtrip',async()=>{
    await saveRule('Groovy','return new Result("A & B <value> &amp;");');
    await page.reload();await expect(page.locator('[name=ruleContent]')).toHaveValue('return new Result("A & B <value> &amp;");');
    return evaluate('"'+prefix+'_rule"','A & B <value> &amp;');
  });
  await check('groovy-update-and-rename',async()=>{
    await saveRule('Groovy','return new Result(42);',prefix+'_renamed_rule');
    await evaluate('"'+prefix+'_renamed_rule"',': 42.0');
    const r=await context.request.get(base+'listTokensQuery.form?sSearch='+prefix+'_rule&iDisplayLength=20&iDisplayStart=0');
    expect((await r.json()).aaData).toHaveLength(0);
  });
  await check('java-rule-create-and-update',async()=>{
    for(const value of [7,8]) {
      const source=`package org.openmrs.module.logic.rule; public class CompiledRule${owned.rule} extends org.openmrs.logic.rule.AbstractRule { public org.openmrs.logic.result.Result eval(org.openmrs.logic.LogicContext context,Integer patientId,java.util.Map<String,Object> parameters) { return new org.openmrs.logic.result.Result(${value}); }}`;
      await saveRule('Java',source,prefix+'_renamed_rule');await evaluate('"'+prefix+'_renamed_rule"',': '+value+'.0');
    }
  });
  await check('invalid-expression',async()=>{
    await evaluate("'AGE'",'Invalid Logic Rule.');await expect(page.locator('body')).not.toContainText('NullPointerException');
    const r=await context.request.post(base+'run.form',{form:{logicRule:'"AGE"','OWASP-CSRFTOKEN':csrf}});
    expect(r.status()).toBe(200);expect(await r.text()).toContain('Invalid parameters');
  });
  await check('patient-selection-reset',async()=>{
    await go('logic.form?patientId='+patient.id);await page.getByRole('link',{name:/Choose.*Patient/i}).click();
    await expect(page.locator('#patientIdField')).toHaveValue('0');await page.locator('#logicRuleField').fill('"AGE"');
    await page.locator('input[type=submit]').click();await expect(page.locator('#patientError')).toBeVisible();
    await page.locator('#pSearch').pressSequentially(patient.identifier,{delay:100});
    await page.getByText(patient.identifier,{exact:true}).last().click();
    await expect(page.locator('#patientIdField')).toHaveValue(String(patient.id));
  });
  await check('token-autocomplete',async()=>{
    await go('logic.form?patientId='+patient.id);await page.locator('#logicRuleField').fill('');await page.locator('#logicRuleField').pressSequentially('AG',{delay:100});
    await expect(page.locator('.ac_results li').filter({hasText:/^AGE$/})).toBeVisible();
  });
  await check('setup-property-cancel-and-network-error',async()=>{
    await go('init.form');const field=page.locator('input[id^="gp_"]');const original=await field.inputValue();
    await field.fill('Test');await field.press('End');await page.getByRole('button',{name:'Cancel',exact:true}).click();await expect(field).toHaveValue(original);
    await context.setOffline(true);await page.locator('#runnow').click();await expect(page.locator('#statusText')).toContainText('Could not initialize');await context.setOffline(false);
  });
  await check('rule-delete-removes-token',async()=>{
    await go('editRuleDefinition.form?id='+owned.rule);await page.getByRole('button',{name:'Delete this Rule',exact:true}).click();await page.waitForURL('**/manageRuleDefinitions.list');
    const r=await context.request.get(base+'listTokensQuery.form?sSearch='+prefix+'_renamed_rule&iDisplayLength=20&iDisplayStart=0');expect((await r.json()).aaData).toHaveLength(0);
  });
  await check('token-delete',async()=>{
    await go('editTokenRegistration.form?id='+owned.token);await page.getByRole('button',{name:'Delete this Token',exact:true}).click();await page.waitForURL('**/manageTokens.list');
    await page.locator('.dataTables_filter input').fill(prefix);await page.locator('.dataTables_filter input').press('End');await expect(page.locator('.dataTables_empty')).toBeVisible();
  });
  await check('setup-initialize-local-providers',async()=>{
    const before=db('SELECT token_registration_id FROM logic_token_registration ORDER BY token_registration_id').split('\n');
    const beforeRows=snapshot();
    const property=db("SELECT COALESCE(property_value,'<NULL>') FROM global_property WHERE property='logic.defaultTokens.conceptClasses'");
    try {
      await go('init.form');await page.locator('#runnow').click();
      await expect(page.locator('#statusText')).toContainText('Registration complete.',{timeout:30000});
      expect(db('SELECT * FROM logic_token_registration WHERE token_registration_id IN ('+before.join(',')+') ORDER BY token_registration_id')).toBe(beforeRows);
      expect(db("SELECT COALESCE(property_value,'<NULL>') FROM global_property WHERE property='logic.defaultTokens.conceptClasses'")).toBe(property);
      await evaluate('"HIV POSITIVE"','Results for');
      await expect(page.locator('body')).not.toContainText('Error:');
    } finally {
      const added=db('SELECT token_registration_id FROM logic_token_registration WHERE token_registration_id NOT IN ('+before.join(',')+')');
      for(const id of added.split('\n').filter(Boolean))await deleteOwned('token',id);
      expect(snapshot()).toBe(beforeRows);
    }
  });

} finally {
  await context.setOffline(false);
  const rules=db(`SELECT id FROM logic_rule_definition WHERE name LIKE '${prefix}%'`);
  for(const id of rules.split('\n').filter(Boolean))await deleteOwned('rule',id);
  const tokens=db(`SELECT token_registration_id FROM logic_token_registration WHERE token LIKE '${prefix}%'`);
  for(const id of tokens.split('\n').filter(Boolean))await deleteOwned('token',id);
  if(owned.rule)for(const [dir,extension]of[['sources','java'],['class','class']]){
    const generated=path.resolve('.native/data/logic',dir,`org/openmrs/module/logic/rule/CompiledRule${owned.rule}.${extension}`);
    if(fs.existsSync(generated))fs.unlinkSync(generated);
  }
  fs.writeFileSync(path.join(out,'cleanup.json'),JSON.stringify({actions:cleanup,baselineUnchanged:snapshot()===baseline},null,2));
  await browser.close();
}
if(checks.some(c=>c.status!=='pass'))process.exitCode=1;
