# S00: Baseline, no faults

- Label: `e-m1-srv3-bnd252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T16:46:00.433288Z, finished: 2026-10-22T16:47:15.652002600Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m1-srv3-bnd252-68ad2b-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 490/490 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3563 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3563 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=490 ack=490 failed=0 p50=456ms p95=1037ms p99=1240ms max=1999ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 490,
    "acked" : 490,
    "failed" : 0,
    "p50" : 456,
    "p95" : 1037,
    "p99" : 1240,
    "max" : 1999,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 110,
      "p95" : 209,
      "p99" : 478,
      "max" : 478,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 512,
      "p95" : 731,
      "p99" : 864,
      "max" : 864,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 600,
      "p95" : 1148,
      "p99" : 1999,
      "max" : 1999,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 718,
      "p95" : 1077,
      "p99" : 1910,
      "max" : 1910,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 195,
      "p95" : 462,
      "p99" : 556,
      "max" : 556,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 556,
      "p95" : 1179,
      "p99" : 1773,
      "max" : 1773,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 444,
      "p95" : 622,
      "p99" : 1284,
      "max" : 1284,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 556,
  "esAppendsPerSecond" : 9.266666666666667,
  "ackedCommandsPerSecond" : 8.166666666666666,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 560,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 10,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 10,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 27,
      "EU/order-query_OrderProjector_1_EUROPA" : 6,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 27,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 6
    }
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
- +69,1 s: invariants: converging up to 90 s
- +73,3 s: cleanup start
- +75,2 s: cleanup done

### stack actions

```
```

