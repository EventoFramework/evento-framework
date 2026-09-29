# S09: App-data (consumer state) primary failover

- Label: `before`
- Gaps exercised: G11, G25
- Started: 2026-09-29T13:39:24.244098900Z, finished: 2026-09-29T13:50:26.277104100Z (662 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s09-before-459d69-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- promoted: `app-pg-eu-r`
- load: `{itemsPerOrder=1, completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| checkpoints never go backwards | **AS_EXPECTED** | last_sequence is monotonic for every consumer across the failover | a stale lane holder or the promoted standby rewinds a checkpoint (G11) | monotonic over 58 samples |
| consumer lanes resume on the promoted primary | **AS_EXPECTED** | every consumer's checkpoint advances after the failover |  | all advanced |
| replays absorbed idempotently (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |

- _checkpoints never go backwards_: A rewind to a value the async/sync standby had is expected only if the promoted node lagged.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 143 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 193 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=143 ack=143 failed=0 p50=2422ms p95=4074ms p99=5269ms max=6035ms |

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 11,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 6,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 11,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 6,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 3,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 17,
      "EU/order-query_OrderProjector_1_EUROPA" : 10,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 17,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 10
    },
    "esHead" : 174
  },
  "checkpointSamples" : 58,
  "checkpointRegressions" : { },
  "globalViewReplaysAbsorbed" : 0,
  "logMarkers" : {
    "order-command-eu" : { },
    "order-query-eu" : {
      "PSQLException" : 1
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

- +7,1 s: scenario start
- +7,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +37,6 s: killed app-pg-eu (app-data primary)
- +45,8 s: promoted app-pg-eu-r: t
- +128,1 s: load done: 143 requests
- +128,1 s: invariants: converging up to 240 s
- +129,3 s: cleanup start
- +662,0 s: cleanup done

### log excerpts: order-query-eu

```
org.postgresql.util.PSQLException: This connection has been closed.
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
2026-09-29T13:40:01.410351500Z docker kill app-pg-eu
2026-09-29T13:40:06.833517200Z psql app-pg-eu-r/app: SELECT pg_promote()
2026-09-29T13:41:33.503384600Z compose down -v
2026-09-29T13:42:11.903364100Z compose up -d --wait
2026-09-29T13:45:05.320671800Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-09-29T13:50:24.461355300Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 missing=order-observer:consumers=0/1 order-query-eu:consumers=0/2 order-saga-eu:consumers=0/2, [lab] event=lab_catalog_repair container=order-observer step=helper_start, [lab] event=lab_catalog_repair container=order-query-eu step=helper_start, [lab] event=lab_catalog_repair container=order-saga-eu step=helper_start, [lab] event=lab_catalog_repair container=order-observer step=restart, [lab] event=lab_catalog_repair container=order-query-eu step=restart, [lab] event=lab_catalog_repair container=order-saga-eu step=restart, [lab] event=lab_catalog_repair container=order-observer step=helper_remove, [lab] event=lab_catalog_repair container=order-saga-eu step=helper_remove, [lab] event=lab_catalog_repair container=order-query-eu step=helper_remove, [lab] event=lab_catalog_repair pass=2 status=complete]
```

