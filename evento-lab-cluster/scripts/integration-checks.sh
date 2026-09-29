#!/usr/bin/env bash
# Integration checks of a running lab: prints a Markdown evidence report on stdout.
#
#   scripts/integration-checks.sh > results/_integration/<tag>/checks-raw.md
#
# Read-only except for the probes it writes on purpose: one order per region (orderId
# it-<tag>-<epoch>-<REGION>) and prices (sku it-<tag>-<epoch>-*) posted through the lb. Safe on a stack
# where scenarios already ran; it only reads its own rows.
#
# Sections: images and framework features (/whoami), bundle presence per server, consumer ids per
# context (/api/consumer on each server GUI), the ALL lane (checkpoints, lock holders sampled
# during a burst), lb routing for the 7 regions, in-cell reads, the price fan-out, WAN toxics, ES
# and app replication, and the connection budget on the ES primary.
set -uo pipefail
. "$(dirname "$0")/_lib.sh"

tag="$LAB_TAG"
run="$tag-$(date +%s)"   # probe ids are unique per run, so a re-run never reads old rows
auth="evento:secret"
REGIONS=(EUROPA AFRICA AMERICA SUD_AMERICA CINA INDIA AUSTRALIA_NZ)
PORTS=(13000 13001 13002)
SERVERS=(evento-eu evento-am evento-apac)

py() { python -c "$@"; }
psql_es() { docker exec es-pg-eu psql -U postgres -d "${2:-evento}" -tAc "$1" 2>&1; }
jvm_get() {  # GET a path on a JVM that has no published port
  docker exec "$1" bash -c "exec 3<>/dev/tcp/127.0.0.1/8080; printf 'GET $2 HTTP/1.0\r\n\r\n' >&3; cat <&3" 2>/dev/null | tail -1
}
code() { printf '```\n%s\n```\n\n' "$1"; }

echo "# Integration checks: \`$tag\` ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
echo
echo "## Images"
code "$(docker ps --filter label=com.docker.compose.project=evento-lab-cluster --format '{{.Names}} {{.Image}}' | sort)"
for img in server order-command order-query order-saga order-observer order-gateway; do
  printf -- '- `evento-lab-cluster/%s:%s` framework-ref `%s`\n' "$img" "$tag" \
    "$(docker inspect -f '{{index .Config.Labels "evento.lab.framework-ref"}}' "evento-lab-cluster/$img:$tag" 2>/dev/null)"
done
echo

echo "## Framework features per deployment (/whoami)"
echo
echo "| container | zone | contexts | consumer plan | features (available) | bus |"
echo "|---|---|---|---|---|---|"
for c in $(docker ps --filter label=com.docker.compose.project=evento-lab-cluster --format '{{.Names}}' \
            | grep -E '^(order-|gateway-)' | sort); do
  jvm_get "$c" /whoami | py "
import json,sys
try: w=json.load(sys.stdin)
except Exception: print('| $c | ? | ? | ? | whoami failed | ? |'); sys.exit()
f=', '.join('%s=%s'%(x['feature'],'yes' if x['available'] else 'NO') for x in w.get('frameworkFeatures',[]))
print('| $c | %s | %s | %s | %s | %s %s |'%(w.get('zone'),w.get('contexts'),w.get('consumerPlan'),f,w.get('server'),w.get('busState')))"
done
echo

echo "## Bundle presence (core__bundle_instance)"
code "$(psql_es "select server_instance_id, string_agg(instance_id, ' ' order by instance_id) from core__bundle_instance group by 1 order by 1" )"

echo "## Consumers per context (/api/consumer on each server)"
echo
echo "Instances are the ones live on *that* server (the dashboard view is local, G17)."
echo
for i in 0 1 2; do
  echo "### ${SERVERS[$i]} (:${PORTS[$i]})"
  code "$(curl -s -u "$auth" "localhost:${PORTS[$i]}/api/consumer/" | py "
import json,sys
for c in sorted(json.load(sys.stdin), key=lambda c:c['consumerId']):
    print('%-45s %-14s %s'%(c['consumerId'],c.get('context'),','.join(sorted(c['instances'])) or '-'))")"
done
echo "Catalog rows (ES primary):"
code "$(psql_es "select consumer_id, string_agg(instance_id, ',' order by instance_id) from core__consumer group by 1 order by 1")"
echo "- \`order-query_OrderProjector_1_EUROPA\` / \`_AFRICA\` instances: \`$(psql_es "select string_agg(distinct instance_id, ',') from core__consumer where consumer_id in ('order-query_OrderProjector_1_EUROPA','order-query_OrderProjector_1_AFRICA')")\`"
echo "- \`order-query_CatalogProjector_1_ALL\` consumer ids: $(psql_es "select count(distinct consumer_id) from core__consumer where consumer_id like '%CatalogProjector%'"), registered instances: \`$(psql_es "select string_agg(instance_id, ',' order by instance_id) from core__consumer where consumer_id = 'order-query_CatalogProjector_1_ALL'")\`"
echo "- wildcard lanes: \`$(psql_es "select string_agg(consumer_id||'@'||instance_id, ' ' order by 1) from core__consumer where right(consumer_id, 2) = '_*'")\`"
echo "- consumers with context ALL other than the CatalogProjector lane: $(psql_es "select count(*) from core__consumer where consumer_id like '%\_ALL' and consumer_id <> 'order-query_CatalogProjector_1_ALL'")"
echo

echo "## LB routing (/whoami through lb:8080 with X-Region)"
echo
echo "| X-Region | X-Lab-Region | served by | cell | server |"
echo "|---|---|---|---|---|"
for r in "${REGIONS[@]}"; do
  resp="$(curl -s -D - -H "X-Region: $r" localhost:8080/whoami | tr -d '\r')"
  hdr="$(printf '%s' "$resp" | sed -n 's/^X-Lab-Region: //Ip')"
  printf '%s' "$resp" | tail -1 | py "
import json,sys
w=json.load(sys.stdin)
print('| $r | $hdr | %s | %s | %s |'%(w['instance'],w['cell'],w['server']))"
done
echo

echo "## Orders: create in each region, read in-cell and globally"
echo
echo "| region | create | in-cell GET (projected by) | global GET | GET from another cell |"
echo "|---|---|---|---|---|"
for r in "${REGIONS[@]}"; do
  id="it-$run-$r"
  cr="$(curl -s -X POST -H "X-Region: $r" -H 'Content-Type: application/json' \
        -d "{\"orderId\":\"$id\",\"items\":[{\"sku\":\"it-$run-price\",\"qty\":1}]}" localhost:8080/orders)"
  lat="$(printf '%s' "$cr" | py "import json,sys; d=json.load(sys.stdin); print('%s ms'%d.get('latencyMs','?') if 'orderId' in d else 'ERR '+str(d)[:80])" 2>/dev/null || echo "ERR ${cr:0:80}")"
  other=EUROPA; case "$r" in EUROPA|AFRICA) other=CINA ;; esac
  local_get="-"; global_get="-"
  for _ in $(seq 1 30); do
    local_get="$(curl -s -H "X-Region: $r" "localhost:8080/orders/$id" | py "import json,sys; d=json.load(sys.stdin); print('%s (%s)'%(d['status'],d['updatedBy']) if 'status' in d else 'missing')" 2>/dev/null)"
    global_get="$(curl -s -H "X-Region: $r" "localhost:8080/orders/global/$id" | py "import json,sys; d=json.load(sys.stdin); print('%s (%s)'%(d['status'],d['updatedBy']) if 'status' in d else 'missing')" 2>/dev/null)"
    [ "$local_get" != missing ] && [ "$global_get" != missing ] && break
    sleep 1
  done
  xcell="$(curl -s -o /dev/null -w '%{http_code}' -H "X-Region: $other" "localhost:8080/orders/$id")"
  echo "| $r | $lat | $local_get | $global_get | $other: HTTP $xcell |"
done
echo
echo "Zones known to each server (/api/cluster-status/zones):"
echo
for i in 0 1 2; do
  echo "- ${SERVERS[$i]}: \`$(curl -s -u "$auth" "localhost:${PORTS[$i]}/api/cluster-status/zones" 2>/dev/null | head -c 600)\`"
done
echo "- pick strategy: $(for s in "${SERVERS[@]}"; do docker logs "$s" 2>&1 | grep -o 'pick_strategy=[A-Z_]*' | tail -1; done | sort | uniq -c | tr '\n' ' ')"
echo

echo "## Global price fan-out (POST /catalog/prices, context ALL)"
echo
sku="it-$run-price"
post="$(curl -s -w ' HTTP %{http_code}' -X POST -H 'X-Region: INDIA' -H 'Content-Type: application/json' \
       -d "{\"sku\":\"$sku\",\"priceCents\":4242,\"currency\":\"EUR\"}" localhost:8080/catalog/prices)"
echo "POST via INDIA: \`$post\`"
echo
t0=$(date +%s)
for _ in $(seq 1 60); do
  n=0
  for db in eu am apac; do curl -s "localhost:8080/catalog/prices/$sku?db=$db" | grep -q '"priceCents":4242' && n=$((n+1)); done
  [ $n -eq 3 ] && break
  sleep 1
done
echo "Visible in $n of 3 regional databases after $(( $(date +%s) - t0 )) s."
echo
echo "| database | appliedEvents | staleSkips | lastSeq | updatedBy |"
echo "|---|---|---|---|---|"
for db in eu am apac; do
  curl -s "localhost:8080/catalog/prices/$sku?db=$db" | py "
import json,sys
d=json.load(sys.stdin)
print('| $db | %s | %s | %s | %s |'%(d.get('appliedEvents'),d.get('staleSkips'),d.get('lastSeq'),d.get('updatedBy')))" 2>/dev/null || echo "| $db | missing | | | |"
done
echo

echo "## The ALL lane: one checkpoint, one holder at a time"
echo
echo "Checkpoint rows in \`consumers\`:"
code "$(psql_es "select consumer_id, last_sequence, updated_at from evento_v2_consumer_state order by 1" consumers)"
tmp="$(mktemp)"
( for _ in $(seq 1 50); do
    psql_es "select coalesce(string_agg(host(a.client_addr)||'#'||l.objid, ' ' order by 1), 'none')
             from pg_locks l join pg_stat_activity a on a.pid = l.pid
             where l.locktype = 'advisory' and l.granted and a.datname = 'consumers'" consumers
    sleep 0.2
  done > "$tmp" ) &
for i in $(seq 1 15); do
  curl -s -o /dev/null -X POST -H 'X-Region: EUROPA' -H 'Content-Type: application/json' \
    -d "{\"sku\":\"it-$run-burst-$i\",\"priceCents\":$((100 + i))}" localhost:8080/catalog/prices &
done
wait
echo "Advisory locks held in \`consumers\` during a burst of 15 prices (50 samples, 200 ms apart;"
echo "clients: $(for c in order-query-global-eu order-query-global-apac toxiproxy; do printf '%s=%s ' "$c" "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | tr -s ' ')"; done); the APAC replica reaches es-pg-eu through toxiproxy):"
code "$(sort "$tmp" | uniq -c)"
echo "Samples with more than one holder: $(awk '$0 != "none" && NF > 1' "$tmp" | wc -l) of $(wc -l < "$tmp")."
rm -f "$tmp"
echo
for c in app-pg-eu app-pg-am app-pg-apac; do
  echo "- $c: \`$(docker exec "$c" psql -U postgres -d app -tAc "select count(*)||' rows, max applied_events '||max(applied_events)||', stale_skips '||sum(stale_skips)||', writers '||string_agg(distinct updated_by, '+') from catalog_price where sku like 'it-$run-%'")\`"
done
echo

echo "## WAN toxics"
code "$(curl -s localhost:8474/proxies | py "
import json,sys
ps=json.load(sys.stdin)
for n,p in sorted(ps.items()):
    t=' '.join('%s:%s/%s'%(x['name'],x['attributes'].get('latency'),x['attributes'].get('jitter')) for x in p.get('toxics',[]))
    print('%-24s %-16s %-22s enabled=%s %s'%(n,p['listen'],p['upstream'],p['enabled'],t))")"

echo "## Replication"
echo "ES primary (es-pg-eu):"
code "$(psql_es "select application_name, state, sync_state, replay_lag from pg_stat_replication order by 1" postgres)"
for c in app-pg-eu app-pg-am app-pg-apac; do
  echo "- $c: \`$(docker exec "$c" psql -U postgres -tAc "select string_agg(application_name||' '||state||' '||sync_state||' lag='||coalesce(replay_lag::text,'0'), '; ') from pg_stat_replication")\`"
done
echo "- ES primary max event seq: $(psql_es 'select max(event_sequence_number) from es__events')"
for c in es-pg-am es-pg-apac; do
  echo "- $c in recovery: $(docker exec "$c" psql -U postgres -tAc 'select pg_is_in_recovery()'), max event seq: $(docker exec "$c" psql -U postgres -d evento -tAc 'select max(event_sequence_number) from es__events' 2>&1)"
done
for p in eu am apac; do
  echo "- app-pg-$p-r in recovery: $(docker exec "app-pg-$p-r" psql -U postgres -tAc 'select pg_is_in_recovery()'), order_view rows replica/primary: $(docker exec "app-pg-$p-r" psql -U postgres -d app -tAc 'select count(*) from order_view' 2>&1)/$(docker exec "app-pg-$p" psql -U postgres -d app -tAc 'select count(*) from order_view' 2>&1)"
done
echo

echo "## Connection budget on the ES primary"
echo
echo "- max_connections: $(psql_es 'show max_connections' postgres)"
echo "- lock pool per server: $(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' evento-eu | grep -E 'LOCK_POOL|HIKARI_MAXIMUM' | tr '\n' ' ')"
echo "- peak budget: 3 servers x (application pool + lock pool) + 2 ALL replicas x CONSUMER_STATE_DB_POOL_SIZE"
code "$(psql_es "select datname, coalesce(host(client_addr),'local') as addr, count(*) from pg_stat_activity where backend_type = 'client backend' group by 1, 2 order by 1, 2" postgres)"
echo "- total client backends now: $(psql_es "select count(*) from pg_stat_activity where backend_type = 'client backend'" postgres)"
echo

echo "## Catalog completeness (repair-catalog.sh --check)"
code "$("$(dirname "$0")/repair-catalog.sh" --check 2>&1; echo "exit=$?")"

echo "## Restarts and health"
code "$(docker ps -a --filter label=com.docker.compose.project=evento-lab-cluster --format '{{.Names}}' | sort | while read -r c; do
  printf '%-26s %-10s restarts=%s\n' "$c" "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c")" "$(docker inspect -f '{{.RestartCount}}' "$c")"; done)"
