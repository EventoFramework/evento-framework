# S00: Baseline, no faults

- Label: `c-m3-3-all3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T14:20:53.392146700Z, finished: 2026-10-22T14:22:05.234233800Z (71 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m3-3-all3-0a78f0-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 482/482 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 482 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 258 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 62 payments for 62 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 482 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 258 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=482 ack=482 failed=0 p50=440ms p95=1056ms p99=1413ms max=1787ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 482,
    "acked" : 482,
    "failed" : 0,
    "p50" : 440,
    "p95" : 1056,
    "p99" : 1413,
    "max" : 1787,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 179,
      "p95" : 369,
      "p99" : 462,
      "max" : 462,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 453,
      "p95" : 728,
      "p99" : 1413,
      "max" : 1413,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 546,
      "p95" : 1118,
      "p99" : 1682,
      "max" : 1682,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 642,
      "p95" : 1107,
      "p99" : 1559,
      "max" : 1559,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 266,
      "p95" : 547,
      "p99" : 1124,
      "max" : 1124,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 453,
      "p95" : 1245,
      "p99" : 1787,
      "max" : 1787,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 428,
      "p95" : 803,
      "p99" : 1096,
      "max" : 1096,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 543,
  "esAppendsPerSecond" : 9.05,
  "ackedCommandsPerSecond" : 8.033333333333333,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 2228,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 6,
      "APAC/order-query_OrderProjector_1_CINA" : 13,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 6,
      "APAC/order-saga_PaymentSaga_1_CINA" : 13,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 4,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 25,
      "EU/order-query_OrderProjector_1_EUROPA" : 11,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 25,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 11
    }
  },
  "commandsByOutcome" : {
    "ACK" : 482
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,0 s: load done: 482 requests
- +69,0 s: invariants: converging up to 90 s
- +70,0 s: cleanup start
- +71,8 s: cleanup done

### stack actions

```
```

