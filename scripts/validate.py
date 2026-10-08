#!/usr/bin/env python3
"""Source-only gate; never executes Docker builds, releases, or deployment."""
import ast
import json
import re
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
for path in list((root / 'scripts').glob('*.py')) + list((root / 'tests').glob('*.py')) + list((root / 'dev').glob('*.py')):
    ast.parse(path.read_text(), filename=str(path))
for path in list((root / 'scripts').glob('*.sh')) + list((root / 'dev').glob('*.sh')):
    subprocess.run(['bash', '-n', str(path)], check=True)
lock = json.loads((root / 'versions.lock.json').read_text())
for image in lock['images'].values():
    assert re.search(r'@sha256:[a-f0-9]{64}$', image), image
for source in lock['sources'].values():
    assert re.fullmatch(r'[a-f0-9]{40}', source['ref']), source
workflow = (root / '.github/workflows/manual-release.yml').read_text()
assert re.search(r'^on:\n  workflow_dispatch:', workflow, re.M)
assert not re.search(r'^\s*(push|pull_request|schedule|repository_dispatch|workflow_run):', workflow, re.M)
assert 'if: inputs.publish' in workflow and 'default: false' in workflow
source_workflow = (root / '.github/workflows/source-validation.yml').read_text()
assert re.search(r'^on:\n  pull_request:', source_workflow, re.M)
assert not re.search(r'\b(docker|depot|buildx|aws|ecr|publish)\b', source_workflow, re.I)
assert 'python3 scripts/validate.py' in source_workflow
prohibited = {'.db', '.sqlite', '.jar', '.omod', '.class', '.zip', '.tar', '.gz'}
for folder in ['source', 'patches', 'docker', 'scripts', 'docs', 'tests', 'licenses', 'dev']:
    for path in (root / folder).rglob('*'):
        if path.is_file() and '__pycache__' not in path.parts:
            assert path.suffix not in prohibited, path
            assert 'node_modules' not in path.parts, path
            data = path.read_bytes()
            assert (b'/' + b'Users/') not in data and (b'/private/' + b'tmp/') not in data, path
print('PASS Python/Bash syntax, immutable source/image pins, manual-only image release and source-only PR workflow, portable source files and prohibited-artifact checks')
