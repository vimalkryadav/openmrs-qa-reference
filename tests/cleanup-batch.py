"""Clean only batch-one UUIDs and verify no owned records remain."""
import json
import re
import subprocess
from pathlib import Path
import setup

root = Path(__file__).resolve().parents[1]
setup.ROOT = root / 'test-results/batch-one'
s = setup.Site('ref', 8090)
ids = s.ids
prefix = 'QA Reports Fix R1 20261008'
browser = json.loads((setup.ROOT / 'browser-results.json').read_text())
ledger = {'requests': browser['requests'], 'designs': [], 'resources': [], 'definitions': []}
for report in [ids['report'], ids['textReport'], ids['capReport']]:
    designs = s.get('ws/rest/v1/reportingrest/reportDesign', params={'reportDefinitionUuid': report, 'v': 'full'}, headers={'Accept': 'application/json'}).json()['results']
    for design in designs:
        assert design['name'].startswith(prefix)
        ledger['designs'].append(design['uuid'])
        ledger['resources'].extend(x['uuid'] for x in design.get('resources', []))
for uuid in ledger['requests']:
    if s.get('ws/rest/v1/reportingrest/reportRequest/' + uuid, headers={'Accept': 'application/json'}).status_code == 404:
        continue
    response = s.c.delete(s.path('ws/rest/v1/reportingrest/reportRequest/' + uuid))
    assert response.status_code in (200, 204, 404), response.status_code
    assert s.get('ws/rest/v1/reportingrest/reportRequest/' + uuid, headers={'Accept': 'application/json'}).status_code == 404
if ids['processor'] in s.get('module/reporting/reports/manageReportProcessors.form').text:
    assert s.get('module/reporting/reports/deleteReportProcessor.form', params={'uuid': ids['processor']}).status_code == 302
for uuid in ledger['designs']:
    s.post('module/reporting/reports/deleteReportDesign.form', {'uuid': uuid})
for key, kind in [('report', 'report.definition.ReportDefinition'), ('textReport', 'report.definition.ReportDefinition'), ('capReport', 'report.definition.ReportDefinition'), ('capDataset', 'dataset.definition.IterableSqlDataSetDefinition'), ('dataset', 'dataset.definition.IterableSqlDataSetDefinition'), ('textDataset', 'dataset.definition.SqlDataSetDefinition')]:
    uuid = ids[key]
    response = s.get('module/reporting/definition/purgeDefinition.form', params={'uuid': uuid, 'type': 'org.openmrs.module.reporting.' + kind})
    assert response.status_code in (200, 302), response.status_code
    ledger['definitions'].append(uuid)
config = dict(line.split('=', 1) for line in (root / '.native/data/openmrs-runtime.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
checks = [('serialized_object', ledger['definitions']), ('reporting_report_design', ledger['designs']), ('reporting_report_design_resource', ledger['resources']), ('reporting_report_processor', [ids['processor']]), ('reporting_report_request', ledger['requests'])]
queries = []
for table, uuids in checks:
    for uuid in uuids:
        assert re.fullmatch(r'[a-f0-9-]{36}', uuid), uuid
    if uuids:
        quoted = ','.join("'" + uuid + "'" for uuid in uuids)
        queries.append(f"SELECT '{table}',COUNT(*) FROM {table} WHERE uuid IN ({quoted})")
queries += [f"SELECT '{table}-prefix',COUNT(*) FROM {table} WHERE name LIKE '{prefix}%'" for table in ['serialized_object', 'reporting_report_design', 'reporting_report_processor']]
response = subprocess.run(['docker', 'exec', '-i', 'openmrs-qa-db-1', 'sh', '-c', 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; mariadb -u "$1" openmrs -N -e "$2"', 'sh', config['connection.username'], ';'.join(queries)], input=config['connection.password'] + '\n', text=True, capture_output=True, check=True)
ledger['residualCounts'] = dict(line.split('\t') for line in response.stdout.splitlines())
(setup.ROOT / 'cleanup-results.json').write_text(json.dumps(ledger, indent=2))
assert all(value == '0' for value in ledger['residualCounts'].values()), ledger['residualCounts']
print('PASS exact UUID and prefix residual counts:', ledger['residualCounts'])
