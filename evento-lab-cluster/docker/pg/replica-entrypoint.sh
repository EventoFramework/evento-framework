#!/bin/sh
# Entrypoint for a streaming replica (postgres:17-alpine).
#
# On an empty data directory it clones the primary with pg_basebackup -R (which writes
# standby.signal + primary_conninfo), then hands over to the stock docker-entrypoint.sh,
# which sees an initialised PGDATA and just starts the server as a hot standby.
#
# Required env:
#   PRIMARY_HOST, PRIMARY_PORT   where the primary is reached. Cross-cell replicas point at
#                                toxiproxy (toxi-<cell>:<port>), so replication pays WAN RTT.
#   STANDBY_NAME                 application_name reported to the primary; the name that
#                                synchronous_standby_names matches (e.g. es_pg_am).
#   REPLICATION_USER, REPLICATION_PASSWORD
#
# Promotion (S8/S9) is manual: docker exec <replica> psql -U postgres -c 'select pg_promote()'.
# A promoted replica keeps its data on restart (standby.signal is gone), so this script is a
# no-op the second time. Re-seeding a replica means removing its volume (scripts/down.sh).
set -eu

: "${PGDATA:=/var/lib/postgresql/data}"
: "${REPLICATION_USER:=replicator}"
: "${REPLICATION_PASSWORD:=replicator}"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "replica[$STANDBY_NAME]: waiting for primary at $PRIMARY_HOST:$PRIMARY_PORT"
  until pg_isready -q -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$REPLICATION_USER" -t 3; do
    sleep 1
  done

  mkdir -p "$PGDATA"
  chown postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"

  conninfo="host=$PRIMARY_HOST port=$PRIMARY_PORT user=$REPLICATION_USER password=$REPLICATION_PASSWORD application_name=$STANDBY_NAME keepalives_idle=10 keepalives_interval=5 keepalives_count=3"

  # The primary may still be finishing its first boot; retry the clone a few times.
  n=0
  until gosu postgres pg_basebackup -d "$conninfo" -D "$PGDATA" -R -X stream -c fast; do
    n=$((n + 1))
    if [ "$n" -ge 30 ]; then echo "replica[$STANDBY_NAME]: pg_basebackup failed" >&2; exit 1; fi
    echo "replica[$STANDBY_NAME]: pg_basebackup failed, retrying ($n)"
    rm -rf "${PGDATA:?}"/* 2>/dev/null || true
    sleep 2
  done

  # pg_basebackup -R already carries the connection string; restate it explicitly so the
  # application_name (what synchronous_standby_names matches) never depends on libpq's
  # choice of which conninfo keys to persist.
  echo "primary_conninfo = '$conninfo'" >> "$PGDATA/postgresql.auto.conf"
  echo "replica[$STANDBY_NAME]: base backup complete"
fi

exec docker-entrypoint.sh "$@"
