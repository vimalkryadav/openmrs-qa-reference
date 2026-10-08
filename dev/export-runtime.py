#!/usr/bin/env python3
"""One-time export from existing reference containers; no image build, no DB copy/reset."""
import importlib.util
import json
import subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / '.native'
if TARGET.exists() and any(TARGET.iterdir()):
    raise SystemExit('Refusing to overwrite existing .native state. Back it up explicitly before a fresh export.')
TARGET.mkdir(exist_ok=True)
for folder in ['tomcat', 'data', 'frontend', 'logs', 'clock', 'distribution']:
    (TARGET / folder).mkdir()
containers = ['openmrs-qa-backend-1', 'openmrs-qa-frontend-1', 'openmrs-qa-gateway-1']
metadata = json.loads(subprocess.check_output(['docker', 'inspect', *containers], text=True))
(TARGET / 'export-provenance.json').write_text(json.dumps([
    {'name': item['Name'], 'imageId': item['Image'], 'imageTag': item['Config']['Image'],
     'mounts': [{'type': m['Type'], 'name': m.get('Name'), 'destination': m['Destination']} for m in item['Mounts']]}
    for item in metadata], indent=2))
for container, source, destination in [
    (containers[0], '/usr/local/tomcat/.', 'tomcat/'),
    (containers[0], '/openmrs/distribution/.', 'distribution/'),
    (containers[1], '/usr/share/nginx/html/.', 'frontend/')]:
    subprocess.run(['docker', 'cp', container + ':' + source, str(TARGET / destination)], check=True)
# Application-data copy must be consistent; database remains in its original named volume.
subprocess.run(['docker', 'stop', containers[0]], check=True)
subprocess.run(['docker', 'cp', containers[0] + ':/openmrs/data/.', str(TARGET / 'data')], check=True)
spec = importlib.util.spec_from_file_location('prepare_backend', ROOT / 'scripts/prepare-backend.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
(ROOT / '.build/backend').mkdir(parents=True, exist_ok=True)
module.extract_dependencies()
print('Runtime exported and dependencies cached. No application image was built; original volumes remain unchanged.')
