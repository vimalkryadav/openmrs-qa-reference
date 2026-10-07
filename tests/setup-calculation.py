"""Create only owned Reporting definitions used by the Calculation console proof."""
import json
import sys
from pathlib import Path
import setup

setup.ROOT = Path(__file__).resolve().parents[1] / 'test-results/calculation'
setup.ROOT.mkdir(parents=True, exist_ok=True)
setup.PREFIX = 'QA Local Calculation 20261008'
target = sys.argv[1]
s = setup.Site('ref' if target == 'reference' else 'clone', 8090 if target == 'reference' else 8093)
if target == 'clone':
    s.prefix = '/api'
if '--boolean-only' in sys.argv:
    s.ids = {}
    uuid = s.create('boolean', 'data.patient.definition.StaticValuePatientDataDefinition', 'definition/editAnnotatedDefinition.form')
    s.post('module/reporting/parameters/saveParameter.form', {'uuid': uuid, 'type': 'org.openmrs.module.reporting.data.patient.definition.StaticValuePatientDataDefinition', 'currentName': '', 'newName': 'staticValue', 'label': 'Value', 'parameterType': 'java.lang.Boolean'})
    (setup.ROOT / (target+'-definitions.json')).write_text(json.dumps(s.ids, indent=2))
    raise SystemExit(0)
for key, datatype in [('text', 'java.lang.String'), ('number', 'java.lang.Integer'), ('boolean', 'java.lang.Boolean')]:
    uuid = s.create(key, 'data.patient.definition.StaticValuePatientDataDefinition', 'definition/editAnnotatedDefinition.form')
    s.post('module/reporting/parameters/saveParameter.form', {'uuid': uuid, 'type': 'org.openmrs.module.reporting.data.patient.definition.StaticValuePatientDataDefinition', 'currentName': '', 'newName': 'staticValue', 'label': 'Value', 'parameterType': datatype})
metadata = s.create('metadata', 'data.patient.definition.PatientIdentifierDataDefinition', 'definition/editAnnotatedDefinition.form')
s.post('module/reporting/parameters/saveParameter.form', {'uuid': metadata, 'type': 'org.openmrs.module.reporting.data.patient.definition.PatientIdentifierDataDefinition', 'currentName': '', 'newName': 'types', 'label': 'Identifier types', 'parameterType': 'org.openmrs.PatientIdentifierType', 'collectionType': 'java.util.List'})
for key, datatype, collection in [('list', 'java.lang.Integer', 'java.util.List'), ('location', 'org.openmrs.Location', 'java.util.List')]:
    uuid = s.create(key, 'data.patient.definition.StaticValuePatientDataDefinition', 'definition/editAnnotatedDefinition.form')
    s.post('module/reporting/parameters/saveParameter.form', {'uuid': uuid, 'type': 'org.openmrs.module.reporting.data.patient.definition.StaticValuePatientDataDefinition', 'currentName': '', 'newName': 'staticValue', 'label': 'Value', 'parameterType': datatype, 'collectionType': collection})
( setup.ROOT / (target+'-definitions.json')).write_text(json.dumps(s.ids, indent=2))
print('Owned Calculation data definitions ready for '+target)
