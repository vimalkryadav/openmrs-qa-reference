"""Remove only reviewed pre-audit QA extras absent from an immutable seed.

Dry run is the default. --apply is reserved for the explicitly approved reconciliation.
No clinical tables, seed contents, configuration rows or unknown UUIDs are changed.
"""
import argparse
import json
import re
import sqlite3
from pathlib import Path
import setup

parser=argparse.ArgumentParser()
parser.add_argument('--seed',type=Path,required=True)
parser.add_argument('--audit',type=Path,required=True)
parser.add_argument('--apply',action='store_true')
args=parser.parse_args()
setup.ROOT=Path(__file__).resolve().parents[1]/'test-results/baseline-reconciliation'
setup.ROOT.mkdir(parents=True,exist_ok=True)
diff=json.loads((args.audit/'reporting-data-comparison.json').read_text())
snapshot=json.loads((args.audit/'reporting-data-snapshot.json').read_text())
seed=sqlite3.connect(args.seed.resolve().as_uri()+'?mode=ro&immutable=1',uri=True)
targets={table:details['only_reference'] for table,details in diff.items()}
assert {t:len(v) for t,v in targets.items()}=={'serialized_object':5,'reporting_report_design':1,'reporting_report_request':2,'reporting_report_processor':0}
for values in targets.values():
    assert all(re.fullmatch(r'[a-f0-9-]{36}',u) for u in values)
quote=lambda values: ','.join("'"+u+"'" for u in values) or "''"
ledger={'seedName':args.seed.name,'seedBytes':args.seed.stat().st_size,'targets':targets,'before':{},'actions':[]}
seed_sets={}
for table,details in snapshot.items():
    fields=list(details['reference'][0])
    expression='JSON_OBJECT('+','.join("'"+field+"',"+field for field in fields)+')'
    rows=[json.loads(line) for line in setup.read_reference_sql('SELECT '+expression+' FROM '+table+' ORDER BY uuid').splitlines()]
    seed_sets[table]={r[0] for r in seed.execute('SELECT uuid FROM '+table)}
    assert not seed_sets[table].intersection(targets[table]),table
    assert {r['uuid'] for r in rows}==seed_sets[table]|set(targets[table]),'Unknown or missing current rows: '+table
    expected={r['uuid']:r for r in details['reference'] if r['uuid'] in targets[table]}
    for row in rows:
        if row['uuid'] in expected: assert row==expected[row['uuid']],('Changed target',row)
    ledger['before'][table]=rows
for uuid in targets['serialized_object']:
    count=int(setup.read_reference_sql("SELECT COUNT(*) FROM serialized_object WHERE serialized_data LIKE '%"+uuid+"%' AND uuid NOT IN ("+quote(targets['serialized_object'])+")"))
    assert count==0,('Unrecognized definition depends on target',uuid)
    for table in ['reporting_report_design','reporting_report_request']:
        count=int(setup.read_reference_sql("SELECT COUNT(*) FROM "+table+" WHERE report_definition_uuid='"+uuid+"' AND uuid NOT IN ("+quote(targets[table])+")"))
        assert count==0,('Unrecognized report dependency',uuid,table)
count=int(setup.read_reference_sql('SELECT COUNT(*) FROM reporting_report_processor p JOIN reporting_report_design d ON d.id=p.report_design_id WHERE d.uuid IN ('+quote(targets['reporting_report_design'])+')'))
assert count==0,'Processor depends on reviewed design'
ledger['resourcesBefore']=setup.read_reference_sql('SELECT r.uuid FROM reporting_report_design_resource r JOIN reporting_report_design d ON d.id=r.report_design_id WHERE d.uuid IN ('+quote(targets['reporting_report_design'])+')').splitlines()
ledger['dependencyChecks']='No definitions, requests, designs or processors outside the reviewed targets depend on these rows.'
path=setup.ROOT/('applied.json' if args.apply else 'plan.json')
def persist():path.write_text(json.dumps(ledger,indent=2))
persist()
if args.apply:
    site=setup.Site('ref',8090)
    for uuid in targets['reporting_report_request']:
        response=site.c.delete(site.path('ws/rest/v1/reportingrest/reportRequest/'+uuid))
        assert response.status_code in (200,204),response.text
        ledger['actions'].append({'table':'reporting_report_request','uuid':uuid,'status':response.status_code});persist()
    for uuid in targets['reporting_report_design']:
        site.post('module/reporting/reports/deleteReportDesign.form',{'uuid':uuid})
        ledger['actions'].append({'table':'reporting_report_design','uuid':uuid});persist()
    definitions=[r for r in ledger['before']['serialized_object'] if r['uuid'] in targets['serialized_object']]
    definitions.sort(key=lambda r:'report.definition' not in r['type'])
    for row in definitions:
        assert row['name'].startswith('QA Reports ')
        response=site.get('module/reporting/definition/purgeDefinition.form',params={'uuid':row['uuid'],'type':row['type']})
        assert response.status_code in (200,302),response.text
        ledger['actions'].append({'table':'serialized_object','uuid':row['uuid'],'name':row['name']});persist()
    ledger['after']={}
    for table,expected in seed_sets.items():
        actual=set(setup.read_reference_sql('SELECT uuid FROM '+table+' ORDER BY uuid').splitlines())
        ledger['after'][table]={'uuidCount':len(actual),'matchesSeedUuidSet':actual==expected,'remainingTargets':list(actual.intersection(targets[table]))}
        assert actual==expected,(table,actual.symmetric_difference(expected))
    persist()
print(json.dumps({'applied':args.apply,'counts':{t:len(v)for t,v in targets.items()},'evidence':str(path)},indent=2))
