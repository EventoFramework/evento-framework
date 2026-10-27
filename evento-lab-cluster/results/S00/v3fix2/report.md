# S00: Baseline, no faults

- Label: `v3fix2`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T18:23:01.508020500Z, finished: 2026-10-20T18:24:16.269917600Z (74 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-v3fix2-f4551b-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 495/495 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 495 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3430 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 75 payments for 75 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 495 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3430 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=495 ack=495 failed=0 p50=498ms p95=1078ms p99=1174ms max=1424ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 495,
    "acked" : 495,
    "failed" : 0,
    "p50" : 498,
    "p95" : 1078,
    "p99" : 1174,
    "max" : 1424,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 178,
      "p95" : 444,
      "p99" : 568,
      "max" : 568,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 501,
      "p95" : 647,
      "p99" : 714,
      "max" : 714,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 561,
      "p95" : 1132,
      "p99" : 1424,
      "max" : 1424,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 748,
      "p95" : 1132,
      "p99" : 1190,
      "max" : 1190,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 274,
      "p95" : 479,
      "p99" : 623,
      "max" : 623,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 580,
      "p95" : 1148,
      "p99" : 1285,
      "max" : 1285,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 556,
      "p95" : 765,
      "p99" : 836,
      "max" : 836,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 568,
  "esAppendsPerSecond" : 9.466666666666667,
  "ackedCommandsPerSecond" : 8.25,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 4,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 12,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 8,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 8,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 6,
      "EU/order-query_OrderProjector_1_EUROPA" : 20,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 6,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 20
    },
    "esHead" : 1441
  },
  "commandsByOutcome" : {
    "ACK" : 495
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +68,8 s: load done: 495 requests
- +68,9 s: invariants: converging up to 90 s
- +73,0 s: cleanup start
- +74,8 s: cleanup done

### stack actions

```
```

