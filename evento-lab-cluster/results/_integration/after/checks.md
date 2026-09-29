# Integration checks: images `:after` (framework = working tree at `0e9b632a`), 2026-09-29

Per-bundle lab (lab-api, lab-support, order-command / query / saga / observer / gateway) on Docker
Desktop (8 CPU, 15.6 GiB), the full compose topology: 36 containers, 21 bundle JVMs.
The raw output of `scripts/integration-checks.sh` is in [`checks-raw.md`](checks-raw.md).

## Commands

```bash
LAB_TAG=after GRADLE_ARGS="--offline --max-workers=3" scripts/build-images.sh      # exit 0
LAB_TAG=after REPAIR_CATALOG=false scripts/up.sh                                    # exit 0, ~2.5 min
LAB_TAG=after scripts/repair-catalog.sh --check                                     # complete (pass 1)
LAB_TAG=after scripts/integration-checks.sh > results/_integration/after/checks-raw.md
LAB_TAG=after scripts/run-scenario.sh S00BaselineTest after-smoke                   # PASSED
LAB_TAG=after scripts/run-scenario.sh S17ContextRoutingTest after                   # PASSED
LAB_TAG=after scripts/run-scenario.sh S16GlobalFanOutTest after                     # PASSED
scripts/down.sh
```

## Summary

| Check | Result |
|---|---|
| Start-up | every service healthy at the first `up` (13:27 -> 13:29:40), `RestartCount=0` everywhere |
| Framework features (`/whoami`) | `endpointFailover`, `expectedClusterName`, `zone` applied in all 21 JVMs |
| Deployments registered on their cell server | yes: `core__bundle_instance` has 8 on evento-eu, 5 on evento-am, 8 on evento-apac, each on its own cell's server |
| Consumer ids per context | 17 ids, one per (component, context); see below |
| `order-query_OrderProjector_1_EUROPA` and `_AFRICA` | both registered by `order-query-eu` (one JVM, plan `{OrderProjector=[EUROPA, AFRICA], CatalogProjector=[]}`) |
| `order-query_CatalogProjector_1_ALL` | exactly one consumer id; two registered instances (`order-query-global-eu`, `-apac`), one checkpoint in `consumers` |
| Wildcard `*` | `order-query_OrderProjector_1_*` (order-query-wildcard) and `order-observer_NotificationObserver_1_*`; no `CatalogProjector_1_*` |
| Only one ALL lane active | 50 samples of `pg_locks` in `consumers` during a 15-price burst: at most one holder of the lane key per sample (0 of 50 with two); holders EU 4, APAC 29, idle 17 |
| LB routing (`X-Region`, 7 regions) | every region routed to its own `gateway-<region>` and that gateway's cell server |
| Queries in-cell | all 3 servers `pick_strategy=LOCALITY_PREFERRED`; every server's zone map holds only its own cell; `GET /orders/{id}` answered in the order's cell for all 7 regions, 404 from another cell (islands, G1) |
| Price fan-out | `POST /catalog/prices` via INDIA visible in eu, am, apac after 1 s, `appliedEvents = 1`, `staleSkips = 0` in each |
| WAN toxics | 18 proxies, each with `wan-latency-up/down`: EU-AM 40+40 ms, EU-APAC 75+75 ms, AM-APAC 85+85 ms, jitter 5 ms |
| ES replication | es_pg_am `streaming sync` (lag 0.09 s), es_pg_apac `streaming async` (0.15 s); both standbys at the primary's head |
| App replication | three `app-pg-*-r` streaming async, lag of a few ms; `order_view` rows equal on replica and primary |
| Connection budget (ES primary) | `max_connections=200`; peak 3 x (20 + 16 lock pool pinned in compose) + 2 x 6 consumer-state = 120; 69-73 client backends measured |
| Catalog repair (G8) | **not needed**: started with `REPAIR_CATALOG=false`, `repair-catalog.sh --check` complete on the first pass, also after S16's kill/restart of a lane holder |

## Consumers per server (live instances only; the list itself is the shared catalog)

| Consumer id | evento-eu | evento-am | evento-apac |
|---|---|---|---|
| `order-query_OrderProjector_1_EUROPA` / `_AFRICA` | order-query-eu | - | - |
| `order-query_OrderProjector_1_AMERICA` / `_SUD_AMERICA` | - | order-query-am | - |
| `order-query_OrderProjector_1_CINA` | - | - | order-query-cina |
| `order-query_OrderProjector_1_INDIA` / `_AUSTRALIA_NZ` | - | - | order-query-apac |
| `order-query_OrderProjector_1_*` | order-query-wildcard | - | - |
| `order-query_CatalogProjector_1_ALL` | order-query-global-eu | - | order-query-global-apac |
| `order-saga_PaymentSaga_1_<region>` (x7) | EUROPA, AFRICA: order-saga-eu | AMERICA, SUD_AMERICA: order-saga-am | CINA, INDIA, AUSTRALIA_NZ: order-saga-apac |
| `order-observer_NotificationObserver_1_*` | order-observer | - | - |

The dashboard shows every catalog row on every server but only the instances connected to that
server (G17 is still local).

## Scenarios

| Scenario | Label | Outcome | Numbers |
|---|---|---|---|
| S00 baseline | `after-smoke` | PASSED; I1..I6 PASS, payment saga 78/78; I7 ERROR in this run (harness bug, fixed, see below) | 498/498 commands acknowledged at 7 x 20 orders/min; 9.6 appends/s; p50 EU 116-267 ms, AM 718-729 ms, APAC 1109-1122 ms; p95 <= 1.7 s |
| S17 context routing | `after` | PASSED, every observation AS_EXPECTED, I1..I7 PASS | 11 deployments register exactly their consumer ids; 17 checkpoints in the right databases; 14 orders, 0 cross-context rows |
| S16 global fan-out | `after` | PASSED, every observation AS_EXPECTED | fan-out 4.1 s for 5 events; APAC DB down: EU/AM got only the head event, checkpoint stayed at 740; convergence after heal immediate, 6 replays absorbed by the sequence guard; takeover by the standby 5.2 s after `docker kill` of the holder |

For comparison, the archived role-based `before` S00 (`results/_archive-role-based/S00/before`)
acknowledged 217 of 309 commands (92 timeouts) with p50 15.3 s at the same offered load.

## Harness defects found and fixed during the integration

- **repair-catalog.sh `--check` reported "complete" on a failing query.** Its handler join used
  the pre-N1 column `core__handler.component_component_name`; the psql error ended the listing
  subshell (errexit), which read as "nothing missing". The join now follows the schema (`c.id =
  h.component_id` after N1), a failed listing exits 2, and the check waits up to
  `REPAIR_SETTLE_S` (90 s) for the asynchronous consumer discovery (rows appear up to ~25 s
  after a JVM turns healthy) before declaring anything missing.
- **I7 could never be read on the N1 schema** (`Db.catalog`, same column): now schema-aware.
- **I7 expected a `core__bundle` row for `order-gateway`.** The server writes the catalog only for
  registrations with handlers (unchanged since 2.x), so a send-only bundle has presence rows but
  no catalog rows; I7 now skips `BUNDLES_WITHOUT_HANDLERS` in the bundle check (unit test added).
- **`Contexts.WILDCARD = Context.WILDCARD`** made the lab uncompilable against the pre-fix
  framework (`FRAMEWORK_REF=e7b823fe`); it is the literal `"*"` again, pinned by `ContextsTest`.
- **build-images.sh** named its temporary worktree `.evento-lab-fw-e7b823fe_` (the `echo` newline
  went through `tr -c`); a stale copy under `.claude/worktrees/` was removed.
