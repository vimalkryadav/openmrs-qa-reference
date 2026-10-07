"""Snapshot/restore only seven OCL configuration properties; never unsubscribe or alter imports."""
import json
from pathlib import Path
import sqlite3
import sys
import pymysql
import httpx

if not __debug__:
    raise RuntimeError('Run this guarded test helper without Python optimization')
root = Path(__file__).resolve().parents[1]
target, action = sys.argv[1:3]
assert target in ('reference', 'clone') and action in ('snapshot', 'restore', 'check')
output = root / 'test-results/ocl-configuration' / target
output.mkdir(parents=True, exist_ok=True)
allowed = {'openconceptlab.'+name for name in ('subscriptionUuid','subscriptionUrl','token','validationType','scheduledDays','scheduledTime','subscribedToSnapshot')}
if target == 'reference':
    config = json.loads((root/'dev/config.json').read_text())
    properties = dict(line.split('=',1) for line in (root/'.native/data/openmrs-runtime.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    db = pymysql.connect(host=config['database_host'], port=config['database_port'], user=properties['connection.username'], password=properties['connection.password'], database='openmrs', autocommit=False)
    placeholder = '%s'
else:
    db = sqlite3.connect(root.parent/'openmrs-native-data/clone/data.db')
    db.execute('PRAGMA foreign_keys=ON')
    placeholder = '?'

def query(sql, values=()):
    cursor = db.cursor();cursor.execute(sql,values) if values else cursor.execute(sql)
    columns = [column[0] for column in cursor.description] if cursor.description else []
    return [dict(zip(columns,row)) for row in cursor.fetchall()] if columns else []

def state():
    return {'properties':query("SELECT property,property_value,description,uuid FROM global_property WHERE property LIKE 'openconceptlab.%' ORDER BY property"),
            'imports':query('SELECT uuid FROM openconceptlab_import ORDER BY uuid'),
            'items':query('SELECT COUNT(*) AS count FROM openconceptlab_item')[0]['count']}

saved = output/'snapshot.json'
if action == 'snapshot':
    assert not saved.exists(), 'Snapshot exists; preserve it and restore before a new run'
    before = state()
    values = {row['property']:row['property_value'] for row in before['properties']}
    assert not values.get('openconceptlab.subscriptionUrl') and not values.get('openconceptlab.token'), 'Only the unconfigured baseline is used by this proof'
    saved.write_text(json.dumps(before,indent=2))
    print('Captured exact OCL property rows and import identities')
else:
    before = json.loads(saved.read_text());current = state()
    assert current['imports']==before['imports'] and current['items']==before['items'], 'Import history changed; no cleanup is authorized for it'
    assert [row for row in current['properties'] if row['property'] not in allowed] == [row for row in before['properties'] if row['property'] not in allowed], 'Unrelated OCL setting changed'
    if action == 'restore':
        values = {row['property']:row['property_value'] for row in current['properties']}
        assert not values.get('openconceptlab.subscriptionUrl') or '/QAConfig/' in values['openconceptlab.subscriptionUrl']
        assert not values.get('openconceptlab.token') or values['openconceptlab.token'].startswith('qa-offline-config-proof')
        original = {row['property']:row for row in before['properties']}
        if target == 'reference':
            # Use the normal service first so its global-property cache observes restoration.
            # New, owned properties become null before their exact SQL removal below.
            ordered = sorted(current['properties'], key=lambda row: row['property'] != 'openconceptlab.subscriptionUrl')
            with httpx.Client(base_url='http://localhost:8090/openmrs/ws/rest/v1/', auth=('admin','Admin123'), timeout=30) as client:
                for row in ordered:
                    if row['property'] in allowed:
                        value = original.get(row['property'], {}).get('property_value')
                        response = client.post('systemsetting/'+row['uuid'], json={'value':value})
                        response.raise_for_status()
                response = client.get('openconceptlab/subscription')
                response.raise_for_status()
                assert response.json()['results'] == [None], 'Native subscription cache was not restored'
            db.rollback()  # Re-read API writes outside the prior repeatable-read snapshot.
        try:
            for row in current['properties']:
                if row['property'] in allowed and row['property'] not in original:
                    query('DELETE FROM global_property WHERE property='+placeholder+' AND uuid='+placeholder,(row['property'],row['uuid']))
            for name,row in original.items():
                if name in allowed:
                    query('UPDATE global_property SET property_value='+placeholder+',description='+placeholder+',uuid='+placeholder+' WHERE property='+placeholder,(row['property_value'],row['description'],row['uuid'],name))
            restored = state();assert restored==before, 'Restoration differs from exact snapshot'
            db.commit()
        except BaseException:
            db.rollback();raise
        (output/'restoration.json').write_text(json.dumps({'exactPropertyRowsRestored':True,'importUUIDsUnchanged':True,'itemCount':before['items'],'propertyCount':len(before['properties'])},indent=2))
        print('Exact properties restored; import identities and items unchanged')
    else:
        assert current==before, 'Post-restoration state changed'
        print('Exact baseline remains restored')
db.close()
