# S08: Event-store primary failover (promote es-pg-am)

- Label: `v3`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-10-20T15:31:53.036615900Z, finished: 2026-10-20T15:38:05.808830200Z (372 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-v3-5c6743-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 6.963 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 134 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 54 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 134 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 54 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 54 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 360 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=136 ack=134 failed=2 p50=388ms p95=6403ms p99=23385ms max=23398ms failures={error=2} |

## Metrics

```json
{
  "writeOutageSeconds" : 6.963,
  "afterKill" : {
    "count" : 100,
    "acked" : 98,
    "failed" : 2,
    "p50" : 355,
    "p95" : 6667,
    "p99" : 23385,
    "max" : 23398,
    "kinds" : {
      "error" : 2
    }
  },
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "error: Unable to rollback against JDBC Connection" : 2
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 20,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 18,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 20,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 18,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 3,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 3,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 1,
      "EU/order-query_OrderProjector_1_EUROPA" : 11,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 1,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 11
    },
    "esHead" : 4459
  },
  "esHeadAfterFailover" : 4459,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +7,8 s: scenario start
- +7,8 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,2 s: killed es-pg-eu (ES primary)
- +43,5 s: promoted es-pg-am: t
- +128,7 s: load done: 136 requests
- +128,9 s: invariants: converging up to 180 s
- +129,6 s: cleanup start
- +372,8 s: cleanup done

### stack actions

```
2026-10-20T15:32:30.883321100Z docker kill es-pg-eu
2026-10-20T15:32:36.264777300Z psql es-pg-am/evento: SELECT pg_promote()
2026-10-20T15:34:02.665977800Z compose down -v
2026-10-20T15:34:39.813687400Z compose up -d --wait
2026-10-20T15:37:17.756720100Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-20T15:38:04.062881Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

