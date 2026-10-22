# S09: App-data (consumer state) primary failover

- Label: `v3e`
- Gaps exercised: G11, G25
- Started: 2026-10-22T16:22:05.719488800Z, finished: 2026-10-22T16:27:55.578265700Z (349 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s09-v3e-11b9cd-`
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
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 142 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 174 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=142 ack=142 failed=0 p50=452ms p95=1126ms p99=1754ms max=1834ms |

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 8,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 7,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 7,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 19,
      "EU/order-query_OrderProjector_1_EUROPA" : 10,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 19,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 10
    },
    "esHead" : 172
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
      "PSQLException" : 1
    },
    "gateway-europa" : { },
    "gateway-africa" : { }
  }
}
```

## Timeline

- +8,1 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,5 s: killed app-pg-eu (app-data primary)
- +46,7 s: promoted app-pg-eu-r: t
- +128,9 s: load done: 142 requests
- +128,9 s: invariants: converging up to 240 s
- +130,0 s: cleanup start
- +349,9 s: cleanup done

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
```

### stack actions

```
2026-10-22T16:22:43.854582200Z docker kill app-pg-eu
2026-10-22T16:22:49.229703600Z psql app-pg-eu-r/app: SELECT pg_promote()
2026-10-22T16:24:15.754628100Z compose down -v
2026-10-22T16:24:40.852999900Z compose up -d --wait
2026-10-22T16:27:34.839637100Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-22T16:27:53.741957400Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

