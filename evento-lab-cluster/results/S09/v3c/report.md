# S09: App-data (consumer state) primary failover

- Label: `v3c`
- Gaps exercised: G11, G25
- Started: 2026-10-22T13:25:25.840798700Z, finished: 2026-10-22T13:31:22.784580100Z (356 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s09-v3c-619c7c-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `app-pg-eu-r`
- load: `{ratePerMinutePerRegion=4.0, completeRatio=0.6, itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

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
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 147 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 247 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=147 ack=147 failed=0 p50=433ms p95=1108ms p99=2056ms max=2217ms |

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 18,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 18,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 8,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 8,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 9,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 9
    },
    "esHead" : 182
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
      "PSQLException" : 2
    },
    "order-observer" : {
      "PSQLException" : 2
    },
    "order-saga-eu" : {
      "PSQLException" : 3
    },
    "gateway-europa" : { },
    "gateway-africa" : { }
  }
}
```

## Timeline

- +9,4 s: scenario start
- +9,4 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +39,9 s: killed app-pg-eu (app-data primary)
- +48,0 s: promoted app-pg-eu-r: t
- +130,4 s: load done: 147 requests
- +130,4 s: invariants: converging up to 240 s
- +131,6 s: cleanup start
- +356,9 s: cleanup done

### log excerpts: order-query-eu

```
org.postgresql.util.PSQLException: This connection has been closed.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
```

### log excerpts: order-query-wildcard

```
org.postgresql.util.PSQLException: This connection has been closed.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
```

### log excerpts: order-observer

```
org.postgresql.util.PSQLException: This connection has been closed.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
```

### log excerpts: order-saga-eu

```
org.postgresql.util.PSQLException: This connection has been closed.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend.
```

### stack actions

```
2026-10-22T13:26:05.315887900Z docker kill app-pg-eu
2026-10-22T13:26:10.733891100Z psql app-pg-eu-r/app: SELECT pg_promote()
2026-10-22T13:27:37.476226700Z compose down -v
2026-10-22T13:28:02.605685300Z compose up -d --wait
2026-10-22T13:31:01.928153500Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-22T13:31:20.934234800Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

