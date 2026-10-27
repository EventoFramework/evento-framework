# S00: Baseline, no faults

- Label: `c-m2-srv252-bnd3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T14:03:42.522500400Z, finished: 2026-10-22T14:04:58.694978600Z (76 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m2-srv252-bnd3-894460-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 488/488 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 488 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3441 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 68 payments for 68 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 488 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3441 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=488 ack=488 failed=0 p50=849ms p95=1466ms p99=1785ms max=1941ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 488,
    "acked" : 488,
    "failed" : 0,
    "p50" : 849,
    "p95" : 1466,
    "p99" : 1785,
    "max" : 1941,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 258,
      "p95" : 448,
      "p99" : 521,
      "max" : 521,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 745,
      "p95" : 1049,
      "p99" : 1325,
      "max" : 1325,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 1082,
      "p95" : 1739,
      "p99" : 1941,
      "max" : 1941,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1092,
      "p95" : 1633,
      "p99" : 1792,
      "max" : 1792,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 251,
      "p95" : 366,
      "p99" : 443,
      "max" : 443,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1088,
      "p95" : 1492,
      "p99" : 1785,
      "max" : 1785,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 641,
      "p95" : 1095,
      "p99" : 1381,
      "max" : 1381,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 553,
  "esAppendsPerSecond" : 9.216666666666667,
  "ackedCommandsPerSecond" : 8.133333333333333,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 556,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 13,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 6,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 13,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 6,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 23,
      "EU/order-query_OrderProjector_1_EUROPA" : 24,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 23,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 24
    }
  },
  "commandsByOutcome" : {
    "ACK" : 488
  }
}
```

## Timeline

- +7,8 s: scenario start
- +7,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +70,2 s: load done: 488 requests
- +70,2 s: invariants: converging up to 90 s
- +74,3 s: cleanup start
- +76,2 s: cleanup done

### stack actions

```
```

