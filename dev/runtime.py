#!/usr/bin/env python3
"""Run the reference application natively; only MariaDB remains a Docker dependency."""
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
NATIVE = (ROOT / '.native').resolve()
CONFIG = json.loads((ROOT / 'dev/config.json').read_text())

def prefix(name):
    return Path(subprocess.check_output(['brew', '--prefix', name], text=True).strip())

def overlay():
    build = ROOT / '.build/backend'
    if not (build / 'reporting-2.1.0.omod').exists():
        raise RuntimeError('Compile current sources with python3 scripts/prepare-backend.py --native first')
    modules = NATIVE / 'data/modules'
    for module in build.glob('*.omod'):
        shutil.copy2(module, modules / module.name)
    shutil.copytree(build / 'openconceptlab', NATIVE / 'data/owa/openconceptlab', dirs_exist_ok=True)
    war = NATIVE / 'tomcat/webapps/openmrs.war'
    replacements = {p.relative_to(build / 'core').as_posix(): p.read_bytes() for p in (build / 'core').rglob('*.class')}
    temporary = war.with_suffix('.tmp')
    with zipfile.ZipFile(NATIVE / 'distribution/openmrs_core/openmrs.war') as source, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            target.writestr(entry, replacements.pop(entry.filename, source.read(entry.filename)))
        for name, data in replacements.items():
            target.writestr(name, data)
    temporary.replace(war)
    # Only generated native app expansion/cache; persistent data stays separate.
    for path in [NATIVE / 'tomcat/webapps/openmrs', NATIVE / 'tomcat/work/Catalina']:
        if path.exists():
            shutil.rmtree(path)
    frontend = ROOT / '.build/frontend'
    if (frontend / 'reports-dist/openmrs-esm-reports-app.js').exists():
        imports = json.loads((frontend / 'importmap.json').read_text())
        for package, folder in [('@openmrs/esm-reports-app', 'reports-dist'),
                                ('@openmrs/esm-openconceptlab-app', 'ocl-dist')]:
            if not (frontend / folder).exists():
                continue
            url = imports["imports"][package]
            target = NATIVE / 'frontend' / url.split('/openmrs/spa/')[-1].lstrip('/')
            shutil.copytree(frontend / folder, target.parent, dirs_exist_ok=True)
        shutil.copy2(frontend / 'importmap.json', NATIVE / 'frontend/importmap.json')

def configure():
    for saved_session in (NATIVE / 'tomcat/work').rglob('SESSIONS.ser'):
        saved_session.unlink()
    java = prefix('openjdk@21')
    shim = NATIVE / 'clock/timed-wait.dylib'
    subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', '-dynamiclib', str(ROOT / 'dev/macos-timed-wait.c'), '-o', str(shim)], check=True)
    faketime = prefix('libfaketime') / 'lib/faketime/libfaketime.1.dylib'
    server = NATIVE / 'tomcat/conf/server.xml'
    text = server.read_text().replace('port="8005"', 'port="-1"').replace('port="8080"', f'port="{CONFIG["tomcat_port"]}" address="127.0.0.1"')
    server.write_text(text)
    properties = NATIVE / 'data/openmrs-runtime.properties'
    text = properties.read_text()
    lines = text.splitlines()
    lines = [('connection.url=jdbc\\:mysql\\://' + CONFIG['database_host'] + '\\:' + str(CONFIG['database_port']) + '/openmrs?autoReconnect\\=true&sessionVariables\\=default_storage_engine\\=InnoDB&useUnicode\\=true&characterEncoding\\=UTF-8') if line.startswith('connection.url=') else line for line in lines]
    properties.write_text('\n'.join(lines) + '\n')
    # These two persisted module settings are filesystem paths, not clinical data.
    path_updates = {'owa.appFolderPath': NATIVE / 'data/owa',
                    'openconceptlab.oclLoadAtStartupPath': NATIVE / 'data/ocl/configuration/loadAtStartup'}
    statements = []
    for name, value in path_updates.items():
        escaped = str(value).replace("'", "''")
        statements.append(f"UPDATE global_property SET property_value='{escaped}' WHERE property='{name}';")
    subprocess.run(['docker', 'exec', '-i', 'openmrs-qa-db-1', 'mariadb', '-uopenmrs', '-popenmrs', 'openmrs'],
                   input='\n'.join(statements), text=True, check=True)
    setenv = f'''export JAVA_HOME="{java}"
export JAVA_OPTS="-Dfile.encoding=UTF-8 -server -Djava.awt.headless=true -Duser.timezone=UTC"
export CATALINA_OPTS="{CONFIG['heap']} -DOPENMRS_APPLICATION_DATA_DIRECTORY={NATIVE / 'data'}/"
export TZ=UTC
export DYLD_INSERT_LIBRARIES="{shim}:{faketime}"
export FAKETIME="{CONFIG['clock']}"
export FAKETIME_DONT_FAKE_MONOTONIC=1
export FAKETIME_FORCE_MONOTONIC_FIX=0
export FAKETIME_DONT_FAKE_STAT=1
'''
    (NATIVE / 'tomcat/bin/setenv.sh').write_text(setenv)
    mime = Path(subprocess.check_output(['brew', '--prefix'], text=True).strip()) / 'etc/nginx/mime.types'
    nginx = f'''worker_processes 1;
pid "{NATIVE / 'nginx.pid'}";
error_log "{NATIVE / 'logs/nginx-error.log'}";
events {{ worker_connections 1024; }}
http {{
 map $request_uri $reference_csp {{
  default "default-src 'self' 'unsafe-inline' 'unsafe-eval' localhost localhost:*; base-uri 'self'; font-src 'self'; img-src 'self' data:; frame-ancestors 'self';";
  "~^/openmrs/(?:admin|dictionary|module|patientDashboard.form)/" "default-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; base-uri 'self'; font-src 'self'; frame-ancestors 'self';";
  "~^/openmrs/owa" "default-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; base-uri 'self'; font-src 'self' data:; img-src 'self' data:; frame-ancestors 'self';";
 }}
 include "{mime}";
 default_type application/octet-stream;
 access_log "{NATIVE / 'logs/nginx-access.log'}";
 server {{
  listen 127.0.0.1:{CONFIG['frontend_port']};
  root "{NATIVE / 'frontend'}";
  location / {{ try_files $uri $uri/ /index.html; }}
 }}
 server {{
  listen 127.0.0.1:{CONFIG['port']};
  absolute_redirect off;
  add_header Content-Security-Policy $reference_csp;
  add_header X-XSS-Protection "1; mode=block";
  add_header X-Content-Type-Options nosniff;
  proxy_set_header Host $http_host;
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header Accept-Encoding "";
  proxy_http_version 1.1;
  location = / {{ return 302 /openmrs/spa/home; }}
  location = /openmrs/spa {{ return 302 /openmrs/spa/home; }}
  location = /openmrs/spa/ {{ return 302 /openmrs/spa/home; }}
  location = /openmrs/demo-clock-config.js {{ default_type application/javascript; add_header Cache-Control no-store; return 200 'window.__OPENMRS_DEMO_TIME__ = "{CONFIG['clock']}";'; }}
  location = /openmrs/demo-clock.js {{ default_type application/javascript; add_header Cache-Control no-store; alias "{ROOT / 'dev/demo-clock.js'}"; }}
  location /openmrs/spa/ {{
   rewrite ^/openmrs/spa/(.*) /$1 break;
   proxy_pass http://127.0.0.1:{CONFIG['frontend_port']};
   sub_filter_once on;
   sub_filter '<head>' '<head><script src="/openmrs/demo-clock-config.js"></script><script src="/openmrs/demo-clock.js"></script>';
  }}
  location /openmrs {{ proxy_read_timeout 300s; proxy_pass http://127.0.0.1:{CONFIG['tomcat_port']}; }}
 }}
}}
'''
    (NATIVE / 'nginx.conf').write_text(nginx)

def stop():
    path = NATIVE / 'processes.json'
    if not path.exists():
        return
    for record in json.loads(path.read_text()):
        pid = record['pid']
        command = subprocess.run(['ps', '-p', str(pid), '-o', 'command='], capture_output=True, text=True).stdout
        if not command.strip():
            continue
        if str(NATIVE) not in command and str(ROOT / '.native') not in command:
            raise RuntimeError(f'Process {pid} does not match this native runtime; retaining manifest')
        os.kill(pid, signal.SIGTERM)
        for _ in range(30):
            try:
                os.kill(pid, 0)
            except ProcessLookupError:
                break
            time.sleep(1)
        else:
            raise RuntimeError(f'Owned process {pid} did not stop; keeping process file for inspection')
    path.unlink()

def start():
    if (NATIVE / 'processes.json').exists():
        raise RuntimeError('Native process file exists; use stop then start to avoid duplicate processes')
    for port in [CONFIG['port'], CONFIG['tomcat_port'], CONFIG['frontend_port']]:
        listeners = subprocess.run(['lsof', '-nP', '-iTCP:' + str(port), '-sTCP:LISTEN'], capture_output=True, text=True)
        if listeners.returncode == 0:
            raise RuntimeError(f'Port {port} already has a listener; refusing a duplicate native server')
    configure()
    nginx = prefix('nginx') / 'bin/nginx'
    subprocess.run([str(nginx), '-t', '-c', str(NATIVE / 'nginx.conf'), '-p', str(NATIVE)], check=True)
    records = []
    for name, command in [('tomcat', [str(NATIVE / 'tomcat/bin/catalina.sh'), 'run']), ('nginx', [str(nginx), '-c', str(NATIVE / 'nginx.conf'), '-p', str(NATIVE), '-g', 'daemon off;'])]:
        log = open(NATIVE / 'logs' / (name + '.log'), 'a')
        proc = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        records.append({'name': name, 'pid': proc.pid})
    (NATIVE / 'processes.json').write_text(json.dumps(records, indent=2))
    print('Native processes started. OpenMRS startup can take several minutes; check .native/logs/tomcat.log.')

if __name__ == '__main__':
    action = sys.argv[1] if len(sys.argv) > 1 else 'status'
    if action == 'overlay':
        overlay()
    elif action == 'start':
        start()
    elif action == 'stop':
        stop()
    elif action == 'status':
        path = NATIVE / 'processes.json'
        print(path.read_text() if path.exists() else 'No native process file')
    else:
        raise SystemExit('Usage: python3 dev/runtime.py start|stop|status|overlay')
