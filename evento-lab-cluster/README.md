# evento-lab-cluster

**IT** — Laboratorio di chaos testing per un deployment Evento multi-regione: 7 regioni
(contesti Evento) raggruppate in 3 celle, 3 evento-server attivi, un cluster Postgres replicato
per l'event store e tre database applicativi regionali indipendenti, un load balancer
"location-aware" e un proxy WAN che simula latenze e partizioni tra continenti. Le applicazioni
sono cinque bundle con responsabilità specifiche (command, query, saga, observer, gateway), ognuno
un solo codice sorgente che diventa un deployment diverso cambiando solo la configurazione. Gli
scenari (S0..S17) producono i risultati in `results/` e servono come evidenza prima/dopo per le
correzioni del framework.

**EN** — A chaos-testing lab for a multi-region Evento deployment: 7 regions (Evento contexts)
grouped into 3 cells, 3 active evento-servers, one replicated Postgres cluster for the event
store and three independent regional app databases, a location-aware load balancer and a WAN
proxy that emulates inter-continental latency and partitions. The applications are five bundles
with specific responsibilities (command, query, saga, observer, gateway); each is one codebase
that becomes a different deployment through configuration only. The scenarios (S0..S17) write
their evidence to `results/`, captured before and after each framework fix.

Design inputs, gap ids (G1..G25), invariants (I1..I8) and the scenario catalogue live in
[`.claude/CLUSTER-READINESS.md`](../.claude/CLUSTER-READINESS.md); what the runs found is in
[`FINDINGS.md`](FINDINGS.md).

**Cluster-mode guide (IT):** [`docs/README.md`](docs/README.md) walks from one server to three
multi-region cells by complexity, each level with topology and sequence diagrams, the exact
configuration of the same images, a bundle × level matrix, guarantees, limits (with links to the
scenario results), sizing and a checklist:
[L0 single server](docs/L0-single-server.md) ·
[L1 server HA](docs/L1-server-ha.md) ·
[L2 cells](docs/L2-cells.md) ·
[L3 multi-region contexts](docs/L3-multi-region-contexts.md) ·
[L4 DB failover](docs/L4-db-failover.md) ·
[L5 limits and roadmap](docs/L5-limits-roadmap.md).
References: [configuration](docs/config-reference.md), [contexts and the `ALL` lane](docs/contexts.md),
[chaos scenarios before/after](docs/chaos-scenarios.md).
**Slides (IT, offline HTML):** [`docs/slides/index.html`](docs/slides/index.html), numbers from
[`results/COMPARISON.md`](results/COMPARISON.md); how to open and export them in
[`docs/slides/README.md`](docs/slides/README.md).

## Bundles

| Module | Bundle id | Components | Consumes |
|---|---|---|---|
| `lab-api` | - | commands, events, queries, views; `Regions`, `Contexts` | - |
| `lab-support` | - | deployment contract (`BundleDeployment`), JDBC consumer wiring, lab schema and stores, `FrameworkFeatures` shim, `/actuator/health`, `/whoami`, `/lab/stats` | - |
| `order-command` | `order-command` | `OrderAggregate`, `PaymentService`, `PriceListService` | nothing |
| `order-query` | `order-query` | **one** `OrderProjector`, **one** `CatalogProjector`, `OrderProjection` (queries) | `EVENTO_CONTEXTS` |
| `order-saga` | `order-saga` | **one** `PaymentSaga` | `EVENTO_CONTEXTS` (regions) |
| `order-observer` | `order-observer` | **one** `NotificationObserver` | `EVENTO_CONTEXTS` |
| `order-gateway` | `order-gateway` | none: HTTP API, sends commands and queries | nothing |
| `evento-lab-cluster-chaos` | - | scenario driver, invariant checker (framework-free) | - |

Every bundle module builds one boot jar (`<module>.jar`) and one image
(`evento-lab-cluster/<module>:$LAB_TAG`). A deployment is a compose service running that image
with its own environment; there is no role switch and nothing about topology is compiled in.

### Regions are contexts, nothing else

There is one projector class per responsibility, not one per region. A deployment lists its
regions in `EVENTO_CONTEXTS`; the bundle calls
`EventoBundle.Builder.setComponentContexts(OrderProjector.class, "EUROPA", "AFRICA")`, and the
builder starts **one consumer engine per context**, each fetching only its context's events.
Consumer ids are `<bundleId>_<Component>_<version>_<context>`, e.g.
`order-query_OrderProjector_1_EUROPA` and `order-query_OrderProjector_1_AFRICA`, both running in
the single JVM `order-query-eu`.

`order-query` maps `EVENTO_CONTEXTS` onto its two projectors (`OrderQueryBundle.plan`):

| `EVENTO_CONTEXTS` | `OrderProjector` engines | `CatalogProjector` engines | writes |
|---|---|---|---|
| regions, e.g. `EUROPA,AFRICA` | one per region | none | the cell database's `order_view` |
| `ALL` | none | `ALL`: **the global lane** | `catalog_price` in every `DATA_TARGETS` database |
| `*` | `*` (every context) | none | `order_view_global` |

A component given an explicitly empty context set runs no engine (only a component with no entry
at all defaults to `*`); that is how the wildcard deployment stays off the global lane although
`*` matches `ALL` events.

### `ALL` is not `*` (the trap)

`com.evento.common.utils.Context.ALL` is the **wildcard** `"*"` (the event store turns it into
`LIKE '%'`): a consumer registered for it receives every context. Cross-region events (the price
list here; catalog, promotions in general) need the opposite: a real context that no regional
consumer receives. The lab calls it `Contexts.GLOBAL = "ALL"` (in `lab-api`), and
`PriceListService` stamps every `PriceChangedEvent` with it. `EVENTO_CONTEXTS=ALL` (any case)
always means the literal context, never the wildcard; `*` must be written as `*`, alone.
`ContextSelection` rejects unknown names, an empty list, and `*` mixed with anything else, so a
typo cannot become a silent lane.

### The single global lane and its fan-out

- Exactly one lane consumes `ALL` in the whole cluster: `order-query_CatalogProjector_1_ALL`. It
  runs in the deployments that list `ALL` (`order-query-global-eu`, `order-query-global-apac`).
  They share the bundle id **and** the consumer-state database (`consumers` on the event-store
  cluster), so they compete for the lane: one projects, the other stands by and takes over when
  the holder dies. The lock is taken per fetch cycle, so the holder can hop between replicas
  (N4); it is still one lane: one consumer id, one checkpoint.
- Decision on the bundle id: every order-query deployment keeps `order-query`. Lanes are told
  apart by context, so the regional deployments never compete with each other or with the global
  one. A second bundle id for the ALL deployments (e.g. `order-query-global-b`) would create a
  second, independent lane (G24) and fan every price out twice; so would an ALL deployment whose
  `CONSUMER_STATE_DB_URL` points elsewhere (competition happens only through a shared lock table).
- The lane writes every price into all three regional primaries (`DATA_TARGETS`), each write an
  upsert applied only if the event's sequence is above the stored one (`CatalogStore`,
  `catalog_price.last_seq`). If a target is down, the others are served, the event fails and is
  redelivered (inline retry): exactly once per target, but the lane stalls for **every** region
  until the broken one is back. That is the price of one lane for N targets; S16 records it.

## Topology

| Cell | Regions (contexts) | Server | ES Postgres (`evento`, `consumers`) | App Postgres (`app`) |
|---|---|---|---|---|
| EU | EUROPA, AFRICA | `evento-eu` | `es-pg-eu` **primary** | `app-pg-eu` primary + `app-pg-eu-r` |
| AM | AMERICA, SUD_AMERICA | `evento-am` | `es-pg-am` sync standby | `app-pg-am` primary + `app-pg-am-r` |
| APAC | CINA, INDIA, AUSTRALIA_NZ | `evento-apac` | `es-pg-apac` async standby | `app-pg-apac` primary + `app-pg-apac-r` |

App data is regional: each cell's read models (`order_view`, one table for all the cell's regions
with a `context` column), its copy of the catalog (`catalog_price`), its side effects (`payments`,
`observed_events`) and the consumer state of its deployments live in its own primary, which has
one local asynchronous streaming replica (promotable, S9). `order_view_global` lives in
`app-pg-eu` (the wildcard deployment's database); AM and APAC order-query deployments read it
over the WAN (`GLOBAL_VIEW_DB_URL`).

```
                           host: curl -H 'X-Region: CINA' localhost:8080/orders
                                              |
                     +------------------------v-------------------------+
                     | lb (nginx) http :8080 X-Region -> gateway-<region> |
                     |            stream VIPs bus-eu|am|apac:3030         |
                     +----+----------------------+-------------------+---+
  net-eu -----------------+   net-am ------------+   net-apac --------+----------------------
  gateway-europa, -africa     gateway-america,        gateway-cina, -india, -australia-nz
  order-command-eu            -sud-america            order-command-apac
  order-query-eu (EUROPA,AFRICA)  order-command-am    order-query-cina (CINA)
  order-query-global-eu (ALL) order-query-am          order-query-apac (INDIA,AUSTRALIA_NZ)
  order-query-wildcard (*)    (AMERICA,SUD_AMERICA)   order-query-global-apac (ALL)
  order-saga-eu, order-observer (*)  order-saga-am    order-saga-apac
        |                          |                        |
    evento-eu :3030           evento-am                 evento-apac
    es-pg-eu (P) ==sync==> es-pg-am (S)            es-pg-apac (A) <==async== es-pg-eu
    app-pg-eu (+ -r)          app-pg-am (+ -r)          app-pg-apac (+ -r)     (independent)
  =========================== toxiproxy (toxi-eu / toxi-am / toxi-apac, API :8474) =======
      every cross-cell hop (server -> remote ES PG, ES replica -> primary, JVM -> remote server,
      global lane -> remote app-pg, global-view reads, VIP backup) goes through it:
          EU <-> AM 80 ms RTT   EU <-> APAC 150 ms RTT   AM <-> APAC 170 ms RTT   (+-5 ms/way)
```

A container is attached only to its own cell network, so it *cannot* reach another cell
except through toxiproxy. `docker network disconnect net-apac toxiproxy` therefore partitions
APAC cleanly (silently: no RST), and toxics on one proxy degrade exactly one WAN link.

### Deployments

All deployments set `EVENTO_ZONE=<cell>`, `EVENTO_CLUSTER_NAME=evento-lab-cluster`,
`EVENTO_INSTANCE_ID=<service>`, `EVENTO_SERVER_ADDRESSES=<own server>,<other two via toxiproxy>`
and `EVENTO_CHECKPOINT_MODE`; the table lists what differs.

| Service | Image | `EVENTO_CONTEXTS` | Databases | mem |
|---|---|---|---|---|
| `order-command-eu` / `-am` / `-apac` | order-command | - | own cell (`payments`) | 288m |
| `order-command-eu-b` (profile `replica`, S2) | order-command | - | EU | 288m |
| `order-query-eu` | order-query | `EUROPA,AFRICA` (two engines, one JVM) | EU | 256m |
| `order-query-am` | order-query | `AMERICA,SUD_AMERICA` | AM; global view via WAN | 256m |
| `order-query-cina` | order-query | `CINA` | APAC; global view via WAN | 256m |
| `order-query-apac` | order-query | `INDIA,AUSTRALIA_NZ` | APAC; global view via WAN | 256m |
| `order-query-global-eu` | order-query | `ALL` | state in `consumers`; `DATA_TARGETS` eu/am/apac | 256m |
| `order-query-global-apac` | order-query | `ALL` (competing replica) | state in `consumers`; `DATA_TARGETS` eu/am/apac | 256m |
| `order-query-wildcard` | order-query | `*` | EU (`order_view_global`) | 256m |
| `order-saga-eu` / `-am` / `-apac` | order-saga | the cell's regions | own cell | 256m |
| `order-observer` | order-observer | `*` | EU | 256m |
| `order-observer-eu` / `-am` / `-apac` (profile `regional-observers`) | order-observer | the cell's regions, bundle id `order-observer-regional` | own cell | 256m |
| `gateway-<region>` x 7 | order-gateway | - (`EVENTO_REGION=<region>`) | none (`DATA_TARGETS` only for `?db=`) | 256m |

Observer choice: the default topology runs one `*` observer. The lab's observer is an audit of
every order event (its only evidence is duplicate delivery, I2), and one lane gives that with one
JVM. Regional notifications are the same image with regional contexts and their own bundle id
(profile `regional-observers`): they then run next to the region, and one region's outage stalls
only its own notifications. `observed_events` counts deliveries per lane, so both can run together.

Per-cell sagas: `PaymentSaga` runs in the cell of the order's region (contexts per cell), so
payments run next to the region and the `ProcessPaymentCommand` is served by the same cell's
`order-command`.

Queries resolve in the caller's cell: the servers run `EVENTO_SERVER_BUS_PICK_STRATEGY`
(`PICK_STRATEGY`, default `locality-preferred`; ignored by a server without the G12 fix) and every
bundle advertises `EVENTO_ZONE`. Every order-query deployment of a cell reads the same cell
database, so any of them answers `FindOrderQuery` / `FindPriceQuery` for every region of the cell
and for the fanned-out catalog. Before G1 the servers are islands, which keeps requests in-cell
anyway.

Total limits about 8.9 GiB (36 services); 21 bundle JVMs.

### Environment contract (`lab-support` `BundleDeployment`)

| Variable | Meaning |
|---|---|
| `EVENTO_BUNDLE_ID` | bundle id; defaults to the module's (`order-query`, ...) |
| `EVENTO_INSTANCE_ID` | instance id (fallback `EVENTO_BUNDLE_INSTANCE_ID`, hostname) |
| `EVENTO_SERVER_ADDRESSES` | `host:port,...`, preferred broker first; all are passed (before G2 only the first is dialled) |
| `EVENTO_CLUSTER_NAME` | cluster the broker must announce (framework feature, G13) |
| `EVENTO_EXPECTED_SERVER_ID` | single-broker guard of 2.4.6 (fallback `EVENTO_EXPECTED_SERVER_INSTANCE_ID`) |
| `EVENTO_ZONE` | where the instance runs (`EU`/`AM`/`APAC`), for locality routing (framework feature, G12); also the cell |
| `EVENTO_CONTEXTS` | consumed contexts: regions, `ALL` or `*` (required by order-query, order-saga, order-observer) |
| `EVENTO_REGION` | the gateway's region |
| `EVENTO_CHECKPOINT_MODE` | `DEFAULT` / `WATERMARK` / `ON_START` (fallback `CONSUMER_CHECKPOINT_MODE`) |
| `APP_DB_URL` / `_USER` / `_PASSWORD` / `_POOL_SIZE` | the cell's app database |
| `CONSUMER_STATE_DB_*` | consumer state (checkpoints, locks, saga state); default the app database |
| `GLOBAL_VIEW_DB_*` | where `order_view_global` is read from; default the app database |
| `DATA_TARGETS` | `name=jdbc:...;name=jdbc:...` (a comma also separates entries when followed by `name=jdbc:`, so multi-host URLs keep theirs) |
| `COMMAND_TIMEOUT_MS` | gateway command/query timeout (30000) |
| `EVENTO_HEARTBEAT_READ_IDLE_S` | silent-partition detection (framework feature, G22) |

### Before and after the framework fixes

The same lab source runs against the framework before and after the cluster fixes. APIs that exist
only after them (`Builder.setZone`, `withExpectedClusterName`, `setHeartbeatReadIdle`, endpoint
failover) are called through `FrameworkFeatures`, a reflective shim that applies what it finds and
logs `event=lab_framework_feature status=unavailable` for the rest; `/whoami` lists the outcome
(`frameworkFeatures`), so a report can say what its images had.

### Containers, ports

| Service | Host port |
|---|---|
| `es-pg-eu` / `-am` / `-apac` (`evento`, `consumers`; `postgres`/`secret`) | 15432 / 15433 / 15434 |
| `app-pg-eu` / `-am` / `-apac` (`app`) | 25432 / 25433 / 25434 |
| `app-pg-eu-r` / `-am-r` / `-apac-r` | 25442 / 25443 / 25444 |
| `evento-eu` / `-am` / `-apac` (GUI Basic `evento`/`secret`) | 13000 / 13001 / 13002 |
| `lb` | 8080 |
| `toxiproxy` API | 8474 |

Container names equal service names, so `docker kill evento-apac` works as written in the
scenarios. JVMs other than the gateways are not published; `/whoami` of any of them:
`docker exec order-query-eu bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /whoami HTTP/1.0\r\n\r\n" >&3; cat <&3'`.

### WAN proxies

Proxy `<src>-to-<svc>-<dst>` listens on `toxi-<src>:20000 + src*100 + dst*10 + svc`
(cells `eu=1 am=2 apac=3`; services `es-pg=1 app-pg=2 evento=3`), for example:

| Proxy | Address (from the source cell) | Upstream |
|---|---|---|
| `am-to-es-pg-eu` | `toxi-am:20211` | `es-pg-eu:5432` (also carries es-pg-am replication) |
| `apac-to-app-pg-eu` | `toxi-apac:20312` | `app-pg-eu:5432` (global lane fan-out, global view reads) |
| `eu-to-evento-apac` | `toxi-eu:20133` | `evento-apac:3030` |

Full list: [`docker/toxiproxy/toxiproxy.json`](docker/toxiproxy/toxiproxy.json). Baseline toxics
are named `wan-latency-up` / `wan-latency-down`; scenario toxics must use other names
(`chaos-*`) so healing never removes the baseline. `scripts/wan.sh [apply|off|show]`
reapplies / removes / shows them (`WAN_LATENCY_SCALE=200` doubles the matrix).

### Load balancer

* **HTTP :8080** routes on `X-Region` (or `?region=`; case-insensitive, `SUD_AMERICA` /
  `sud-america` both accepted) to `gateway-<r>:8080`; same-cell gateways are `backup` servers.
  No header = EUROPA. Responses carry `X-Lab-Region` (routing key) and `X-Lab-Upstream`
  (who served it). Only connect errors / 502-504 are retried on a backup, so the LB never
  replays a POST that reached a gateway. Health: `GET /lb/health`.
* **Stream VIPs** `bus-eu:3030`, `bus-am:3030`, `bus-apac:3030` (static lb addresses
  172.28.{1,2,3}.10 on each cell network): primary = own cell server, backups = the other two
  servers through toxiproxy. JVMs use them only with `BUS_VIA_LB=true`.
* Upstreams use nginx `resolve`, so the lb starts without the app containers and follows
  kills/restarts. After re-attaching the lb to a network, restart it (`scripts/partition.sh`
  does) so it binds the VIP address again.

### HTTP API (every gateway)

| Request | Effect |
|---|---|
| `POST /orders` `{orderId?, items?, context?}` | CreateOrder (+ AddItem per item) in the gateway's region |
| `POST /orders/{id}/items` `{sku, qty}` / `POST /orders/{id}/complete` | AddOrderItem / CompleteOrder |
| `GET /orders/{id}` | `FindOrderQuery`: the order in the caller's cell `order_view` |
| `GET /orders/global/{id}` | `FindGlobalOrderQuery`: `order_view_global` |
| `POST /catalog/prices` `{sku, priceCents, currency?}` | `SetPriceCommand` -> `PriceChangedEvent` with context `ALL` |
| `GET /catalog/prices/{sku}` | `FindPriceQuery`: the caller's cell copy |
| `GET /catalog/prices/{sku}?db=eu\|am\|apac` | that regional database, read directly (was the fan-out there?) |
| `GET /whoami`, `/lab/stats`, `/actuator/health` | every bundle |

### Start-up order

`toxiproxy` -> `toxiproxy-init` (baseline WAN) and `es-pg-eu` / app primaries -> replicas
(`pg_basebackup -R`; ES replicas through toxiproxy, app replicas locally) -> `evento-eu` (waits for
the ES sync standby, otherwise every commit blocks) -> `evento-am` -> `evento-apac` (health chain
against the concurrent `schema.sql`, G14) -> bundles. `COLD_START=true` (S10) drops the chain.

## Build and run

Requirements: Docker with compose v2, JDK 25 for Gradle (the scripts find
`C:/Users/Gaborando/.jdks/openjdk-25.0.1`, `~/.jdks/*25*` or macOS `java_home -v 25`).
Every `.sh` works under Git Bash; the `.ps1` twins call the same script through Git Bash.

```bash
scripts/build-images.sh            # gradle bootJar (server + 5 bundles, --offline) + docker build
                                   #   -> evento-lab-cluster/{server,order-*}:$LAB_TAG
FRAMEWORK_REF=e7b823fe LAB_TAG=before scripts/build-images.sh   # same lab, framework before the fixes
LAB_TAG=after scripts/build-images.sh                           # working tree
scripts/up.sh                      # full topology, waits for every healthcheck, then repair-catalog.sh
scripts/run-scenario.sh S00BaselineTest before
scripts/run-scenario.sh all after
scripts/down.sh                    # removes containers, networks and volumes (-v)
```

`FRAMEWORK_REF` creates a temporary git worktree at that ref, copies the current
`evento-lab-cluster` folder and its `settings.gradle` include lines into it, builds every jar there
and removes the worktree (`KEEP_FRAMEWORK_WORKTREE=true` keeps it). Images carry the label
`evento.lab.framework-ref`.

| Variable | Effect |
|---|---|
| `LAB_TAG` (dev) | image tag used by build and compose; when unset, the scripts use the tag of the running `evento-eu` |
| `FRAMEWORK_REF` | build-images.sh: framework git ref to build against (default: working tree) |
| `REPAIR_CATALOG=false` | `up.sh` skips `repair-catalog.sh` (`REPAIR_PASSES`, `REPAIR_PARALLEL`, `REPAIR_SETTLE_S` tune it) |
| `GRADLE_ARGS` (`--offline`) | extra Gradle flags; `SKIP_GRADLE=true` reuses existing jars |
| `BUS_VIA_LB=true` | `up.sh` points the JVMs at their cell VIP instead of the direct list (`BUS_ADDR_EU/AM/APAC` can also be set directly) |
| `REPLICA=true` | `up.sh` adds `order-command-eu-b` (profile `replica`, S2) |
| `REGIONAL_OBSERVERS=true` | `up.sh` adds the per-cell observers (profile `regional-observers`) |
| `PICK_STRATEGY` | servers' handler pick (`locality-preferred`, `random`, `first`) |
| `COLD_START=true` | `up.sh` adds `docker/compose.cold-start.yaml` (S10) |
| `CONSUMER_CHECKPOINT_MODE` | `DEFAULT` or `WATERMARK` for every consumer |
| `WAN_LATENCY_SCALE`, `WAN_JITTER_MS` | scale / jitter of the baseline WAN matrix |

`scripts/up.sh <service...>` starts a subset (with dependencies).

**Catalog repair (G8 workaround, `:before` only).** On an empty event store the pre-fix server's
discovery and consumer registration race on inserts, so some JVMs end up without `core__consumer` or
`core__handler` rows. `scripts/repair-catalog.sh` starts a helper twin of each affected JVM
(`compose run`, instance id `<c>-helper`), restarts the JVM while the twin keeps the bundle live, then
removes the twin. `--check` only reports. It first waits up to `REPAIR_SETTLE_S` (90 s) for the rows,
because consumer registration lands up to ~25 s after a JVM turns healthy (N6). With the `:after`
images the catalog is complete without it (integration run, `results/_integration/`); `up.sh` still
calls it, and then it only checks. The chaos driver's `Cluster.recreateStack()` runs it too.

**Integration evidence.** `scripts/integration-checks.sh > results/_integration/<tag>/checks-raw.md`
prints, for the running stack: framework features per JVM, presence per server, consumer ids per
context on each server GUI, the ALL lane (checkpoint, lock holders sampled during a burst), lb routing
for the 7 regions, in-cell reads, the price fan-out, WAN toxics, ES/app replication, the connection
budget, the catalog check and restart counts. It writes only its own probe rows (`it-<tag>-<epoch>-*`).

**No per-region classes.** The earlier N1 workaround (one projector class per region, because the
server keyed `core__component` by simple class name across bundles) is gone: regions are contexts of
one component class, and all order-query deployments are one bundle. Before the N1 fix the catalog
rows of that bundle are shared by its instances, which is the normal replica case.

Manual fault helpers: `scripts/partition.sh <eu|am|apac> on [--with-lb]` / `off`;
`scripts/wan.sh off|apply`; promotion `docker exec es-pg-am psql -U postgres -c 'select pg_promote()'`
or `docker exec app-pg-eu-r psql -U postgres -c 'select pg_promote()'`.
After a promotion the topology is not restored automatically: `scripts/down.sh && scripts/up.sh`.

## Scenarios

Classes live in `evento-lab-cluster-chaos` (`S<nn><Name>Test`), are skipped unless
`EVENTO_LAB_CLUSTER=true` (set by `run-scenario.sh`), and write
`results/<scenario>/<label>/{summary.json,report.md}`. A run that exposes a known gap still
passes and records observed vs expected; only harness errors fail. Select with the class
name or a pattern: `scripts/run-scenario.sh 'S03*' before`.

Invariants over the regional layout: I4 = each cell database's `order_view` holds only that cell's
contexts; I5 = `order_view_global` equals the union of the three `order_view`; I6 = every order in
its cell's `order_view` and in the global view with the event store's state; I7 = bundles
`order-command`, `order-query`, `order-saga`, `order-observer` in `core__bundle` (the send-only
`order-gateway` has no catalog rows at all, N8), a handler for every command/query, and the consumer ids
`order-query_OrderProjector_1_<R>` (x7), `order-query_OrderProjector_1_*`,
`order-query_CatalogProjector_1_ALL`, `order-saga_PaymentSaga_1_<R>` (x7),
`order-observer_NotificationObserver_1_*`. Side effects (`payments`, `observed_events`) are summed
over the three cell databases.

| Id | Scenario | Fault | Gaps |
|---|---|---|---|
| S0 | Baseline | none, 7 regions x 20 orders/min | reference, G9 |
| S1 | Cross-cell routing miss | stop `order-command-am` | G1 |
| S2 | Kill a handler replica in a cell | `docker kill order-command-eu` (with `REPLICA=true`) | intra-server failover |
| S3 | Kill an evento-server | `docker kill evento-apac` | G2, G4, G5, G8, G13 |
| S4 | Server returns after failover | restart / pause > 45 s `evento-apac` | G7, G17 |
| S5 | Same aggregate from two cells | EUROPA + CINA contention, 150 ms RTT | G3, G6, G9 |
| S6 | WAN latency sweep | 0/50/150/300 ms to the ES primary | G9, G18 |
| S7 | Region partition | detach APAC for 60 s; watch the ALL lane and the `*` lanes | G22, G25, G11 |
| S8 | ES primary failover | kill `es-pg-eu`, promote `es-pg-am` (sync) / `es-pg-apac` (async) | G3, G5, G6, G19 |
| S9 | App-DB primary failover | kill `app-pg-eu`, promote `app-pg-eu-r` | G11, G25 |
| S10 | Cold start of 3 servers | simultaneous start on an empty DB | G14 |
| S11 | Underscore contexts | SUD_AMERICA / AUSTRALIA_NZ + `SUDXAMERICA` | G15, G16, L6 |
| S12 | Per-context WATERMARK cost | `order-query-eu` redeployed with `EVENTO_CONTEXTS=EUROPA` under global load | G10, G20 |
| S13 | Wrong-broker / identity | `gateway-europa` behind the VIP: C expects a foreign cluster; A single-id guard + kill `evento-eu`; B cluster-name guard + kill `evento-eu` | G13, N16 |
| S14 | Retry storm | retry CreateOrder through another cell | G5 |
| S15 | Clock skew (optional) | +40 s on `order-query-eu` | fetch staleness gate |
| S16 | Global ALL events | fan-out to 3 databases; stop `app-pg-apac`; kill the lane holder | G24, G11, N4 |
| S17 | Context routing | one class, engines per context; no cross-context rows; `*` sees all | setComponentContexts, G8, G15 |

Suggested order: S0, S17, S16, S10, S11, S1, S2, S3, S4, S5, S6, S12, S7, S8, S9, S13, S14.

Recreate the stack (`scripts/down.sh && scripts/up.sh`) after S10 and after S04. S10 drops the
event store, and S04's variant B leaves the `:before` catalog beyond repair (N12). S08 and S09
recreate it themselves.

Every scenario starts with two guards: it moves bundle JVMs that failed over to a remote server
back to their own cell's server by restarting them (there is no failback, N16; this is a no-op on
2.4.6), and it checks that each region's `/whoami` comes from its own gateway, recreating the lb
once if not (L23). Both are recorded as `placementRestoredAtStart` and `lbRoutingAtStart`, and
`EVENTO_LAB_RESTORE_PLACEMENT=false` turns the first off. **Never run two scenario drivers at
once.** Overlapping runs broke the lb's network re-attach and silently routed every region to one
gateway. To stop a run, kill the whole process tree (bash, `run-scenario.sh`, the Gradle client),
not only the parent shell.

Scenario knobs worth knowing:
- `S14_KILL_DELAY_MS=2000` on `:before`: at the default 150 ms no command reaches the append
  before the kill, and G5 is then INCONCLUSIVE (L17).
- S15 needs a clock **behind** the server's by more than 30 s, and libfaketime:
  ```bash
  docker build -f docker/faketime/Dockerfile --build-arg BASE=evento-lab-cluster/order-query:before \
    -t evento-lab-cluster/order-query:before-faketime docker/faketime
  MSYS2_ENV_CONV_EXCL='*' S15_IMAGE=evento-lab-cluster/order-query:before-faketime \
  S15_FAKETIME_ENV='LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1,FAKETIME=-40s,FAKETIME_DONT_FAKE_MONOTONIC=1' \
    scripts/run-scenario.sh S15ClockSkewTest before
  ```
  The same for `:after` with `BASE=...:after`, tag `order-query:after-faketime`, label `after`.
- `S12_IDLE_S` (30), `S12_NOISE_FLOOR_MS` (1000): S12 subtracts the JVM's idle CPU rate and calls a
  difference below the floor INCONCLUSIVE (L19).
- `LAB_BASH`: the bash the chaos driver uses for `repair-catalog.sh` (default Git Bash on
  Windows, L16).
- ES replicas start without `synchronous_standby_names` (L24), so a promoted replica (S08) takes
  writes; the primary still has its sync standby.

Per-scenario goal, fault, invariants and numbers in Italian: [`docs/chaos-scenarios.md`](docs/chaos-scenarios.md).
Results: `:before` [`results/BEFORE-SUMMARY.md`](results/BEFORE-SUMMARY.md), `:after`
[`results/AFTER-SUMMARY.md`](results/AFTER-SUMMARY.md), side by side with the list of what is still
broken in [`results/COMPARISON.md`](results/COMPARISON.md).

## Files

```
lab-api/                      commands, events, queries, views, Regions, Contexts (GLOBAL = "ALL")
lab-support/                  BundleDeployment, ContextSelection, DataTargets, FrameworkFeatures,
                              BundleRunner, LabDatabases, stores (order_view, catalog_price, side effects)
order-command/ order-query/ order-saga/ order-observer/ order-gateway/   one boot jar each
evento-lab-cluster-chaos/     scenarios S00..S17, invariants I1..I8
docs/                         cluster-mode guide (IT): L0..L5, config reference, contexts, scenarios
docker/
  Dockerfile                  generic JVM image (explicit jar via ARG JAR, L1)
  healthcheck.sh              HTTP probe over bash /dev/tcp (the JRE image has no curl)
  compose.yaml                the topology above
  compose.cold-start.yaml     S10 override (no server health chain)
  pg/primary-init.sh          replication role, pg_hba, synchronous_standby_names, EXTRA_DATABASES
  pg/replica-entrypoint.sh    pg_basebackup -R bootstrap, then the stock entrypoint
  pg/healthcheck.sh           primary writable, or standby streaming
  nginx/nginx.conf            HTTP geo routing + stream VIPs
  toxiproxy/toxiproxy.json    the 18 cross-cell proxies
  toxiproxy/apply-wan.sh      baseline RTT toxics (container and host)
scripts/                      build-images, up, down, run-scenario, wan, partition, repair-catalog (.sh + .ps1),
                              integration-checks.sh (evidence report of a running stack)
```
