#!/bin/sh
# Runs once, from /docker-entrypoint-initdb.d, on an empty primary data directory.
#
# - creates the replication role used by the streaming replicas (pg_basebackup -R);
# - opens pg_hba for physical replication from any container address;
# - records synchronous_standby_names with ALTER SYSTEM rather than a '-c' flag: the image's
#   temporary init server would otherwise wait forever for a standby that cannot connect yet
#   (it listens on the unix socket only), hanging CREATE ROLE. postgresql.auto.conf is read
#   by the final server start, after this script, so the sync standby applies from then on.
#   An empty SYNC_STANDBY_NAMES (the regional app databases) means asynchronous replicas;
# - creates the databases listed in EXTRA_DATABASES (space or comma separated), e.g. the
#   'consumers' database of the event-store cluster, which holds the consumer state of the
#   global ALL lane. Physical replication copies them to every standby.
#
# Works whether the entrypoint sources the file or executes it (no 'exit').
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE ${REPLICATION_USER:-replicator} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD:-replicator}';
ALTER SYSTEM SET synchronous_standby_names = '${SYNC_STANDBY_NAMES:-}';
EOSQL

for db in $(echo "${EXTRA_DATABASES:-}" | tr ',' ' '); do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE \"$db\""
  echo "primary-init: database $db created"
done

echo "host replication ${REPLICATION_USER:-replicator} all scram-sha-256" >> "$PGDATA/pg_hba.conf"
echo "primary-init: replication role ready, synchronous_standby_names='${SYNC_STANDBY_NAMES:-}'"
