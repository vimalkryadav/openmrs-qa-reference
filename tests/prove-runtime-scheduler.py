"""Let the existing scheduler run one owned, bounded structured report at the frozen instant."""
import base64
import json
import os
from pathlib import Path
import time
import setup

setup.ROOT = Path(__file__).resolve().parents[1] / os.environ.get('REPORTS_OBJECT_EVIDENCE', 'test-results/runtime-o3')
s = setup.Site('ref', 8090)
ids = s.ids
report_uuid = ids['obsReport']
request = json.loads((setup.ROOT / 'o3-schedule-request.json').read_text())
assert request['parameterizable']['uuid'] == report_uuid
uuid = request['uuid']
assert uuid in ids['requests']
rest = 'ws/rest/v1/reportingrest/'
# This is the original unrelated schedule; it does not match Tuesday09:00 UTC.
others = setup.read_reference_sql("SELECT uuid,schedule FROM reporting_report_request WHERE status='SCHEDULED' AND uuid <> '"+uuid+"'")
assert others.strip() == '90bae275-1a69-4bdd-87ef-f08c7d47839c\t0 30 7 ? * 1,4', others
before = set(setup.read_reference_sql('SELECT uuid FROM reporting_report_request').splitlines())
body = {'uuid': uuid, 'reportDefinition': {'parameterizable': {'uuid': report_uuid}, 'parameterMappings': request['parameterMappings']}, 'renderingMode': {'argument': ids['obsCsv']}, 'schedule': '0 0 9 29 9 ? 2026'}
result = s.c.post(s.path(rest+'reportRequest'), json=body, headers={**s.token, 'Accept':'application/json'})
result.raise_for_status()
ledger = {'scheduleUuid': uuid, 'cron': body['schedule'], 'unrelatedScheduleBefore': others, 'globalClockChanged': False, 'instrumentation': None, 'newRequests': []}
try:
    for attempt in range(91):
        children = setup.read_reference_sql("SELECT uuid FROM reporting_report_request WHERE report_definition_uuid='"+report_uuid+"'").splitlines()
        new = [value for value in children if value not in before]
        if new:
            for value in new:
                if value not in ids['requests']: ids['requests'].append(value)
            s.remember('requests', ids['requests'])
            ledger['newRequests'] = new
            break
        if attempt % 15 == 0: print('Waiting for existing scheduler', attempt, flush=True)
        time.sleep(1)
    assert ledger['newRequests'], 'Existing scheduler did not enqueue within90 seconds'
    # Stop only our schedule before polling the actual queued worker/export.
    deleted = s.c.delete(s.path(rest+'reportRequest/'+uuid), headers=s.token)
    assert deleted.status_code in (200,204,404), deleted.text
    ledger['initialScheduleCleanupStatus'] = deleted.status_code
    for child in ledger['newRequests']:
        for attempt in range(91):
            evaluated = s.get(rest+'reportRequest/'+child, params={'v':'full'}, headers={'Accept':'application/json'}).json()
            if evaluated.get('status') in ('COMPLETED','FAILED'): break
            time.sleep(1)
        assert evaluated['status'] == 'COMPLETED', evaluated
        assert len(evaluated['parameterMappings']['runtimeColumns']) == 2
        output = s.get(rest+'downloadReport', params={'reportRequestUuid':child}, headers={'Accept':'application/json'}).json()
        csv = base64.b64decode(output['fileContent']).decode()
        expected = json.loads((setup.ROOT/'expected.json').read_text())['obs']
        assert csv.index(expected[1]) < csv.index(expected[0]), csv
        (setup.ROOT/('scheduled-'+child+'.csv')).write_text(csv)
        (setup.ROOT/('scheduled-'+child+'.json')).write_text(json.dumps(evaluated,indent=2))
    ledger['status'] = 'pass'
finally:
    remaining = s.get(rest+'reportRequest/'+uuid, headers={'Accept':'application/json'})
    if remaining.status_code != 404:
        deleted = s.c.delete(s.path(rest+'reportRequest/'+uuid), headers=s.token)
        assert deleted.status_code in (200,204), deleted.text
    ledger['scheduleCleanupStatus'] = s.get(rest+'reportRequest/'+uuid, headers={'Accept':'application/json'}).status_code
    assert ledger['scheduleCleanupStatus'] == 404
    ledger['unrelatedScheduleAfter'] = setup.read_reference_sql("SELECT uuid,schedule FROM reporting_report_request WHERE status='SCHEDULED'")
    assert ledger['unrelatedScheduleAfter'] == others
    (setup.ROOT/'scheduler-results.json').write_text(json.dumps(ledger,indent=2))
print(json.dumps(ledger,indent=2))
