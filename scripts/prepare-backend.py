#!/usr/bin/env python3
"""Extract pinned binary dependencies, compile editable overrides; never build an image."""
import io
import json
import shutil
import subprocess
import sys
import os
import zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / '.build'
LOCK = json.loads((ROOT / 'versions.lock.json').read_text())

def run(*args):
    subprocess.run(args, check=True)

def extract_dependencies():
    inputs = BUILD / 'backend-inputs'
    inputs.mkdir(parents=True, exist_ok=True)
    cid = subprocess.check_output(['docker', 'create', LOCK['images']['backend']], text=True).strip()
    try:
        run('docker', 'cp', f'{cid}:/openmrs/distribution/openmrs_modules/.', str(inputs))
        run('docker', 'cp', f'{cid}:/openmrs/distribution/openmrs_core/openmrs.war', str(inputs / 'openmrs.war'))
        for jar in ['servlet-api.jar', 'jsp-api.jar', 'el-api.jar']:
            run('docker', 'cp', f'{cid}:/usr/local/tomcat/lib/{jar}', str(inputs / jar))
    finally:
        run('docker', 'rm', '-v', cid)
    deps = BUILD / 'deps'
    deps.mkdir(exist_ok=True)
    for jar in ['servlet-api.jar', 'jsp-api.jar', 'el-api.jar']:
        shutil.copy2(inputs / jar, deps / jar)
    for artifact in sorted(inputs.glob('*.omod')) + [inputs / 'openmrs.war']:
        with zipfile.ZipFile(artifact) as archive:
            for name in archive.namelist():
                if name.endswith('.jar'):
                    # Preserve each jar with an artifact prefix; do not overwrite different versions.
                    (deps / (artifact.stem + '--' + Path(name).name)).write_bytes(archive.read(name))
    with zipfile.ZipFile(inputs / 'openconceptlab-3.1.0.omod') as archive:
        data = archive.read('web/module/owas/openconceptlab.owa')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        archive.extractall(BUILD / 'backend/openconceptlab')

def prepare_ocl_owa():
    with zipfile.ZipFile(BUILD / 'backend-inputs/openconceptlab-3.1.0.omod') as archive:
        data = archive.read('web/module/owas/openconceptlab.owa')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        archive.extractall(BUILD / 'backend/openconceptlab')
    app = BUILD / 'backend/openconceptlab/app.bundle.min.js'
    content = app.read_text()
    replacements = {
        'ng-show=vm.subscription>': 'ng-show=true>',
        'ng-click=vm.startImportIfNoErrors()>': 'disabled ng-click=vm.startImportIfNoErrors()>',
        '<div ng-show=!vm.subscription': '<div ng-show=false',
        '<div ng-show=true>': '<p>Remote OCL imports are disabled in this environment. Import a local ZIP file containing export.json instead.</p><div ng-show=true>',
    }
    for old, new in replacements.items():
        if content.count(old) != 1:
            raise RuntimeError('OCL OWA source changed: ' + old)
        content = content.replace(old, new)
    content = content.replace('<a href="{{item.versionUrl }}">View in OCL</a>', '<span title="External OCL links are unavailable in this offline environment.">View in OCL (offline)</span>')
    app.write_text(content)
    # The activator redeploys this embedded OWA at startup, so patch it as well.
    packaged = io.BytesIO()
    with zipfile.ZipFile(packaged, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted((BUILD / 'backend/openconceptlab').rglob('*')):
            if file.is_file():
                archive.write(file, file.relative_to(BUILD / 'backend/openconceptlab').as_posix())
    module = BUILD / 'backend/openconceptlab-3.1.0.omod'
    temporary = module.with_suffix('.tmp')
    with zipfile.ZipFile(module) as source, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            target.writestr(entry, packaged.getvalue() if entry.filename == 'web/module/owas/openconceptlab.owa' else source.read(entry.filename))
    temporary.replace(module)


def main():
    BUILD.mkdir(exist_ok=True)
    shutil.copytree(ROOT / 'docker/backend', BUILD / 'backend', dirs_exist_ok=True)
    # Backend rebuilds must not overwrite the compiled frontend's content-addressed map.
    if not (BUILD / 'frontend/importmap.json').exists():
        shutil.copytree(ROOT / 'docker/frontend', BUILD / 'frontend', dirs_exist_ok=True)
    for source in (ROOT / 'source').iterdir():
        link = BUILD / source.name
        if not link.exists():
            link.symlink_to(source, target_is_directory=True)
    native = '--native' in sys.argv
    if not (BUILD / "deps/jsp-api.jar").exists():
        if native:
            raise RuntimeError('Native dependencies missing: run dev/export-runtime.py before removing baseline images')
        extract_dependencies()
    cp = '/work/backend-classes:/work/deps/*:' + ':'.join('/work/backend-inputs/' + p.name for p in sorted((BUILD / 'backend-inputs').glob('*.omod')))
    groups = [('backend-src', 'backend-classes'), ('controller-src', 'controller-classes'),
              ('core-src', 'core-classes'), ('legacyui-src', 'legacyui-classes'),
              ('patientdocuments-src', 'patientdocuments-classes'), ('cohort-src', 'cohort-classes'),
              ('logic-source', 'logic-classes'), ('ocl-src', 'ocl-classes'), ('ocl-web-src', 'ocl-web-classes'), ('owa-src', 'owa-classes'), ('calculation-src', 'calculation-classes')]
    # Mount repository root too, because .build source paths are symlinks into tracked source/.
    for folder, output in groups:
        paths = sorted(p for p in (ROOT / 'source' / folder).rglob('*.java') if '/src/test/' not in str(p))
        if not paths:
            raise RuntimeError('No Java sources in ' + folder)
        argfile = BUILD / (folder + '.txt')
        argfile.write_text('\n'.join(str(p) if native else '/repo/' + p.relative_to(ROOT).as_posix() for p in paths) + '\n')
        (BUILD / output).mkdir(exist_ok=True)
        if native:
            java_home = os.environ.get('JAVA_HOME')
            if not java_home:
                java_home = subprocess.check_output(['brew', '--prefix', 'openjdk@21'], text=True).strip()
            native_cp = cp.replace('/work/', str(BUILD) + '/')
            run(str(Path(java_home) / 'bin/javac'), '-proc:none', '--release',
                '21' if folder == 'cohort-src' else '8', '-cp', native_cp,
                '-d', str(BUILD / output), '@' + str(argfile))
        else:
            run('docker', 'run', '--rm', '--mount', 'type=tmpfs,destination=/openmrs/data', '--entrypoint', 'javac',
                '-v', str(ROOT) + ':/repo:ro', '-v', str(BUILD) + ':/work', LOCK['images']['backend'],
                '-proc:none', '--release', '21' if folder == 'cohort-src' else '8', '-cp', cp, '-d', '/work/' + output, '@/work/' + argfile.name)
    for name in ['assemble-backend.py', 'package-logic.py']:
        shutil.copy2(ROOT / 'scripts' / name, BUILD / name)
        run(sys.executable, str(BUILD / name))
    run(sys.executable, str(ROOT / 'scripts/package-cohort.py'))
    prepare_ocl_owa()
    print('Compiled and packaged .build/backend. No Docker image was built or deployed.')

if __name__ == '__main__':
    main()
