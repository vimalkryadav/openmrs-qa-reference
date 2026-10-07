"""Delete only UUIDs captured by this suite; never remove reference data or volumes."""
from setup import Site, ROOT
import json
s = Site('ref', 8090)
results = []
for key in ['run', 'schedule']:
    if key in s.ids:
        response = s.c.delete(s.path('ws/rest/v1/reportingrest/reportRequest/' + s.ids[key]))
        results.append([key, response.status_code])
if 'csvDesign' in s.ids:
    s.post('module/reporting/reports/deleteReportDesign.form', {'uuid': s.ids['csvDesign']})
for key, kind in [('row','report.definition.ReportDefinition'), ('period','report.definition.PeriodIndicatorReportDefinition'), ('dataset','dataset.definition.LogicDataSetDefinition'), ('cohort','cohort.definition.SqlCohortDefinition'), ('converted','data.person.definition.ConvertedPersonDataDefinition'), ('source','data.person.definition.GenderDataDefinition')]:
    if key in s.ids:
        response = s.get('module/reporting/definition/purgeDefinition.form', params={'uuid':s.ids[key], 'type':'org.openmrs.module.reporting.'+kind})
        results.append([key, response.status_code])
(ROOT / 'cleanup-results.json').write_text(json.dumps(results, indent=2))
print(results)

(ROOT / 'ref-ids.json').unlink(missing_ok=True)
