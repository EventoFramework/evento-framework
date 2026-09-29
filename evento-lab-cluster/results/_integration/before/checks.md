# Integration checks: images `:before` (framework `e7b823fe` = evento 2.4.6 + current lab sources), 2026-09-29

Same compose topology and lab sources as [`../after/checks.md`](../after/checks.md), with the
framework and the evento-server built at `e7b823fe` (before the cluster fixes). The raw output of
`scripts/integration-checks.sh` is in [`checks-raw.md`](checks-raw.md); some of its sections
query tables or endpoints that exist only after the fixes and show the error instead.

## Commands

```bash
FRAMEWORK_REF=e7b823fe LAB_TAG=before GRADLE_ARGS="--offline --max-workers=3" scripts/build-images.sh
    # first attempt: lab-api did not compile (Contexts.WILDCARD = Context.WILDCARD, a post-fix API);
    # after making it the literal "*": exit 0, temporary worktree removed
LAB_TAG=before REPAIR_CATALOG=false scripts/up.sh                  # exit 0, every service healthy
LAB_TAG=before scripts/repair-catalog.sh --check                   # exit 1: 2 JVMs missing consumer rows
LAB_TAG=before scripts/integration-checks.sh > results/_integration/before/checks-raw.md
LAB_TAG=before REPAIR_SETTLE_S=5 scripts/repair-catalog.sh         # pass 2 complete, 65 s
LAB_TAG=before scripts/repair-catalog.sh --check                   # exit 0
scripts/down.sh
```

## It starts

Every one of the 36 containers became healthy at the first `up` (~2.5 min, `RestartCount=0`).
Every bundle reached `READY` on its cell server; lb routing for the 7 regions, in-cell reads,
the global view, the price fan-out and the WAN/replication setup behave as with `:after`.

## Framework features unavailable (lab `FrameworkFeatures` shim, `/whoami`)

| Feature | Framework API | `:before` | `:after` |
|---|---|---|---|
| `endpointFailover` (G2) | ordered `EVENTO_SERVER_ADDRESSES` list | **unavailable**: only the first address is dialled | applied, 3 addresses |
| `expectedClusterName` (G13) | `withExpectedClusterName` | **unavailable** | applied |
| `zone` (G12) | `Builder.setZone` | **unavailable** | applied |
| `heartbeatReadIdle` (G22) | `setHeartbeatReadIdle` | not requested (no `EVENTO_HEARTBEAT_READ_IDLE_S` in compose) | not requested |

All 21 JVMs log `event=lab_framework_feature status=unavailable` for the three.
Server side, `EVENTO_SERVER_BUS_PICK_STRATEGY`, `EVENTO_LOCK_POOL_MAXIMUM_SIZE` and
`EVENTO_CLUSTER_NAME` are ignored (no such properties before the fixes);
`/api/cluster-status/zones` answers 500 and `core__bundle_instance` (presence, G7) does not
exist. Queries still stay in-cell because the servers are islands (G1).

## Catalog: `repair-catalog.sh` is still needed here

With `REPAIR_CATALOG=false`, after a 90 s settle, two JVMs had lost their consumer registration
(G8):

| JVM | Missing | Server log (evento-eu) |
|---|---|---|
| `order-query-wildcard` | `order-query_OrderProjector_1_*` | `Consumer registration failed for bundle: order-query and instance: order-query-wildcard` |
| `order-saga-eu` | `order-saga_PaymentSaga_1_EUROPA`, `_AFRICA` | `Consumer registration failed for bundle: order-saga and instance: order-saga-eu` |

Duplicate-key errors in the servers' logs during the start: evento-eu `core__bundle_pkey` 5,
`core__component_pkey` 12, `core__payload_pkey` 7, 2 failed consumer registrations; evento-am and
evento-apac none (they started after evento-eu had written most rows). The lanes themselves ran
(the wildcard lane filled `order_view_global`); only the catalog and the admin view were wrong.
`repair-catalog.sh` fixed both in one pass (65 s). With `:after` the same start needed no repair.

## Dashboard (`/api/consumer`) before the fixes

The pre-fix controller keys consumers by component, so each server lists 4 entries (one per
component) instead of 17 consumer ids, and the one shown for `OrderProjector` carries the
first context found (`order-query_OrderProjector_1_AFRICA`) with the instances of every context
on that server, e.g. `order-query-apac,order-query-cina` on evento-apac. The per-context split
exists in `core__consumer` but not in the admin view.

## Latency of one create + one item (unloaded, through the lb)

| Cell | `:before` | `:after` |
|---|---|---|
| EU | 263-464 ms | 197-205 ms |
| AM | 2.42-2.52 s | 1.32-1.50 s |
| APAC | 4.18-4.40 s | 2.27-2.46 s |

## The ALL lane

Also correct before the fixes: one consumer id, one checkpoint in `consumers`, at most one holder
of the lane lock per sample (0 of 50 with two; EU 12, APAC 23, idle 15), every price in the three
regional databases once. The holder hops between the replicas (N4) as with `:after`.
