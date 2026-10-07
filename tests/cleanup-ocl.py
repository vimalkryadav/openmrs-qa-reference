"""Remove only this runner's captured OCL UUIDs, preserving all seeded import history."""
import base64
import json
import sqlite3
import subprocess
import sys
import urllib.request
from pathlib import Path
from uuid import UUID

root = Path(__file__).resolve().parents[1]
target = sys.argv[1]
evidence = root / 'test-results/ocl-offline' / target
owned = json.loads((evidence / 'owned.json').read_text())
seed = sqlite3.connect((root.parent / 'Downloads/data.db').as_uri() + '?mode=ro', uri=True)
for key, table in [('imports', 'openconceptlab_import'), ('concepts', 'concept')]:
    for uuid in owned[key]:
        UUID(uuid)
        assert seed.execute('SELECT COUNT(*) FROM '+table+' WHERE uuid=?', [uuid]).fetchone()[0] == 0
seed.close()
origin = 'http://localhost:8090/openmrs' if target == 'reference' else 'http://localhost:8093/api/openmrs'
for uuid in owned['concepts']:
    assert uuid.startswith('b2b557a0-dc98-4ad8-b201-ff314db293')
    request = urllib.request.Request(origin+'/ws/rest/v1/concept/'+uuid+'?purge=true', method='DELETE')
    if target == 'reference':
        request.add_header('Authorization', 'Basic '+base64.b64encode(b'admin:Admin123').decode())
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            assert response.status in (200, 204)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
uuids = ','.join("'"+str(UUID(uuid))+"'" for uuid in owned['imports'])
statements = [
    'DELETE FROM openconceptlab_item WHERE import_id IN (SELECT import_id FROM openconceptlab_import WHERE uuid IN ('+uuids+'))',
    'DELETE FROM openconceptlab_import WHERE uuid IN ('+uuids+')',
]
counts = "SELECT 'imports', COUNT(*) FROM openconceptlab_import; SELECT 'items', COUNT(*) FROM openconceptlab_item; SELECT 'owned-concepts', COUNT(*) FROM concept WHERE uuid IN ("+','.join("'"+uuid+"'" for uuid in owned['concepts'])+"); SELECT 'owned-imports',COUNT(*) FROM openconceptlab_import WHERE uuid IN ("+uuids+");"
if target == 'reference':
    result = subprocess.run(['docker','exec','-i','openmrs-qa-db-1','mariadb','-uopenmrs','-popenmrs','openmrs','-N','-B'],input='START TRANSACTION;'+ ';'.join(statements)+';COMMIT;'+counts,text=True,check=True,capture_output=True).stdout
    after = dict(line.split('\t') for line in result.splitlines())
else:
    db = sqlite3.connect(root.parent / 'openmrs-native-data/clone/data.db')
    db.execute('PRAGMA foreign_keys=ON')
    with db:
        for sql in statements: db.execute(sql)
    after = dict(db.execute(sql).fetchone() for sql in counts.split(';') if sql.strip())
    db.close()
assert int(after['imports']) == 24 and int(after['items']) == 35440, after
assert int(after['owned-concepts']) == 0 and int(after['owned-imports']) == 0, after
(evidence/'cleanup.json').write_text(json.dumps({'owned':owned,'remaining':after},indent=2))
print(json.dumps(after))
