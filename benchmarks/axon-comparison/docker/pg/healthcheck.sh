#!/bin/sh
# Healthy when this node is a writable primary, or a standby whose WAL receiver is streaming.
# A promoted replica therefore stays healthy; a standby cut off from its primary does not.
set -eu
pg_isready -q -U postgres -d "${POSTGRES_DB:-postgres}" || exit 1
ok=$(psql -U postgres -d "${POSTGRES_DB:-postgres}" -tAc \
  "select case when not pg_is_in_recovery() then 1
               when exists (select 1 from pg_stat_wal_receiver where status = 'streaming') then 1
               else 0 end")
[ "$ok" = "1" ]
