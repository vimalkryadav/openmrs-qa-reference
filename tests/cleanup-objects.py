"""Delete only captured R3 request/design/definition UUIDs and verify no residuals."""
import os
import json
import re
from pathlib import Path
import setup
setup.ROOT=Path(__file__).resolve().parents[1]/os.environ.get('REPORTS_OBJECT_EVIDENCE',os.environ.get('REPORTS_EVIDENCE','test-results/objects'))
s=setup.Site('ref',8090);prefix=os.environ.get('REPORTS_OBJECT_PREFIX','QA Reports Fix R3 20261008')
kinds={'obsReport':'report.definition.ReportDefinition','visitReport':'report.definition.ReportDefinition','encounterReport':'report.definition.ReportDefinition','metadataReport':'report.definition.ReportDefinition','obs':'dataset.definition.ObsDataSetDefinition','visit':'dataset.definition.VisitDataSetDefinition','encounter':'dataset.definition.EncounterAndObsDataSetDefinition','scalar':'dataset.definition.SqlDataSetDefinition','obsFilter':'query.obs.definition.SqlObsQuery','visitFilter':'query.visit.definition.AllVisitQuery','visitTypedFilter':'query.visit.definition.BasicVisitQuery','encounterFilter':'query.encounter.definition.SqlEncounterQuery','obsId':'data.obs.definition.ObsIdDataDefinition','visitId':'data.visit.definition.VisitIdDataDefinition','encounterId':'data.encounter.definition.EncounterIdDataDefinition','age':'data.person.definition.AgeDataDefinition','gender':'data.person.definition.GenderDataDefinition','cohort':'cohort.definition.SqlCohortDefinition'}
ledger={'definitions':[],'designs':[],'resources':[],'requests':s.ids.get('requests',[])}
def valid(uuid):
    assert re.fullmatch(r'[a-f0-9-]{36}',uuid),uuid
    return uuid
for uuid in ledger['requests']:
    valid(uuid)
    if s.get('ws/rest/v1/reportingrest/reportRequest/'+uuid,headers={'Accept':'application/json'}).status_code!=404:
        response=s.c.delete(s.path('ws/rest/v1/reportingrest/reportRequest/'+uuid));assert response.status_code in(200,204),response.text
for key in ['obsReport','visitReport','encounterReport','metadataReport']:
    uuid=valid(s.ids[key])
    designs=s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':uuid,'v':'full'},headers={'Accept':'application/json'}).json()['results']
    for design in designs:
        assert design['name'].startswith(prefix);duuid=valid(design['uuid']);ledger['designs'].append(duuid)
        ledger['resources']+=setup.read_reference_sql("SELECT r.uuid FROM reporting_report_design_resource r JOIN reporting_report_design d ON d.id=r.report_design_id WHERE d.uuid='"+duuid+"'").splitlines()
        s.post('module/reporting/reports/deleteReportDesign.form',{'uuid':duuid})
for key,kind in kinds.items():
    uuid=valid(s.ids[key]);ledger['definitions'].append(uuid)
    names=setup.read_reference_sql("SELECT name FROM serialized_object WHERE uuid='"+uuid+"'").splitlines()
    if names:
        assert names[0].startswith(prefix),names
        response=s.get('module/reporting/definition/purgeDefinition.form',params={'uuid':uuid,'type':'org.openmrs.module.reporting.'+kind});assert response.status_code in(200,302),response.text
queries=[]
for table,key in [('serialized_object','definitions'),('reporting_report_design','designs'),('reporting_report_design_resource','resources'),('reporting_report_request','requests')]:
    uuids=[valid(x) for x in ledger[key]]
    if uuids:queries.append("SELECT '"+table+"',COUNT(*) FROM "+table+" WHERE uuid IN ("+','.join("'"+x+"'" for x in uuids)+")")
queries += [f"SELECT '{t}-prefix',COUNT(*) FROM {t} WHERE name LIKE '{prefix}%'"for t in ['serialized_object','reporting_report_design']]
ledger['residualCounts']=dict(line.split('\t')for line in setup.read_reference_sql(';'.join(queries)).splitlines())
(setup.ROOT/'cleanup-results.json').write_text(json.dumps(ledger,indent=2));assert all(x=='0'for x in ledger['residualCounts'].values()),ledger
print(ledger['residualCounts'])
