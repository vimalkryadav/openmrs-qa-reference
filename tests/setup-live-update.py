"""Own two-row definitions for the edited-dataset regression."""
from pathlib import Path
import setup
setup.ROOT = Path(__file__).resolve().parents[1] / 'test-results/live-update'
setup.ROOT.mkdir(parents=True, exist_ok=True)
setup.PREFIX = 'QA Reports Fix R5 20261008'
s = setup.Site('ref', 8090)
for key,kind in [('sql','Sql'),('iterable','IterableSql')]:
    dataset=s.create(key,'dataset.definition.'+kind+'DataSetDefinition','datasets/sqlDataSetEditor.form')
    endpoint='iterableSqlDataSetDefinitionAssignQueryString' if key=='iterable' else 'sqlDataSetDefinitionAssignQueryString'
    s.post('module/reporting/datasets/'+endpoint+'.form',{'uuid':dataset,'queryString':'SELECT 7 AS qa_value UNION ALL SELECT 9 AS qa_value'})
    report=s.create(key+'Report','report.definition.ReportDefinition','reports/reportEditor.form')
    s.post('module/reporting/reports/saveMappedProperty.form',{'uuid':report,'type':'org.openmrs.module.reporting.report.definition.ReportDefinition','property':'dataSetDefinitions','newKey':'rows','mappedUuid':dataset})
    if key+'Design' not in s.ids:
        s.post('module/reporting/reports/renderers/saveDelimitedTextReportDesign.form',{'name':setup.PREFIX+' '+key,'reportDefinition':report,'rendererType':'org.openmrs.module.reporting.report.renderer.CsvReportRenderer','successUrl':'/module/reporting/reports/manageReportDesigns.form'})
        designs=s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':report,'v':'full'},headers={'Accept':'application/json'}).json()['results']
        s.remember(key+'Design',next(d['uuid'] for d in designs if d['name']==setup.PREFIX+' '+key))
