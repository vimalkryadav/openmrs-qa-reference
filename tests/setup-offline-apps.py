"""Generate owned, local-only OCL and OWA packages for paired headless workflows."""
import json
import stat
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'test-results'
ocl = root / 'ocl-offline'
owa = root / 'owa-offline'
for directory in (ocl, owa): directory.mkdir(parents=True, exist_ok=True)

def archive(file, entries):
    with zipfile.ZipFile(file, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, content in entries.items(): output.writestr(name, content)

prefix = '/orgs/QAOffline/sources/QAOffline/'
ids = ['b2b557a0-dc98-4ad8-b201-ff314db2930'+str(i) for i in range(1, 4)]
def concept(i, name, datatype='N/A'):
    return {'type':'Concept','id':str(i),'external_id':ids[i-1], 'concept_class':'Misc', 'datatype':datatype,
            'names':[{'uuid':'qa-offline-name-'+str(i), 'external_id':'b2b557a0-dc98-4ad8-b201-ff314db2950'+str(i),
                      'name':name,'locale':'en','locale_preferred':True,'name_type':'FULLY_SPECIFIED'}],
            'descriptions':[], 'retired':False, 'source':'QAOffline', 'url':prefix+'concepts/'+str(i)+'/',
            'version_url':prefix+'concepts/'+str(i)+'/v1/', 'extras':{'is_set':int(i==1)}}
parent=concept(1,'QA offline workflow parent')
member=concept(2,'QA offline workflow member')
bad=concept(3,'QA offline unsupported datatype','OfflineMissingDatatype')
mapping={'id':'set-member','external_id':'b2b557a0-dc98-4ad8-b201-ff314db29401','url':prefix+'mappings/set-member/',
         'map_type':'CONCEPT-SET','from_concept_url':parent['url'],'from_source_name':'QAOffline','from_concept_code':'1',
         'to_concept_url':member['url'],'to_source_name':'QAOffline','to_concept_code':'2',
         'updated_on':'2026-09-29T09:00:00','retired':False,'extras':{'sort_weight':1}}
for name, data in [('valid',{'concepts':[parent,member],'mappings':[mapping]}),
                   ('errors',{'concepts':[bad],'mappings':[]}), ('empty',{'concepts':[],'mappings':[]})]:
    archive(ocl/(name+'.zip'), {'export.json':json.dumps(data)})
member['names'][0]['name']='QA offline workflow member updated'
member['version_url']=prefix+'concepts/2/v2/'
archive(ocl/'update.zip', {'export.json':json.dumps({'concepts':[member],'mappings':[]})})
archive(ocl/'missing-export.zip', {'readme.txt':'No export.json'})
(ocl/'invalid.zip').write_text('not a zip')
(ocl/'fixtures.json').write_text(json.dumps({'concepts':ids,'mapping':mapping['external_id']},indent=2))

name='QA Offline Packaged App'
manifest={'name':name,'version':'1.0','description':'Owned local QA lifecycle package','launch_path':'pages/index.html',
          'deployed.owa.name':'qa-offline-app','developer':{'name':'QA'},'icons':{},'activities':{'openmrs':{'href':'*'}}}
html='''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>QA offline package</title><link rel="stylesheet" href="../assets/style.css"></head><body><h1>Offline package VERSION</h1><p id="script">Loading local script</p><p id="api">Loading local API</p><a href="detail.html">Nested page</a><script src="../assets/app.js"></script></body></html>'''
js="""document.getElementById('script').textContent='Local script loaded';fetch('../manifest.webapp').then(r=>r.json()).then(m=>fetch(m.activities.openmrs.href+'/ws/rest/v1/session')).then(r=>{document.getElementById('api').textContent=r.ok?'Local API connected':'Local API failed';});"""
base={'manifest.webapp':json.dumps(manifest),'pages/index.html':html.replace('VERSION','v1'),'pages/detail.html':'<!doctype html><html lang="en"><title>Nested</title><h1>Nested local page</h1></html>', 'assets/app.js':js, 'assets/style.css':'h1 { color: rgb(0, 90, 70); }','obsolete.txt':'v1 only'}
archive(owa/'valid.zip',base)
manifest['version']='2.0'
updated={**base,'manifest.webapp':json.dumps(manifest),'pages/index.html':html.replace('VERSION','v2')};updated.pop('obsolete.txt')
archive(owa/'update.zip',updated)
archive(owa/'missing-manifest.zip',{'index.html':'Missing manifest'})
archive(owa/'missing-launch.zip',{'manifest.webapp':json.dumps(manifest)})
archive(owa/'traversal.zip',{**updated,'../qa-outside.txt':'must not escape'})
archive(owa/'bad-manifest.zip',{**updated,'manifest.webapp':'{bad'})
archive(owa/'empty.zip',{})
(owa/'invalid.zip').write_text('not a zip')
with zipfile.ZipFile(owa/'symlink.zip','w') as z:
    for key,value in updated.items():z.writestr(key,value)
    entry=zipfile.ZipInfo('assets/link');entry.create_system=3;entry.external_attr=(stat.S_IFLNK|0o777)<<16;z.writestr(entry,'../outside')
(owa/'fixtures.json').write_text(json.dumps({'name':name,'folder':'qa-offline-app'},indent=2))
print('Generated OCL and OWA local fixtures under test-results.')
