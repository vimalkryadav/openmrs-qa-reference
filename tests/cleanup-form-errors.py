"""Remove exact R6 fixtures after the UI correction proof."""
import os
from pathlib import Path
import json
import setup
setup.ROOT=Path(__file__).resolve().parents[1]/os.environ.get('REPORTS_EVIDENCE','test-results/form-errors')
s=setup.Site('ref',8090)
s.post('module/reporting/reports/deleteReportProcessor.form',{'uuid':s.ids['processor']})
for key,kind in [('dimension','indicator.dimension.CohortDefinitionDimension'),('logic','dataset.definition.LogicDataSetDefinition'),('cohort','cohort.definition.SqlCohortDefinition')]:
    name=setup.read_reference_sql("SELECT name FROM serialized_object WHERE uuid='"+s.ids[key]+"'").strip()
    assert name.startswith('QA Reports Fix R6 20261008'),name
    response=s.get('module/reporting/definition/purgeDefinition.form',params={'uuid':s.ids[key],'type':'org.openmrs.module.reporting.'+kind})
    assert response.status_code in(200,302),response.text
counts={}
for table,keys in [('serialized_object',['dimension','logic','cohort']),('reporting_report_processor',['processor'])]:
    counts[table]=int(setup.read_reference_sql('SELECT COUNT(*) FROM '+table+' WHERE uuid IN ('+','.join("'"+s.ids[k]+"'" for k in keys)+')'))
(setup.ROOT/'cleanup-results.json').write_text(json.dumps({'ids':s.ids,'residualCounts':counts},indent=2))
assert not any(counts.values()),counts
print(counts)
