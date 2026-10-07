"""Guarded four-row registration snapshot/recovery; no definition or clinical writes."""
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import pymysql

if not __debug__:
    raise RuntimeError('Run with assertions enabled')
root = Path(__file__).resolve().parents[1]
target, action = sys.argv[1:3]
assert target in ('reference', 'clone')
assert action in ('snapshot', 'read', 'check', 'restore')
out = root/'test-results/calculation-auto'/target
out.mkdir(parents=True, exist_ok=True)
if target == 'reference':
    config = json.loads((root/'dev/config.json').read_text())
    properties = dict(line.split('=',1) for line in (root/'.native/data/openmrs-runtime.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    db = pymysql.connect(host=config['database_host'], port=config['database_port'], user=properties['connection.username'], password=properties['connection.password'], database='openmrs', autocommit=False)
    placeholder = '%s'
else:
    db = sqlite3.connect(root.parent/'openmrs-native-data/clone/data.db')
    db.execute('PRAGMA foreign_keys=ON')
    placeholder = '?'

def query(sql, args=()):
    cursor = db.cursor()
    cursor.execute(sql,args)
    names = [col[0] for col in cursor.description] if cursor.description else []
    return [dict(zip(names,row)) for row in cursor.fetchall()] if names else []

def state():
    rows = query('SELECT * FROM calculation_registration ORDER BY calculation_registration_id')
    rows = json.loads(json.dumps(rows,default=str))
    definitions = query('SELECT uuid,serialized_data FROM serialized_object ORDER BY uuid')
    hashes = {row['uuid']:hashlib.sha256(row['serialized_data'].encode()).hexdigest() for row in definitions}
    return {'registrations':rows,'definitionHashes':hashes}

snapshot = out/'snapshot.json'
current = state()
if action == 'snapshot':
    assert not snapshot.exists(), 'Restore/archive previous run before repeating'
    assert {r['token'] for r in current['registrations']} == {'gender','age','villageName','patientId'}
    assert len(current['registrations']) == 4
    snapshot.write_text(json.dumps(current,indent=2)+'\n')
    print(json.dumps(current))
elif action == 'read':
    print(json.dumps(current))
else:
    original = json.loads(snapshot.read_text())
    assert current['definitionHashes'] == original['definitionHashes'], 'Definition changed: stop recovery'
    before = {r['token']:r for r in original['registrations']}
    assert len(before)==4
    if action == 'restore':
        assert len(current['registrations'])<=4
        for row in current['registrations']:
            assert row['token'] in before, 'Concurrent registration: stop recovery'
            if row['uuid'] != before[row['token']]['uuid']:
                assert row['token']=='age', 'Only the explicitly re-created age row may have a new identity'
                for field in ('provider_class_name','calculation_name','configuration'):
                    assert row[field] == before['age'][field]
        (out/'before-restoration.json').write_text(json.dumps(current,indent=2)+'\n')
        if target=='reference':
            assert query('SELECT @@foreign_key_checks AS enabled')[0]['enabled']==1
            assert query("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='calculation_registration'")[0]['ENGINE']=='InnoDB'
        try:
            for row in current['registrations']:
                if row['uuid'] != before[row['token']]['uuid']:
                    query('DELETE FROM calculation_registration WHERE calculation_registration_id='+placeholder+' AND uuid='+placeholder,(row['calculation_registration_id'],row['uuid']))
            for row in original['registrations']:
                existing=query('SELECT uuid FROM calculation_registration WHERE calculation_registration_id='+placeholder,(row['calculation_registration_id'],))
                if existing:
                    assert existing[0]['uuid']==row['uuid']
                    columns=[name for name in row if name!='calculation_registration_id']
                    query('UPDATE calculation_registration SET '+','.join('`'+name+'`='+placeholder for name in columns)+' WHERE calculation_registration_id='+placeholder,tuple(row[name] for name in columns)+(row['calculation_registration_id'],))
                else:
                    columns=list(row)
                    query('INSERT INTO calculation_registration ('+','.join('`'+name+'`' for name in columns)+') VALUES ('+','.join(placeholder for _ in columns)+')',tuple(row[name] for name in columns))
            assert state()==original, 'Recovery differs from original field/identity snapshot'
            db.commit()
        except BaseException:
            db.rollback()
            raise
        (out/'restoration.json').write_text(json.dumps({'allFourRegistrationRowsExact':True,'allDefinitionHashesUnchanged':True,'definitionCount':len(original['definitionHashes']),'clinicalTablesWritten':False},indent=2)+'\n')
    else:
        assert current==original, 'Rows changed'
    print(json.dumps({'exactBaseline':True}))
db.close()
