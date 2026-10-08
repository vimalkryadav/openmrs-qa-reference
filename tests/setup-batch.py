"""Create only owned, bounded reporting metadata for the native regression browser suite."""
import os
import json
import re
from pathlib import Path
import setup

setup.ROOT = Path(__file__).resolve().parents[1] / os.environ.get('REPORTS_EVIDENCE','test-results/batch-one')
setup.ROOT.mkdir(parents=True, exist_ok=True)
setup.PREFIX = 'QA Reports Fix R1 20261008'
s = setup.Site('ref', 8090)
report = s.create('report', 'report.definition.ReportDefinition', 'reports/reportEditor.form')
ds = s.create('dataset', 'dataset.definition.IterableSqlDataSetDefinition', 'datasets/iterableSqlDataSetEditor.form')
s.post('module/reporting/datasets/iterableSqlDataSetDefinitionAssignQueryString.form',
       {'uuid': ds, 'queryString': 'SELECT 7 AS qa_value UNION ALL SELECT 9 AS qa_value'})
s.post('module/reporting/reports/saveMappedProperty.form',
       {'uuid': report, 'type': 'org.openmrs.module.reporting.report.definition.ReportDefinition',
        'property': 'dataSetDefinitions', 'newKey': 'result', 'currentKey': '', 'mappedUuid': ds})
# Text template needs a reusable ordinary SQL dataset: iterable datasets have a single-use cursor.
text_report = s.create('textReport', 'report.definition.ReportDefinition', 'reports/reportEditor.form')
text_ds = s.create('textDataset', 'dataset.definition.SqlDataSetDefinition', 'datasets/sqlDataSetEditor.form')
s.post('module/reporting/datasets/sqlDataSetDefinitionAssignQueryString.form',
       {'uuid': text_ds, 'queryString': 'SELECT 7 AS qa_value'})
s.post('module/reporting/reports/saveMappedProperty.form',
       {'uuid': text_report, 'type': 'org.openmrs.module.reporting.report.definition.ReportDefinition',
        'property': 'dataSetDefinitions', 'newKey': 'result', 'currentKey': '', 'mappedUuid': text_ds})
if 'csv' not in s.ids:
    s.post('module/reporting/reports/renderers/saveDelimitedTextReportDesign.form',
           {'name': setup.PREFIX + ' CSV', 'reportDefinition': report,
            'rendererType': 'org.openmrs.module.reporting.report.renderer.CsvReportRenderer',
            'successUrl': '/module/reporting/reports/manageReportDesigns.form'})
    designs = s.get('ws/rest/v1/reportingrest/reportDesign', params={'reportDefinitionUuid': report, 'v': 'full'}, headers={'Accept': 'application/json'}).json()['results']
    s.remember('csv', next(x['uuid'] for x in designs if x['name'] == setup.PREFIX + ' CSV'))
if 'processor' not in s.ids:
    s.post('module/reporting/reports/saveReportProcessor.form',
           {'name': setup.PREFIX + ' Logging', 'description': 'Owned local logging verification',
            'processorType': 'org.openmrs.module.reporting.report.processor.LoggingReportProcessor',
            'processorMode': 'ON_DEMAND', 'runOnSuccess': 't', 'runOnError': 'f',
            'reportDesignUuid': s.ids['csv'], 'configuration': ''})
    html = s.get('module/reporting/reports/manageReportProcessors.form').text
    for row in re.findall(r'<tr\b[^>]*>.*?</tr>', html, re.S):
        if setup.PREFIX + ' Logging' in row:
            s.remember('processor', re.search(r'id="([a-f0-9-]+)EditLink"', row)[1])

cap_ds = s.create('capDataset', 'dataset.definition.IterableSqlDataSetDefinition', 'datasets/iterableSqlDataSetEditor.form')
s.post('module/reporting/datasets/iterableSqlDataSetDefinitionAssignQueryString.form', {'uuid': cap_ds, 'queryString': ' UNION ALL '.join(f'SELECT {i} AS qa_value' for i in range(60))})
cap_report = s.create('capReport', 'report.definition.ReportDefinition', 'reports/reportEditor.form')
s.post('module/reporting/reports/saveMappedProperty.form', {'uuid': cap_report, 'type': 'org.openmrs.module.reporting.report.definition.ReportDefinition', 'property': 'dataSetDefinitions', 'newKey': 'result', 'mappedUuid': cap_ds})
if 'capCsv' not in s.ids:
    s.post('module/reporting/reports/renderers/saveDelimitedTextReportDesign.form', {'name': setup.PREFIX + ' Cap CSV', 'reportDefinition': cap_report, 'rendererType': 'org.openmrs.module.reporting.report.renderer.CsvReportRenderer', 'successUrl': '/module/reporting/reports/manageReportDesigns.form'})
    designs = s.get('ws/rest/v1/reportingrest/reportDesign', params={'reportDefinitionUuid': cap_report, 'v': 'full'}, headers={'Accept': 'application/json'}).json()['results']
    s.remember('capCsv', next(x['uuid'] for x in designs if x['name'] == setup.PREFIX + ' Cap CSV'))
