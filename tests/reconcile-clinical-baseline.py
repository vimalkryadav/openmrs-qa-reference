"""Guarded clinical fixture reconciliation; default mode is read-only.

Requires PyMySQL 1.1.2 in the test environment. The immutable seed and reviewed
provenance evidence are mandatory. --rollback exercises the entire transaction
without committing; --apply commits only the exact verified dependency closure.
"""
import argparse
import base64
from collections import defaultdict
import hashlib
import itertools
import json
from pathlib import Path
import sqlite3
import sys

import pymysql

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--seed', type=Path, required=True)
parser.add_argument('--evidence', type=Path, required=True)
parser.add_argument('--output', type=Path, default=ROOT / 'test-results/clinical-reconciliation')
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--apply', action='store_true')
mode.add_argument('--rollback', action='store_true')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
evidence = json.loads(args.evidence.read_text())
config = json.loads((ROOT / 'dev/config.json').read_text())
properties = dict(line.split('=', 1) for line in (ROOT / '.native/data/openmrs-runtime.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
seed = sqlite3.connect(args.seed.resolve().as_uri() + '?mode=ro&immutable=1', uri=True)
seed.row_factory = sqlite3.Row
seed_stat = (args.seed.stat().st_size, args.seed.stat().st_mtime_ns)
connection = pymysql.connect(host=config['database_host'], port=config['database_port'],
                             user=properties['connection.username'], password=properties['connection.password'],
                             database='openmrs', charset='utf8mb4', autocommit=False)
mutating = args.apply or args.rollback
suffix = ' FOR UPDATE' if mutating else ''

def query(sql, values=()):
    with connection.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute(sql, values)
        return cursor.fetchall()

def safe(name):
    assert name.replace('_', '').isalnum(), name
    return '`' + name + '`'

def recovery(value):
    if isinstance(value, bytes):
        return {'encoding': 'base64', 'data': base64.b64encode(value).decode()}
    return str(value)

columns = defaultdict(list)
for row in query("SELECT table_name,column_name,column_key,data_type FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position"):
    columns[row['table_name']].append(row)
primary = {table: [c['column_name'] for c in cols if c['column_key'] == 'PRI'] for table, cols in columns.items()}
constraints = defaultdict(list)
for row in query("SELECT table_name,column_name,referenced_table_name,referenced_column_name,constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL ORDER BY table_name,constraint_name,ordinal_position"):
    constraints[(row['table_name'], row['constraint_name'])].append(row)
incoming = defaultdict(list)
for group in constraints.values():
    incoming[group[0]['referenced_table_name']].append(group)
seed_tables = {r[0] for r in seed.execute("SELECT name FROM sqlite_master WHERE type='table'")}
rows = {}
edges = defaultdict(set)
ledger = {'mode': 'apply' if args.apply else 'rollback' if args.rollback else 'plan',
          'seedName': args.seed.name, 'seedBytes': seed_stat[0], 'rows': [], 'actions': [], 'keyChecks': {}}
output = args.output / (ledger['mode'] + '.json')

def persist():
    output.write_text(json.dumps(ledger, indent=2, default=recovery))

def identity(table, row):
    assert primary[table], ('No primary key', table)
    return table, tuple(row[k] for k in primary[table])

def add(table, row):
    key = identity(table, row)
    if key in rows:
        return key
    assert len(rows) < 500, 'Dependency closure exceeds reviewed fixture scope'
    if table in seed_tables:
        seed_columns = {r[1] for r in seed.execute('PRAGMA table_info(' + safe(table) + ')')}
        assert set(primary[table]) <= seed_columns, ('Cannot compare seed primary key', table)
        where = ' AND '.join(safe(k) + '=?' for k in primary[table])
        assert not seed.execute('SELECT 1 FROM ' + safe(table) + ' WHERE ' + where,
                                tuple(row[k] for k in primary[table])).fetchone(), ('Refusing seed primary key', key)
        if 'uuid' in row and 'uuid' in seed_columns:
            assert not seed.execute('SELECT 1 FROM ' + safe(table) + ' WHERE uuid=?', (row['uuid'],)).fetchone(), ('Refusing seed UUID', table, row['uuid'])
    rows[key] = row
    for group in incoming[table]:
        child_table = group[0]['table_name']
        values = tuple(row[c['referenced_column_name']] for c in group)
        if any(v is None for v in values):
            continue
        where = ' AND '.join(safe(c['column_name']) + '=%s' for c in group)
        for child in query('SELECT * FROM ' + safe(child_table) + ' WHERE ' + where + suffix, values):
            child_key = add(child_table, child)
            if child_key != key:
                edges[key].add(child_key)
    return key

def key_check(table, expected_extra):
    """Stream matching typed PK/UUID pairs; never write or materialize a full dump."""
    if table not in seed_tables:
        return {'seedTableAbsent': True, 'extraRows': len(expected_extra)}
    seed_columns = {r[1] for r in seed.execute('PRAGMA table_info(' + safe(table) + ')')}
    fields = primary[table][:]
    if 'uuid' in seed_columns and any(c['column_name'] == 'uuid' for c in columns[table]) and 'uuid' not in fields:
        fields.append('uuid')
    projection = ','.join(map(safe, fields))
    order = ','.join(map(safe, primary[table]))
    left = seed.execute('SELECT ' + projection + ' FROM ' + safe(table) + ' ORDER BY ' + order)
    digest_seed, digest_reference = hashlib.sha256(), hashlib.sha256()
    count = 0
    with connection.cursor(pymysql.cursors.SSCursor) as cursor:
        cursor.execute('SELECT ' + projection + ' FROM ' + safe(table) + ' ORDER BY ' + order)
        def reference_rows():
            for row in cursor:
                if tuple(row[:len(primary[table])]) not in expected_extra:
                    yield row
        for a, b in itertools.zip_longest(left, reference_rows()):
            assert a is not None and b is not None and tuple(a) == tuple(b), ('Seed key mismatch', table, tuple(a) if a is not None else None, b)
            encoded = json.dumps(tuple(a), separators=(',', ':'), default=str).encode()
            digest_seed.update(encoded); digest_reference.update(encoded)
            count += 1
    result = {'seedRows': count, 'excludedExtraRows': len(expected_extra), 'sha256': digest_seed.hexdigest(),
              'matchesSeedPrimaryKeyAndUuidSet': digest_seed.digest() == digest_reference.digest()}
    print(table, result['seedRows'], 'seed keys verified', flush=True)
    return result

try:
    assert query('SELECT @@foreign_key_checks AS value')[0]['value'] == 1
    roots = {'person': [r['uuid'] for r in evidence['people']],
             'encounter': [r['uuid'] for r in evidence['encounters']],
             'visit': [r['uuid'] for r in evidence['visits'] if not r['inSeed']]}
    assert {t: len(v) for t, v in roots.items()} == {'person': 8, 'encounter': 3, 'visit': 1}
    additional = evidence.get('additionalRoots', {})
    assert set(additional) <= {'patientflags_patient_flag'}
    roots.update(additional)
    for table, uuids in roots.items():
        found = query('SELECT * FROM ' + safe(table) + ' WHERE uuid IN (' + ','.join(['%s'] * len(uuids)) + ')' + suffix, uuids)
        assert {r['uuid'] for r in found} == set(uuids), ('Reviewed roots changed', table)
        for row in found:
            add(table, row)
    tables = sorted({t for t, pk in rows})
    engines = {r['table_name']: r['engine'] for r in query("SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE()")}
    assert all(engines[t] == 'InnoDB' for t in tables), 'All candidate tables must be transactional InnoDB'
    # Some modules omit formal FKs; conventional references must be covered too.
    conventional = {'person_id': 'person', 'patient_id': 'patient', 'encounter_id': 'encounter',
                    'visit_id': 'visit', 'obs_id': 'obs', 'patient_appointment_id': 'patient_appointment',
                    'appointment_id': 'patient_appointment'}
    declared = {(c['table_name'], c['column_name']) for group in constraints.values() for c in group}
    for table, metadata in columns.items():
        for column in metadata:
            field = column['column_name']; target = conventional.get(field)
            if target is None or (table, field) in declared or column['column_key'] == 'PRI':
                continue
            target_keys = [pk[0] for t, pk in rows if t == target]
            if not target_keys:
                continue
            found = query('SELECT * FROM ' + safe(table) + ' WHERE ' + safe(field) + ' IN (' + ','.join(['%s'] * len(target_keys)) + ')' + suffix, target_keys)
            assert all(identity(table, row) in rows for row in found), ('Unaccounted conventional dependency', table, field)
    ledger['transactionalTables'] = {t: engines[t] for t in tables}
    ledger['conventionalReferencesCovered'] = True
    triggers = query('SELECT trigger_name,event_object_table FROM information_schema.triggers WHERE trigger_schema=DATABASE()')
    assert not [r for r in triggers if r['event_object_table'] in tables], 'Review target-table triggers before reconciliation'
    ledger['rows'] = [{'table': t, 'primaryKey': dict(zip(primary[t], pk)), 'row': row} for (t, pk), row in rows.items()]
    ledger['tableSchema'] = {t: columns[t] for t in tables}
    ledger['counts'] = {t: sum(k[0] == t for k in rows) for t in tables}
    ledger['foreignKeyChecks'] = 1
    persist()
    for table in tables:
        extras = {pk for t, pk in rows if t == table}
        ledger['keyChecks'][table] = key_check(table, extras)
        persist()
    # These shared baseline parents must survive byte-for-byte in the native database.
    parents = {'person': query('SELECT * FROM person WHERE person_id=1392354'),
               'patient': query('SELECT * FROM patient WHERE patient_id=1392354'),
               'visit': query('SELECT * FROM visit WHERE visit_id=125047')}
    ledger['preservedParentsBefore'] = parents
    order, visiting, visited = [], set(), set()
    def visit(key):
        if key in visited:
            return
        assert key not in visiting, ('Dependency cycle requires explicit review', key)
        visiting.add(key)
        for child in edges[key]:
            visit(child)
        visiting.remove(key); visited.add(key); order.append(key)
    for key in rows:
        visit(key)
    if mutating:
        for table, pk in order:
            where = ' AND '.join(safe(k) + '=%s' for k in primary[table])
            original = rows[(table, pk)]
            assert query('SELECT * FROM ' + safe(table) + ' WHERE ' + where + ' FOR UPDATE', pk) == [original]
            with connection.cursor() as cursor:
                cursor.execute('DELETE FROM ' + safe(table) + ' WHERE ' + where, pk)
                assert cursor.rowcount == 1, ('Unexpected deletion count', table, pk)
            ledger['actions'].append({'table': table, 'primaryKey': dict(zip(primary[table], pk)), 'uuid': original.get('uuid')})
        after = {'person': query('SELECT * FROM person WHERE person_id=1392354'),
                 'patient': query('SELECT * FROM patient WHERE patient_id=1392354'),
                 'visit': query('SELECT * FROM visit WHERE visit_id=125047')}
        assert parents == after, 'Seed parent fields changed'
        ledger['preservedParentsIdentical'] = True
        ledger['finalKeyChecks'] = {}
        for table in tables:
            ledger['finalKeyChecks'][table] = key_check(table, set())
        if args.apply:
            connection.commit(); ledger['committed'] = True
        else:
            connection.rollback(); ledger['committed'] = False
        ledger['foreignKeyChecksAfter'] = query('SELECT @@foreign_key_checks AS value')[0]['value']
    else:
        connection.rollback()
    assert seed_stat == (args.seed.stat().st_size, args.seed.stat().st_mtime_ns), 'Seed file changed'
    ledger['seedFileUnchanged'] = True
    persist()
    print(json.dumps({'mode': ledger['mode'], 'rows': len(rows), 'counts': ledger['counts'], 'committed': ledger.get('committed', False)}, indent=2))
except BaseException as error:
    connection.rollback()
    ledger['error'] = str(error); ledger['committed'] = False; persist()
    raise
finally:
    connection.close(); seed.close()
