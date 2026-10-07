"""Reapply editable cohort pagination controller/JSP overrides to pinned baseline."""
import io
import zipfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
build = root / '.build'
replacements = {p.relative_to(build / 'cohort-classes').as_posix(): p.read_bytes() for p in (build / 'cohort-classes').rglob('*.class')}
for p in (root / 'source/cohort-web').rglob('*.jsp'):
    replacements[p.relative_to(root / 'source/cohort-web').as_posix()] = p.read_bytes()
with zipfile.ZipFile(build / 'backend-inputs/cohort-3.7.3.omod') as source, zipfile.ZipFile(build / 'backend/cohort-3.7.3.omod', 'w', zipfile.ZIP_DEFLATED) as target:
    for entry in source.infolist():
        target.writestr(entry, replacements.pop(entry.filename, source.read(entry.filename)))
    for name, data in replacements.items():
        target.writestr(name, data)
