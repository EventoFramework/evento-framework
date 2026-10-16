# S09: App-data (consumer state) primary failover

- Label: `after-query`
- Gaps exercised: G11, G25
- Started: 2026-10-15T14:13:21.704797700Z, finished: 2026-10-15T14:19:29.112639300Z (367 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s09-after-query-5067b4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `app-pg-eu-r`
- load: `{ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1, completeRatio=0.6}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| checkpoints never go backwards | **AS_EXPECTED** | last_sequence is monotonic for every consumer across the failover | a stale lane holder or the promoted standby rewinds a checkpoint (G11) | monotonic over 59 samples |
| consumer lanes resume on the promoted primary | **AS_EXPECTED** | every consumer's checkpoint advances after the failover |  | all advanced |
| replays absorbed idempotently (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |

- _checkpoints never go backwards_: A rewind to a value the async/sync standby had is expected only if the promoted node lagged.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 142 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 179 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=142 ack=142 failed=0 p50=825ms p95=1545ms p99=2034ms max=2210ms |

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 172,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 8,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 6,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 6,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 19,
      "EU/order-query_OrderProjector_1_EUROPA" : 17,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 19,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 17
    }
  },
  "checkpointSamples" : 59,
  "checkpointRegressions" : { },
  "globalViewReplaysAbsorbed" : 0,
  "logMarkers" : {
    "order-command-eu" : { },
    "order-query-eu" : {
      "PSQLException" : 3
    },
    "order-query-global-eu" : { },
    "order-query-wildcard" : {
      "PSQLException" : 1
    },
    "order-observer" : {
      "PSQLException" : 1
    },
    "order-saga-eu" : {
      "PSQLException" : 1
    },
    "gateway-europa" : { },
    "gateway-africa" : { }
  }
}
```

## Timeline

- +8,3 s: scenario start
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,7 s: killed app-pg-eu (app-data primary)
- +46,9 s: promoted app-pg-eu-r: t
- +129,2 s: load done: 142 requests
- +129,2 s: invariants: converging up to 240 s
- +130,3 s: cleanup start
- +367,4 s: cleanup done

### log excerpts: order-query-eu

```
org.postgresql.util.PSQLException: This connection has been closed.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
Caused by: org.postgresql.util.PSQLException: Could not find a server with specified targetServerType: primary
```

### log excerpts: order-query-wildcard

```
org.postgresql.util.PSQLException: This connection has been closed.
```

### log excerpts: order-observer

```
org.postgresql.util.PSQLException: This connection has been closed.
```

### log excerpts: order-saga-eu

```
org.postgresql.util.PSQLException: This connection has been closed.
```

### stack actions

```
2026-10-15T14:14:00.032621400Z docker kill app-pg-eu
2026-10-15T14:14:05.430075800Z psql app-pg-eu-r/app: SELECT pg_promote()
2026-10-15T14:15:31.984208100Z compose down -v
2026-10-15T14:16:09.817096700Z compose up -d --wait
2026-10-15T14:18:42.972239300Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-15T14:19:27.249060200Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

