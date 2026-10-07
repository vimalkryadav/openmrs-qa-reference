"""Purge only exact R5 fixture UUIDs, then verify the ledger is empty."""
from pathlib import Path
import json
import setup
setup.ROOT=Path(__file__).resolve().parents[1]/'test-results/live-update'
s=setup.Site('ref',8090)
for uuid in s.ids.get('requests',[]):
    response=s.c.delete(s.path('ws/rest/v1/reportingrest/reportRequest/'+uuid))
    assert response.status_code in (200,204,404),response.text
for key in ['sql','iterable']:
    s.post('module/reporting/reports/deleteReportDesign.form',{'uuid':s.ids[key+'Design']})
    for obj,kind in [(key+'Report','report.definition.ReportDefinition'),(key,'dataset.definition.'+('Sql' if key=='sql' else 'IterableSql')+'DataSetDefinition')]:
        name=setup.read_reference_sql("SELECT name FROM serialized_object WHERE uuid='"+s.ids[obj]+"'").strip()
        assert name.startswith('QA Reports Fix R5 20261008'),name
        response=s.get('module/reporting/definition/purgeDefinition.form',params={'uuid':s.ids[obj],'type':'org.openmrs.module.reporting.'+kind})
        assert response.status_code in (200,302),response.text
counts={}
for table,keys in [('serialized_object',['sql','iterable','sqlReport','iterableReport']),('reporting_report_design',['sqlDesign','iterableDesign']),('reporting_report_request',[])]:
    uuids=[s.ids[k] for k in keys] if keys else s.ids.get('requests',[])
    if uuids:counts[table]=int(setup.read_reference_sql('SELECT COUNT(*) FROM '+table+' WHERE uuid IN ('+','.join("'"+u+"'" for u in uuids)+')'))
(setup.ROOT/'cleanup-results.json').write_text(json.dumps({'ids':s.ids,'residualCounts':counts},indent=2))
assert not any(counts.values()),counts
print(counts)
