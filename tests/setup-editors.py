"""Owned bounded fixtures for reference editor regression tests."""
import os
from pathlib import Path
from urllib.parse import urlparse, parse_qs
import setup
setup.ROOT = Path(__file__).resolve().parents[1] / os.environ.get('REPORTS_EVIDENCE','test-results/editors')
setup.ROOT.mkdir(parents=True, exist_ok=True)
setup.PREFIX = 'QA Reports Fix R2 20261008'
s = setup.Site('ref', 8090)
cohort = s.create('cohort', 'cohort.definition.SqlCohortDefinition', 'cohorts/sqlCohortDefinition.form')
s.post('module/reporting/cohorts/sqlCohortDefinitionAssignQueryString.form', {'uuid':cohort, 'queryString': "SELECT patient_id FROM patient WHERE voided = 0 ORDER BY patient_id LIMIT 1"})
if 'indicator' not in s.ids:
    r = s.post('module/reporting/indicators/saveBaseCohortIndicator.form', {'uuid':'', 'name': setup.PREFIX+' Count', 'type':'COUNT'})
    s.remember('indicator', parse_qs(urlparse(r.headers['location']).query)['uuid'][0])
s.post('module/reporting/reports/saveMappedProperty.form', {'uuid':s.ids['indicator'],'type':'org.openmrs.module.reporting.indicator.CohortIndicator','property':'cohortDefinition','mappedUuid':cohort})
s.create('indicatorDataset', 'dataset.definition.CohortIndicatorAndDimensionDataSetDefinition', 'datasets/cohortIndicatorAndDimensionDatasetEditor.form')
sql = s.create('sql', 'dataset.definition.SqlDataSetDefinition', 'datasets/sqlDataSetEditor.form')
s.post('module/reporting/datasets/sqlDataSetDefinitionAssignQueryString.form', {'uuid':sql,'queryString':'SELECT :number AS qa_value'})
s.post('module/reporting/parameters/saveParameter.form', {'type':'org.openmrs.module.reporting.dataset.definition.SqlDataSetDefinition','uuid':sql,'currentName':'','newName':'number','label':'QA number','parameterType':'java.lang.Integer'})
s.create('multi', 'dataset.definition.MultiParameterDataSetDefinition', 'datasets/multiParameterDataSetEditor.form')
s.create('patientDataset', 'dataset.definition.PatientDataSetDefinition', 'datasets/patientDataSetEditor.form')
s.create('gender', 'data.person.definition.GenderDataDefinition', 'definition/editAnnotatedDefinition.form')
s.create('report', 'report.definition.ReportDefinition', 'reports/reportEditor.form')
s.post('module/reporting/reports/saveMappedProperty.form', {'uuid':s.ids['report'],'type':'org.openmrs.module.reporting.report.definition.ReportDefinition','property':'dataSetDefinitions','newKey':'patients','mappedUuid':s.ids['patientDataset']})
if 'csv' not in s.ids:
    s.post('module/reporting/reports/renderers/saveDelimitedTextReportDesign.form', {'name':setup.PREFIX+' CSV','reportDefinition':s.ids['report'],'rendererType':'org.openmrs.module.reporting.report.renderer.CsvReportRenderer','successUrl':'/module/reporting/reports/manageReportDesigns.form'})
    designs=s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':s.ids['report'],'v':'full'},headers={'Accept':'application/json'}).json()['results']
    s.remember('csv',next(x['uuid'] for x in designs if x['name']==setup.PREFIX+' CSV'))
