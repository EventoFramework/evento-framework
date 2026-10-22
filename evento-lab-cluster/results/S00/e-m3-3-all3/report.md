# S00: Baseline, no faults

- Label: `e-m3-3-all3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T17:12:22.999026100Z, finished: 2026-10-22T17:13:38.016554900Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m3-3-all3-60f0d3-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 490/490 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 241 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 70 payments for 70 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 241 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=490 ack=490 failed=0 p50=546ms p95=1144ms p99=1381ms max=1555ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 490,
    "acked" : 490,
    "failed" : 0,
    "p50" : 546,
    "p95" : 1144,
    "p99" : 1381,
    "max" : 1555,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 442,
      "p95" : 618,
      "p99" : 1019,
      "max" : 1019,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 535,
      "p95" : 715,
      "p99" : 746,
      "max" : 746,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 561,
      "p95" : 1338,
      "p99" : 1555,
      "max" : 1555,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 729,
      "p95" : 1158,
      "p99" : 1551,
      "max" : 1551,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 432,
      "p95" : 632,
      "p99" : 1114,
      "max" : 1114,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 803,
      "p95" : 1213,
      "p99" : 1381,
      "max" : 1381,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 66,
      "acked" : 66,
      "failed" : 0,
      "p50" : 581,
      "p95" : 822,
      "p99" : 1022,
      "max" : 1022,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 559,
  "esAppendsPerSecond" : 9.316666666666666,
  "ackedCommandsPerSecond" : 8.166666666666666,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 13,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 13,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 9,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 5,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 5,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 10,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 10
    },
    "esHead" : 2209
  },
  "commandsByOutcome" : {
    "ACK" : 490
  }
}
```

## Timeline

- +7,9 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,0 s: load done: 490 requests
- +69,0 s: invariants: converging up to 90 s
- +73,1 s: cleanup start
- +75,0 s: cleanup done

### stack actions

```
```

