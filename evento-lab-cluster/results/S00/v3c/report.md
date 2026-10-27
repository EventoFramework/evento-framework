# S00: Baseline, no faults

- Label: `v3c`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T12:38:59.785778500Z, finished: 2026-10-22T12:40:15.803933400Z (76 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-v3c-096e29-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 490/490 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3466 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3466 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=490 ack=490 failed=0 p50=480ms p95=1264ms p99=1855ms max=2491ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 490,
    "acked" : 490,
    "failed" : 0,
    "p50" : 480,
    "p95" : 1264,
    "p99" : 1855,
    "max" : 2491,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 355,
      "p95" : 573,
      "p99" : 1084,
      "max" : 1084,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 556,
      "p95" : 878,
      "p99" : 1855,
      "max" : 1855,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 728,
      "p95" : 1169,
      "p99" : 2283,
      "max" : 2283,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 739,
      "p95" : 1054,
      "p99" : 2491,
      "max" : 2491,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 186,
      "p95" : 662,
      "p99" : 1373,
      "max" : 1373,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 544,
      "p95" : 1567,
      "p99" : 2087,
      "max" : 2087,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 456,
      "p95" : 806,
      "p99" : 2296,
      "max" : 2296,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 559,
  "esAppendsPerSecond" : 9.316666666666666,
  "ackedCommandsPerSecond" : 8.166666666666666,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 5,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 17,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 17
    },
    "esHead" : 560
  },
  "commandsByOutcome" : {
    "ACK" : 490
  }
}
```

## Timeline

- +8,7 s: scenario start
- +8,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,7 s: load done: 490 requests
- +69,8 s: invariants: converging up to 90 s
- +74,0 s: cleanup start
- +76,0 s: cleanup done

### stack actions

```
```

