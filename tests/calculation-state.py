"""Read-only hashes and fixture ownership evidence for Calculation console flows."""
import hashlib
import json
import sqlite3
import sys
from pathlib import Path
import setup

root = Path(__file__).resolve().parents[1]
out = root / 'test-results/calculation'
action = sys.argv[1]
query = "SELECT uuid,serialized_data FROM serialized_object WHERE name LIKE 'QA Local Calculation 20261008-%'"
clone = sqlite3.connect((root.parent / 'openmrs-native-data/clone/data.db').as_uri()+'?mode=ro', uri=True)
state = {
    'clone': {row[0]:hashlib.sha256(row[1].encode()).hexdigest() for row in clone.execute(query)},
    'reference': dict(line.split('\t') for line in setup.read_reference_sql(query.replace('serialized_data','SHA2(serialized_data,256)')).splitlines()),
}
if action == 'before':
    (out/'definition-hashes-before.json').write_text(json.dumps(state, indent=2))
elif action == 'after':
    assert state == json.loads((out/'definition-hashes-before.json').read_text()), 'Saved definitions changed during evaluation'
    (out/'definition-hashes-after.json').write_text(json.dumps(state, indent=2))
elif action == 'clean':
    assert state == {'clone':{},'reference':{}}, state
    query = "SELECT COUNT(*) FROM calculation_registration WHERE token LIKE 'QA Local Calculation 20261008-%'"
    counts = {'clone':clone.execute(query).fetchone()[0], 'reference':int(setup.read_reference_sql(query))}
    assert counts == {'clone':0,'reference':0}, counts
    seed = sqlite3.connect((root.parent / 'Downloads/data.db').as_uri()+'?mode=ro', uri=True)
    baseline = dict(seed.execute('SELECT uuid,token FROM calculation_registration'))
    assert dict(clone.execute('SELECT uuid,token FROM calculation_registration')) == baseline
    assert dict(line.split('\t') for line in setup.read_reference_sql('SELECT uuid,token FROM calculation_registration').splitlines()) == baseline
    (out/'cleanup.json').write_text(json.dumps({'ownedDefinitions':state,'ownedRegistrations':counts,'seedRegistrationsPreserved':len(baseline)},indent=2))
else:
    raise SystemExit('before|after|clean')
print('PASS Calculation state '+action)
