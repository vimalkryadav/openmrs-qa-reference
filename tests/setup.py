import httpx,re,json,sys
from pathlib import Path
from urllib.parse import urlparse,parse_qs
ROOT=Path(__file__).resolve().parents[1]/'.build/test-fixtures';ROOT.mkdir(parents=True,exist_ok=True)
PREFIX='QA Reports final headless 20261007'
class Site:
 def __init__(self,name,port):
  self.name=name;self.prefix='';self.c=httpx.Client(base_url=f'http://localhost:{port}',auth=('admin','Admin123') if name=='ref' else None,headers={'Accept':'text/html'},timeout=60,follow_redirects=False);self.token={};self.ids={}
  state=ROOT/(name+'-ids.json')
  if state.exists():self.ids=json.loads(state.read_text())
  if name=='ref':
   r=self.c.get('/openmrs/ws/rest/v1/session',headers={'Accept':'application/json'});r.raise_for_status();s=self.c.get('/openmrs/csrfguard').text
   for a,b in [('tokenName','masterTokenValue')]:self.token={re.search(r'var '+a+r'\s*=\s*[\"\']([^\"\']+)',s)[1]:re.search(r'var '+b+r'\s*=\s*[\"\']([^\"\']+)',s)[1]}
 def path(self,p):return self.prefix+'/openmrs/'+p.lstrip('/')
 def get(self,p,**kw):return self.c.get(self.path(p),**kw)
 def post(self,p,data):
  r=self.c.post(self.path(p),data={**data,**self.token},headers=self.token); print(self.name,'POST',p,r.status_code,r.headers.get('location',''));(ROOT/(self.name+'-error.html')).write_text(r.text) if r.status_code>=400 else None;r.raise_for_status() if r.status_code>=400 else None;return r
 def remember(self,key,value):self.ids[key]=value;(ROOT/(self.name+'-ids.json')).write_text(json.dumps(self.ids,indent=2));return value
 def create(self,key,kind,subpath):
  if key in self.ids:return self.ids[key]
  r=self.post('module/reporting/reports/saveBaseParameterizable.form',{'uuid':'','type':'org.openmrs.module.reporting.'+kind,'name':PREFIX+'-'+key,'description':'Bounded lifecycle proof','successUrl':'/module/reporting/'+subpath+'?uuid=uuid'})
  return self.remember(key,parse_qs(urlparse(r.headers['location']).query)['uuid'][0])
 def savehtml(self,key,r): (ROOT/(self.name+'-'+key+'.html')).write_text(r.text);return r

def setup(s):
 cohort=s.create('cohort','cohort.definition.SqlCohortDefinition','cohorts/sqlCohortDefinition.form')
 s.post('module/reporting/cohorts/sqlCohortDefinitionAssignQueryString.form',{'uuid':cohort,'queryString':'select patient_id from patient where voided = 0 order by patient_id limit 1'})
 period=s.create('period','report.definition.PeriodIndicatorReportDefinition','reports/periodIndicatorReport.form')
 if '--skip-column' not in sys.argv:s.post('module/reporting/reports/periodIndicatorReportSaveColumn.form',{'index':0 if s.name=='clone' else '', 'uuid':period,'key':'A1','displayName':'One patient','indicator':'','cohortQuery':cohort,'createFromCohortQuery':'true'})
 s.savehtml('period-before',s.get('module/reporting/reports/periodIndicatorReport.form',params={'uuid':period}))
 if 'dataset' not in s.ids:
  r=s.post('module/reporting/reports/logicReportCreate.form',{'name':PREFIX+'-row','description':'Bounded row report'})
  s.remember('dataset',parse_qs(urlparse(r.headers['location']).query)['uuid'][0])
 dataset=s.ids['dataset']
 reports=s.get('ws/rest/v1/reportingrest/reportDefinition',params={'q':PREFIX,'v':'full'},headers={'Accept':'application/json'}).json();(ROOT/(s.name+'-reports.json')).write_text(json.dumps(reports,indent=2))
 row=next(x for x in reports['results'] if x['name']==PREFIX+'-row');s.remember('row',row['uuid'])
 for id,typ in [(row['uuid'],'ReportDefinition'),(period,'ReportDefinition')]:
  s.post('module/reporting/reports/saveMappedProperty.form',{'type':'org.openmrs.module.reporting.report.definition.'+typ,'uuid':id,'property':'baseCohortDefinition','mappedUuid':cohort})
 s.post('module/reporting/datasets/logicDataSetEditorSave.form',{'uuid':dataset,'name':PREFIX+'-row (DSD)','description':'Saved twice','columnName':['gender'],'columnLabel':['Gender'],'columnLogic':['gender'],'columnFormat':['']})
 s.savehtml('row-editor',s.get('module/reporting/datasets/logicDataSetEditor.form',params={'uuid':dataset}))

if __name__=='__main__':
 s=Site('ref',8090);setup(s)
 import uuid
 base='org.openmrs.module.reporting.data.person.definition.'
 source=s.ids.get('source') or s.remember('source',str(uuid.uuid4()))
 converted=s.ids.get('converted') or s.remember('converted',str(uuid.uuid4()))
 s.post('module/reporting/definition/saveAnnotatedDefinition.form',{'uuid':source,'type':base+'GenderDataDefinition','parentType':base+'PersonDataDefinition','name':'QA Reports converter source'})
 s.post('module/reporting/definition/saveAnnotatedDefinition.form',{'uuid':converted,'type':base+'ConvertedPersonDataDefinition','parentType':base+'PersonDataDefinition','name':'QA Reports converter lifecycle','parameter.definitionToConvert.allowAtEvaluation':'f','parameter.definitionToConvert.value':json.dumps({'uuid':source,'mappings':{}}),'parameter.converters.allowAtEvaluation':'f','parameter.converters.value':json.dumps([{'type':'StringConverter','properties':{'conversions':{'F':'Female','M':'Male'},'unspecifiedValue':'Other'}}])})
 if 'csvDesign' not in s.ids:
  s.post('module/reporting/reports/renderers/saveNonConfigurableReportRenderer.form',{'name':'QA Reports final headless CSV','reportDefinition':s.ids['row'],'rendererType':'org.openmrs.module.reporting.report.renderer.CsvReportRenderer','successUrl':'manageReportDesigns.form'})
  data=s.get('ws/rest/v1/reportingrest/reportDesign',params={'reportDefinitionUuid':s.ids['row'],'v':'full'},headers={'Accept':'application/json'}).json()
  s.remember('csvDesign',next(x['uuid'] for x in data['results'] if x['name']=='QA Reports final headless CSV'))
