# evento-lab-cluster

**IT** — Laboratorio di chaos testing per un deployment Evento multi-regione: 7 regioni
(contesti Evento) raggruppate in 3 celle, 3 evento-server attivi, due cluster Postgres
replicati (event store e dati applicativi), un load balancer "location-aware" e un proxy WAN
che simula latenze e partizioni tra continenti. Gli scenari (S0..S15) producono i risultati in
`results/` e servono come evidenza prima/dopo per le correzioni del framework.

**EN** — A chaos-testing lab for a multi-region Evento deployment: 7 regions (Evento contexts)
grouped into 3 cells, 3 active evento-servers, two replicated Postgres clusters (event store
and app data), a location-aware load balancer and a WAN proxy that emulates inter-continental
latency and partitions. The scenarios (S0..S15) write their evidence to `results/`, captured
before and after each framework fix.

Design inputs, gap ids (G1..G25), invariants (I1..I8) and the scenario catalogue live in
[`.claude/CLUSTER-READINESS.md`](../.claude/CLUSTER-READINESS.md).

## Topology

| Cell | Regions (contexts) | Server | ES Postgres (`evento`) | App Postgres (`app`) |
|---|---|---|---|---|
| EU | EUROPA, AFRICA | `evento-eu` | `es-pg-eu` **primary** | `app-pg-eu` **primary** |
| AM | AMERICA, SUD_AMERICA | `evento-am` | `es-pg-am` sync standby | `app-pg-am` sync standby |
| APAC | CINA, INDIA, AUSTRALIA_NZ | `evento-apac` | `es-pg-apac` async standby | `app-pg-apac` async standby |

```
                               host: curl -H 'X-Region: CINA' localhost:8080/orders
                                              |
                          +-------------------v--------------------+
                          | lb (nginx)  http :8080  X-Region/?region=
                          |             stream VIPs bus-eu|am|apac:3030
                          +---+----------------+----------------+---+
                              |                |                |
  net-eu ---------------------+   net-am ------+   net-apac ----+-------------------------
  region-europa  region-africa |  region-america|  region-cina  region-india
  cell-core-eu (+eu-b)  saga-eu|  region-sud-am.|  region-australia-nz
          |                    |  cell-core-am  |  cell-core-apac  saga-apac
          v                    |       |        |       |
      evento-eu :3030          |   evento-am    |   evento-apac
          |                    |       |        |       |
      es-pg-eu (P) ==sync====> es-pg-am (S)     |   es-pg-apac (A)  <==async== es-pg-eu
      app-pg-eu (P) =sync====> app-pg-am (S)    |   app-pg-apac (A) <==async== app-pg-eu
                               |                |
  =========================== toxiproxy (toxi-eu / toxi-am / toxi-apac, API :8474) =======
      every cross-cell hop (server -> remote PG, replica -> primary, JVM -> remote server,
      JVM -> remote app-PG, VIP backup) goes through it:
          EU <-> AM 80 ms RTT   EU <-> APAC 150 ms RTT   AM <-> APAC 170 ms RTT   (+-5 ms/way)
```

A container is attached only to its own cell network, so it *cannot* reach another cell
except through toxiproxy. `docker network disconnect net-apac toxiproxy` therefore partitions
APAC cleanly (silently: no RST), and toxics on one proxy degrade exactly one WAN link.

### Containers, ports, memory

| Service | Role | Host port | mem |
|---|---|---|---|
| `es-pg-eu` / `es-pg-am` / `es-pg-apac` | event store PG 17 (db `evento`, `postgres`/`secret`) | 15432 / 15433 / 15434 | 384m |
| `app-pg-eu` / `app-pg-am` / `app-pg-apac` | app data PG 17 (db `app`, `postgres`/`secret`) | 25432 / 25433 / 25434 | 512m |
| `evento-eu` / `evento-am` / `evento-apac` | evento-server, CPES, GUI Basic `evento`/`secret` | 13000 / 13001 / 13002 | 512m |
| `cell-core-{eu,am,apac}` (+ `cell-core-eu-b`, profile `replica`) | bundles `cluster-command` + `cluster-query-global` | - | 384m |
| `region-{europa,africa,america,sud-america,cina,india,australia-nz}` | HTTP gateway :8080 + bundle `query-<region>` | via lb | 320m |
| `saga-eu`, `saga-apac` | bundle `cluster-saga` (competing consumers) | - | 320m |
| `lb` | nginx: HTTP geo routing + bus VIPs | 8080 | 32m |
| `toxiproxy` | WAN emulation, 18 proxies | 8474 (API) | 64m |
| `toxiproxy-init` | installs the baseline RTT toxics, then idles | - | 32m |

Total limits ~8.3 GB (8.7 GB with `replica`). Container names equal service names, so
`docker kill evento-apac` works as written in the scenarios.

### WAN proxies

Proxy `<src>-to-<svc>-<dst>` listens on `toxi-<src>:20000 + src*100 + dst*10 + svc`
(cells `eu=1 am=2 apac=3`; services `es-pg=1 app-pg=2 evento=3`), for example:

| Proxy | Address (from the source cell) | Upstream |
|---|---|---|
| `am-to-es-pg-eu` | `toxi-am:20211` | `es-pg-eu:5432` (also carries es-pg-am replication) |
| `apac-to-app-pg-eu` | `toxi-apac:20312` | `app-pg-eu:5432` |
| `eu-to-evento-apac` | `toxi-eu:20133` | `evento-apac:3030` |

Full list: [`docker/toxiproxy/toxiproxy.json`](docker/toxiproxy/toxiproxy.json). Baseline toxics
are named `wan-latency-up` / `wan-latency-down`; scenario toxics must use other names
(`chaos-*`) so healing never removes the baseline. `scripts/wan.sh [apply|off|show]`
reapplies / removes / shows them (`WAN_LATENCY_SCALE=200` doubles the matrix).

### Connection strings (inside the lab)

| Who | Setting |
|---|---|
| `evento-eu` | `SPRING_DATASOURCE_URL=jdbc:postgresql://es-pg-eu:5432,toxi-eu:20121,toxi-eu:20131/evento?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10` |
| `evento-am` | `...//toxi-am:20211,es-pg-am:5432,toxi-am:20231/evento?...` |
| `evento-apac` | `...//toxi-apac:20311,toxi-apac:20321,es-pg-apac:5432/evento?...` |
| EU app JVMs | `EVENTO_SERVER_ADDRESSES=evento-eu:3030,toxi-eu:20123,toxi-eu:20133`, `APP_DB_URL=jdbc:postgresql://app-pg-eu:5432,toxi-eu:20122,toxi-eu:20132/app?targetServerType=primary&...` |
| AM app JVMs | `evento-am:3030,toxi-am:20213,toxi-am:20233`, `toxi-am:20212,app-pg-am:5432,toxi-am:20232` |
| APAC app JVMs | `evento-apac:3030,toxi-apac:20313,toxi-apac:20323`, `toxi-apac:20312,toxi-apac:20322,app-pg-apac:5432` |

Servers: `EVENTO_CLUSTER_NODE_SERVER_ID` = `EVENTO_SERVER_INSTANCE_ID` = service name,
`EVENTO_CLUSTER_NAME=evento-lab-cluster`, `EVENTO_ES_MODE=CPES`, Hikari pool 20.
App JVMs: `EVENTO_ROLE` (`cell-core` | `region` | `saga-observer`), `CELL`, `REGION` (regions),
`EVENTO_BUNDLE_INSTANCE_ID` (= service name), `APP_DB_USER`/`APP_DB_PASSWORD`,
`CONSUMER_CHECKPOINT_MODE` (default `DEFAULT`). All JVMs:
`JAVA_OPTS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k` (override with `LAB_JAVA_OPTS`).

From the host: `jdbc:postgresql://localhost:15432,localhost:15433,localhost:15434/evento?targetServerType=primary`
(event store) and the same on 25432..25434 for `app`.

### Load balancer

* **HTTP :8080** routes on `X-Region` (or `?region=`; case-insensitive, `SUD_AMERICA` /
  `sud-america` both accepted) to `region-<r>:8080`; same-cell regions are `backup` servers.
  No header = EUROPA. Responses carry `X-Lab-Region` (routing key) and `X-Lab-Upstream`
  (who served it). Only connect errors / 502-504 are retried on a backup, so the LB never
  replays a POST that reached a gateway. Health: `GET /lb/health`.
* **Stream VIPs** `bus-eu:3030`, `bus-am:3030`, `bus-apac:3030` (static lb addresses
  172.28.{1,2,3}.10 on each cell network): primary = own cell server, backups = the other two
  servers through toxiproxy. JVMs use them only with `BUS_VIA_LB=true` (see below).
* Upstreams use nginx `resolve`, so the lb starts without the app containers and follows
  kills/restarts. After re-attaching the lb to a network, restart it (`scripts/partition.sh`
  does) so it binds the VIP address again.

### Start-up order

`toxiproxy` -> `toxiproxy-init` (baseline WAN) and `es-pg-eu` / `app-pg-eu` -> replicas
(`pg_basebackup -R` through toxiproxy) -> `evento-eu` (waits for the sync standby, otherwise
every commit blocks) -> `evento-am` -> `evento-apac` (health chain against the concurrent
`schema.sql`, G14) -> cell cores, regions, sagas. `COLD_START=true` (S10) drops the chain.

## Build and run

Requirements: Docker with compose v2, JDK 25 for Gradle (the scripts find
`C:/Users/Gaborando/.jdks/openjdk-25.0.1`, `~/.jdks/*25*` or macOS `java_home -v 25`).
Every `.sh` works under Git Bash; the `.ps1` twins call the same script through Git Bash.

```bash
scripts/build-images.sh            # gradle bootJar (server + app, --offline) + docker build
                                   #   -> evento-lab-cluster/server:$LAB_TAG, evento-lab-cluster/app:$LAB_TAG
scripts/up.sh                      # full topology, waits for every healthcheck
scripts/run-scenario.sh S00BaselineTest before
scripts/run-scenario.sh all after
scripts/down.sh                    # removes containers, networks and volumes (-v)
```

| Variable | Effect |
|---|---|
| `LAB_TAG` (dev) | image tag used by build and compose |
| `GRADLE_ARGS` (`--offline`) | extra Gradle flags; `SKIP_GRADLE=true` reuses existing jars |
| `BUS_VIA_LB=true` | `up.sh` points the app JVMs at their cell VIP instead of the direct list (`BUS_ADDR_EU/AM/APAC` can also be set directly) |
| `REPLICA=true` | `up.sh` adds `cell-core-eu-b` (profile `replica`, S2) |
| `COLD_START=true` | `up.sh` adds `docker/compose.cold-start.yaml` (S10) |
| `CONSUMER_CHECKPOINT_MODE` | `DEFAULT` or `WATERMARK` for every consumer |
| `WAN_LATENCY_SCALE`, `WAN_JITTER_MS` | scale / jitter of the baseline WAN matrix |

`scripts/up.sh <service...>` starts a subset (with dependencies), e.g. only the data tier:
`scripts/up.sh toxiproxy-init es-pg-am es-pg-apac app-pg-am app-pg-apac lb`.

Manual fault helpers: `scripts/partition.sh <eu|am|apac> on [--with-lb]` / `off`;
`scripts/wan.sh off|apply`; promotion `docker exec es-pg-am psql -U postgres -c 'select pg_promote()'`.
After a promotion the topology is not restored automatically: `scripts/down.sh && scripts/up.sh`.

## Scenarios

Classes live in `evento-lab-cluster-chaos` (`S<nn><Name>Test`), are skipped unless
`EVENTO_LAB_CLUSTER=true` (set by `run-scenario.sh`), and write
`results/<scenario>/<label>/{summary.json,report.md}`. A run that exposes a known gap still
passes and records observed vs expected; only harness errors fail. Select with the class
name or a pattern: `scripts/run-scenario.sh 'S03*' before`.

| Id | Scenario | Fault | Gaps |
|---|---|---|---|
| S0 | Baseline | none, 7 regions x 20 orders/min | reference, G9 |
| S1 | Cross-cell routing miss | stop `cell-core-am` | G1 |
| S2 | Kill a handler replica in a cell | `docker kill cell-core-eu` (with `REPLICA=true`) | intra-server failover |
| S3 | Kill an evento-server | `docker kill evento-apac` | G2, G4, G5, G8, G13 |
| S4 | Server returns after failover | restart / pause > 45 s `evento-apac` | G7, G17 |
| S5 | Same aggregate from two cells | EUROPA + CINA contention, 150 ms RTT | G3, G6, G9 |
| S6 | WAN latency sweep | 0/50/150/300 ms to the ES primary | G9, G18 |
| S7 | Region partition | detach APAC for 60 s | G22, G25, G11 |
| S8 | ES primary failover | kill `es-pg-eu`, promote `es-pg-am` (sync) / `es-pg-apac` (async) | G3, G5, G6, G19 |
| S9 | App-DB primary failover | kill `app-pg-eu`, promote `app-pg-am` | G11, G25 |
| S10 | Cold start of 3 servers | simultaneous start on an empty DB | G14 |
| S11 | Underscore contexts | SUD_AMERICA / AUSTRALIA_NZ + `SUDXAMERICA` | G15, G16, L6 |
| S12 | Per-context WATERMARK cost | EUROPA consumer under global load | G10, G20 |
| S13 | Wrong-broker / identity | kill `evento-eu` behind VIPs, foreign cluster | G13 |
| S14 | Retry storm | retry CreateOrder through another cell | G5 |
| S15 | Clock skew (optional) | +40 s on one JVM | fetch staleness gate |

Suggested order: S0, S10, S11, S1, S2, S3, S4, S5, S6, S12, S7, S8, S9, S13, S14.

## Files

```
docker/
  Dockerfile                  generic JVM image (explicit jar via ARG JAR, L1)
  healthcheck.sh              HTTP probe over bash /dev/tcp (the JRE image has no curl)
  compose.yaml                the topology above
  compose.cold-start.yaml     S10 override (no server health chain)
  pg/primary-init.sh          replication role, pg_hba, synchronous_standby_names
  pg/replica-entrypoint.sh    pg_basebackup -R bootstrap, then the stock entrypoint
  pg/healthcheck.sh           primary writable, or standby streaming
  nginx/nginx.conf            HTTP geo routing + stream VIPs
  toxiproxy/toxiproxy.json    the 18 cross-cell proxies
  toxiproxy/apply-wan.sh      baseline RTT toxics (container and host)
scripts/                      build-images, up, down, run-scenario, wan, partition (.sh + .ps1)
```
