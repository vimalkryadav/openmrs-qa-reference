"""Bounded reusable definitions for object dataset UI regressions."""
from pathlib import Path
import os
import json
import setup
setup.ROOT = Path(__file__).resolve().parents[1] / os.environ.get('REPORTS_OBJECT_EVIDENCE',os.environ.get('REPORTS_EVIDENCE','test-results/objects'))
setup.ROOT.mkdir(parents=True, exist_ok=True)
setup.PREFIX = os.environ.get('REPORTS_OBJECT_PREFIX','QA Reports Fix R3 20261008')
s = setup.Site('ref',8090)
patient = int(setup.read_reference_sql('SELECT v.patient_id FROM visit v JOIN encounter e ON e.visit_id=v.visit_id AND e.voided=0 JOIN obs o ON o.encounter_id=e.encounter_id AND o.voided=0 JOIN patient p ON p.patient_id=v.patient_id AND p.voided=0 WHERE v.voided=0 ORDER BY v.visit_id,e.encounter_id,o.obs_id LIMIT 1').strip())
s.remember('patient',patient)
cohort=s.create('cohort','cohort.definition.SqlCohortDefinition','cohorts/sqlCohortDefinition.form')
s.post('module/reporting/cohorts/sqlCohortDefinitionAssignQueryString.form',{'uuid':cohort,'queryString':f'SELECT patient_id FROM patient WHERE patient_id={patient} AND voided=0'})
gender=s.create('gender','data.person.definition.GenderDataDefinition','definition/editAnnotatedDefinition.form')
age=s.create('age','data.person.definition.AgeDataDefinition','definition/editAnnotatedDefinition.form')
s.post('module/reporting/parameters/saveParameter.form',{'uuid':age,'type':'org.openmrs.module.reporting.data.person.definition.AgeDataDefinition','currentName':'','newName':'effectiveDate','label':'Age effective date','parameterType':'java.util.Date'})
for key,kind,table,owner in [('obs','Obs','obs','person_id'),('visit','Visit','visit','patient_id'),('encounter','EncounterAndObs','encounter','patient_id')]:
    s.create(key,'dataset.definition.'+kind+'DataSetDefinition','definition/editAnnotatedDefinition.form')
    sourceKind='Encounter' if key=='encounter' else kind
    s.create(key+'Id','data.'+key+'.definition.'+sourceKind+'IdDataDefinition','definition/editAnnotatedDefinition.form')
    filterKind='query.visit.definition.AllVisitQuery' if key=='visit' else 'query.'+key+'.definition.Sql'+sourceKind+'Query'
    filter=s.create(key+'Filter',filterKind,'definition/editAnnotatedDefinition.form')
    if key!='visit':
        s.post('module/reporting/definition/saveAnnotatedDefinition.form',{'uuid':filter,'type':'org.openmrs.module.reporting.'+filterKind,'parentType':'org.openmrs.module.reporting.query.'+key+'.definition.'+sourceKind+'Query','name':setup.PREFIX+'-'+key+'Filter','parameter.query.allowAtEvaluation':'f','parameter.query.value':f'SELECT {table}_id FROM {table} WHERE {owner}={patient} AND voided=0 ORDER BY {table}_id LIMIT 2'})
    report=s.create(key+'Report','report.definition.ReportDefinition','reports/reportEditor.form')
    for prop,source in [('dataSetDefinitions',s.ids[key]),('baseCohortDefinition',cohort)]:
        s.post('module/reporting/reports/saveMappedProperty.form',{'uuid':report,'type':'org.openmrs.module.reporting.report.definition.ReportDefinition','property':prop,'newKey':'rows','mappedUuid':source})
    designKey=key+'Csv'
    if designKey not in s.ids:
        s.post('module/reporting/reports/renderers/saveDelimitedTextReportDesign.form',{'name':setup.PREFIX+' '+key+' CSV','reportDefinition':report,'rendererType':'org.openmrs.module.reporting.report.renderer.CsvReportRenderer','successUrl':'/module/reporting/reports/manageReportDesigns.form'})
        designs=s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':report,'v':'full'},headers={'Accept':'application/json'}).json()['results']
        s.remember(designKey,next(x['uuid'] for x in designs if x['name']==setup.PREFIX+' '+key+' CSV'))
( setup.ROOT/'expected.json').write_text(json.dumps({key:setup.read_reference_sql(query).splitlines() for key,query in {'obs':f'SELECT obs_id FROM obs WHERE person_id={patient} AND voided=0 ORDER BY obs_id LIMIT 2','visit':f'SELECT visit_id FROM visit WHERE patient_id={patient} AND voided=0 ORDER BY visit_id','encounter':f'SELECT encounter_id FROM encounter WHERE patient_id={patient} AND voided=0 ORDER BY encounter_id LIMIT 2'}.items()},indent=2))
typedFilter=s.create('visitTypedFilter','query.visit.definition.BasicVisitQuery','definition/editAnnotatedDefinition.form')
s.post('module/reporting/parameters/saveParameter.form',{'uuid':typedFilter,'type':'org.openmrs.module.reporting.query.visit.definition.BasicVisitQuery','currentName':'','newName':'visitTypes','label':'Visit types','parameterType':'org.openmrs.VisitType','collectionType':'java.util.List'})
s.remember('visitTypes',setup.read_reference_sql(f'SELECT visit_type_id FROM visit WHERE patient_id={patient} AND voided=0 ORDER BY visit_id').splitlines())
scalar=s.create('scalar','dataset.definition.SqlDataSetDefinition','datasets/sqlDataSetEditor.form')
s.post('module/reporting/datasets/sqlDataSetDefinitionAssignQueryString.form',{'uuid':scalar,'queryString':'SELECT 7 AS qa_value'})
metadataReport=s.create('metadataReport','report.definition.ReportDefinition','reports/reportEditor.form')
s.post('module/reporting/reports/saveMappedProperty.form',{'uuid':metadataReport,'type':'org.openmrs.module.reporting.report.definition.ReportDefinition','property':'dataSetDefinitions','newKey':'rows','mappedUuid':scalar})
