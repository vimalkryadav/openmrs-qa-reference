"""Minimal owned fixtures for recoverable legacy form validation."""
from pathlib import Path
import setup
setup.ROOT=Path(__file__).resolve().parents[1]/'test-results/form-errors';setup.ROOT.mkdir(parents=True,exist_ok=True)
setup.PREFIX='QA Reports Fix R6 20261008';s=setup.Site('ref',8090)
cohort=s.create('cohort','cohort.definition.SqlCohortDefinition','cohorts/sqlCohortDefinition.form')
s.post('module/reporting/cohorts/sqlCohortDefinitionAssignQueryString.form',{'uuid':cohort,'queryString':'SELECT patient_id FROM patient WHERE patient_id=-1'})
dimension=s.create('dimension','indicator.dimension.CohortDefinitionDimension','indicators/editCohortDefinitionDimension.form')
s.post('module/reporting/reports/saveMappedProperty.form',{'uuid':dimension,'type':'org.openmrs.module.reporting.indicator.dimension.CohortDefinitionDimension','property':'cohortDefinitions','newKey':'one','mappedUuid':cohort})
logic=s.create('logic','dataset.definition.LogicDataSetDefinition','datasets/logicDataSetEditor.form')
s.post('module/reporting/datasets/logicDataSetEditorSave.form',{'uuid':logic,'name':setup.PREFIX+' logic','columnName':['gender'],'columnLabel':['Gender'],'columnLogic':['gender'],'columnFormat':['']})
if 'processor' not in s.ids:
    s.post('module/reporting/reports/saveReportProcessor.form',{'name':setup.PREFIX+' Processor','processorType':'org.openmrs.module.reporting.report.processor.LoggingReportProcessor','processorMode':'DISABLED','runOnSuccess':'t','configuration':'label=Retained'})
    s.remember('processor',setup.read_reference_sql("SELECT uuid FROM reporting_report_processor WHERE name='"+setup.PREFIX+" Processor'").strip())
