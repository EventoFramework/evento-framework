# S00: Baseline, no faults

- Label: `c-m3-0-all252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T14:09:01.741730500Z, finished: 2026-10-22T14:10:17.663672500Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m3-0-all252-4972aa-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 503/503 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 503 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3519 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 83 payments for 83 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 503 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3519 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=503 ack=503 failed=0 p50=854ms p95=1481ms p99=1776ms max=2030ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 503,
    "acked" : 503,
    "failed" : 0,
    "p50" : 854,
    "p95" : 1481,
    "p99" : 1776,
    "max" : 2030,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 265,
      "p95" : 460,
      "p99" : 610,
      "max" : 610,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 722,
      "p95" : 1129,
      "p99" : 1402,
      "max" : 1402,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 74,
      "acked" : 74,
      "failed" : 0,
      "p50" : 1077,
      "p95" : 1578,
      "p99" : 2030,
      "max" : 2030,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1150,
      "p95" : 1706,
      "p99" : 1925,
      "max" : 1925,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 251,
      "p95" : 441,
      "p99" : 554,
      "max" : 554,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 75,
      "acked" : 75,
      "failed" : 0,
      "p50" : 1118,
      "p95" : 1759,
      "p99" : 1939,
      "max" : 1939,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 691,
      "p95" : 995,
      "p99" : 1201,
      "max" : 1201,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 584,
  "esAppendsPerSecond" : 9.733333333333333,
  "ackedCommandsPerSecond" : 8.383333333333333,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 586,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 4,
      "APAC/order-query_OrderProjector_1_INDIA" : 5,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 4,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 5,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 15,
      "EU/order-query_OrderProjector_1_EUROPA" : 25,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 15,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 25
    }
  },
  "commandsByOutcome" : {
    "ACK" : 503
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,9 s: load done: 503 requests
- +69,9 s: invariants: converging up to 90 s
- +74,1 s: cleanup start
- +75,9 s: cleanup done

### stack actions

```
```

