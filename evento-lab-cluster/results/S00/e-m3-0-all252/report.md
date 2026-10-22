# S00: Baseline, no faults

- Label: `e-m3-0-all252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T17:00:35.160789900Z, finished: 2026-10-22T17:01:50.933625200Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m3-0-all252-6ce6e7-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 486/486 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 486 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3388 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 66 payments for 66 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 486 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3388 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=486 ack=486 failed=0 p50=847ms p95=1570ms p99=1749ms max=1995ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 486,
    "acked" : 486,
    "failed" : 0,
    "p50" : 847,
    "p95" : 1570,
    "p99" : 1749,
    "max" : 1995,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 257,
      "p95" : 453,
      "p99" : 516,
      "max" : 516,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 702,
      "p95" : 1161,
      "p99" : 1406,
      "max" : 1406,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1070,
      "p95" : 1656,
      "p99" : 1844,
      "max" : 1844,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1098,
      "p95" : 1706,
      "p99" : 1995,
      "max" : 1995,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 189,
      "p95" : 385,
      "p99" : 617,
      "max" : 617,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 1143,
      "p95" : 1633,
      "p99" : 1979,
      "max" : 1979,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 673,
      "p95" : 1230,
      "p99" : 1438,
      "max" : 1438,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 548,
  "esAppendsPerSecond" : 9.133333333333333,
  "ackedCommandsPerSecond" : 8.1,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 16,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 16,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 28,
      "EU/order-query_OrderProjector_1_EUROPA" : 30,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 28,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 30
    },
    "esHead" : 552
  },
  "commandsByOutcome" : {
    "ACK" : 486
  }
}
```

## Timeline

- +7,9 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,8 s: load done: 486 requests
- +69,9 s: invariants: converging up to 90 s
- +74,0 s: cleanup start
- +75,8 s: cleanup done

### stack actions

```
```

