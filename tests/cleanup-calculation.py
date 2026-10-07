"""Delete only captured Calculation proof registrations and optional definitions."""
import json
import sqlite3
import sys
from pathlib import Path
import setup

root = Path(__file__).resolve().parents[1]
target = sys.argv[1]
evidence = root / 'test-results/calculation'
setup.ROOT = evidence
site = setup.Site('ref' if target == 'reference' else 'clone', 8090 if target == 'reference' else 8093)
if target == 'clone':
    site.prefix = '/api'
owned_file = evidence / (target + ('-boolean' if '--boolean-only' in sys.argv else '')) / 'owned.json'
owned = json.loads(owned_file.read_text()) if owned_file.exists() else []
for item in owned:
    assert item['token'].startswith('QA Local Calculation 20261008-')
    if target == 'reference':
        registration_id = int(item['id'])
        if setup.read_reference_sql(f'SELECT COUNT(*) FROM calculation_registration WHERE calculation_registration_id={registration_id}').strip() == '0':
            continue
    response = site.get('module/calculation/deleteCalculationRegistration.form', params={'id': item['id']})
    assert response.status_code in (200, 302, 404), response.text
if '--definitions' in sys.argv:
    seed = sqlite3.connect((root.parent / 'Downloads/data.db').as_uri()+'?mode=ro', uri=True)
    for key, uuid in json.loads((evidence / (target+'-definitions.json')).read_text()).items():
        assert seed.execute('select count(*) from serialized_object where uuid=?', (uuid,)).fetchone()[0] == 0
        kind = 'PatientIdentifierDataDefinition' if key == 'metadata' else 'StaticValuePatientDataDefinition'
        response = site.get('module/reporting/definition/purgeDefinition.form', params={'uuid':uuid, 'type':'org.openmrs.module.reporting.data.patient.definition.'+kind})
        assert response.status_code in (200, 302, 404), response.text
    seed.close()
print('Owned Calculation fixtures cleaned: '+target)
