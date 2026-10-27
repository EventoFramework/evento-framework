# S00: Baseline, no faults

- Label: `m1-srv3-bnd252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:32:53.533133400Z, finished: 2026-10-20T19:34:12.252134300Z (78 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m1-srv3-bnd252-020990-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 498/498 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 498 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3527 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 78 payments for 78 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 498 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3527 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=498 ack=498 failed=0 p50=619ms p95=1386ms p99=1842ms max=2590ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 498,
    "acked" : 498,
    "failed" : 0,
    "p50" : 619,
    "p95" : 1386,
    "p99" : 1842,
    "max" : 2590,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 366,
      "p95" : 812,
      "p99" : 1722,
      "max" : 1722,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 622,
      "p95" : 1207,
      "p99" : 1842,
      "max" : 1842,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 879,
      "p95" : 1578,
      "p99" : 2072,
      "max" : 2072,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 856,
      "p95" : 1475,
      "p99" : 2492,
      "max" : 2492,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 456,
      "p95" : 946,
      "p99" : 1504,
      "max" : 1504,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 881,
      "p95" : 1548,
      "p99" : 2590,
      "max" : 2590,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 614,
      "p95" : 1163,
      "p99" : 1782,
      "max" : 1782,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 575,
  "esAppendsPerSecond" : 9.583333333333334,
  "ackedCommandsPerSecond" : 8.3,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 576,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 5,
      "APAC/order-query_OrderProjector_1_INDIA" : 6,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 5,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 6,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 4,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 4,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12
    }
  },
  "commandsByOutcome" : {
    "ACK" : 498
  }
}
```

## Timeline

- +11,4 s: scenario start
- +11,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +72,5 s: load done: 498 requests
- +72,5 s: invariants: converging up to 90 s
- +76,8 s: cleanup start
- +78,7 s: cleanup done

### stack actions

```
```

