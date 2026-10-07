"""Remove the exact editor-suite fixtures and verify dependency rows are gone."""
import json
import re
from pathlib import Path
import setup
setup.ROOT = Path(__file__).resolve().parents[1] / 'test-results/editors'
s = setup.Site('ref', 8090)
prefix = 'QA Reports Fix R2 20261008'
report = s.ids['report']
assert re.fullmatch(r'[a-f0-9-]{36}', report)
resources = setup.read_reference_sql("SELECT r.uuid FROM reporting_report_design_resource r JOIN reporting_report_design d ON d.id=r.report_design_id WHERE d.report_definition_uuid='"+report+"'").splitlines()
designs = s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':report,'v':'full'},headers={'Accept':'application/json'}).json()['results']
for request in s.ids.get('requests', []):
    if s.get('ws/rest/v1/reportingrest/reportRequest/'+request,headers={'Accept':'application/json'}).status_code != 404:
        r=s.c.delete(s.path('ws/rest/v1/reportingrest/reportRequest/'+request)); assert r.status_code in (200,204),r.status_code
for design in designs:
    assert design['name'].startswith(prefix)
    s.post('module/reporting/reports/deleteReportDesign.form',{'uuid':design['uuid']})
keys = [('report','report.definition.ReportDefinition'),('patientDataset','dataset.definition.PatientDataSetDefinition'),('indicatorDataset','dataset.definition.CohortIndicatorAndDimensionDataSetDefinition'),('multi','dataset.definition.MultiParameterDataSetDefinition'),('indicator','indicator.CohortIndicator'),('sql','dataset.definition.SqlDataSetDefinition'),('gender','data.person.definition.GenderDataDefinition'),('cohort','cohort.definition.SqlCohortDefinition'),('staticQuery','cohort.definition.StaticCohortDefinition')]
for key,kind in keys:
    if key not in s.ids: continue
    uuid=s.ids[key]; assert re.fullmatch(r'[a-f0-9-]{36}',uuid)
    rows=setup.read_reference_sql("SELECT name FROM serialized_object WHERE uuid='"+uuid+"'").splitlines()
    if rows:
        assert rows[0].startswith(prefix), rows
        r=s.get('module/reporting/definition/purgeDefinition.form',params={'uuid':uuid,'type':'org.openmrs.module.reporting.'+kind});assert r.status_code in (200,302)
ledger = {'definitions':[s.ids[key] for key,_ in keys if key in s.ids],'designs':[d['uuid']for d in designs],'resources':resources,'requests':s.ids.get('requests',[])}
queries=[]
for table,section in [('serialized_object','definitions'),('reporting_report_design','designs'),('reporting_report_design_resource','resources'),('reporting_report_request','requests')]:
    uuids=ledger[section]
    if uuids:
        assert all(re.fullmatch(r'[a-f0-9-]{36}',u) for u in uuids)
        queries.append("SELECT '"+table+"',COUNT(*) FROM "+table+" WHERE uuid IN ("+','.join("'"+u+"'"for u in uuids)+")")
queries += [f"SELECT '{t}-prefix',COUNT(*) FROM {t} WHERE name LIKE '{prefix}%'"for t in ['serialized_object','reporting_report_design']]
ledger['residualCounts']=dict(line.split('\t')for line in setup.read_reference_sql(';'.join(queries)).splitlines())
(setup.ROOT/'cleanup-results.json').write_text(json.dumps(ledger,indent=2));assert all(x=='0'for x in ledger['residualCounts'].values()),ledger
print(ledger['residualCounts'])
