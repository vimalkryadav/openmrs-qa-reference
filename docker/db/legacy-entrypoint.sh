#!/bin/sh
set -eu
marker=/var/lib/mysql-qa/.legacy-qa-20261007
/bin/sh /opt/demo-clock/mariadb-entrypoint.sh "$@" &
server_pid=$!
trap 'kill -TERM "$server_pid" 2>/dev/null || true; wait "$server_pid" || true; exit 143' TERM INT
if [ ! -f "$marker" ]; then
    attempts=0
    until mariadb --protocol=socket -uopenmrs -popenmrs openmrs -e 'SELECT 1' >/dev/null 2>&1; do
        if ! kill -0 "$server_pid" 2>/dev/null; then wait "$server_pid"; exit 1; fi
        attempts=$((attempts + 1))
        if [ "$attempts" -ge 1800 ]; then kill -TERM "$server_pid"; wait "$server_pid" || true; exit 1; fi
        sleep 1
    done
    if ! mariadb --protocol=socket -uopenmrs -popenmrs openmrs < /opt/legacy-qa/06-legacy-qa.sql; then
        kill -TERM "$server_pid"; wait "$server_pid" || true; exit 1
    fi
    touch "$marker"
fi
wait "$server_pid"
