# S00: Baseline, no faults

- Label: `v3e`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T15:37:50.386562800Z, finished: 2026-10-22T15:39:05.763381Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-v3e-9adf40-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 486/486 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 486 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 298 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 298 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=486 ack=486 failed=0 p50=523ms p95=1138ms p99=1466ms max=2464ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 486,
    "acked" : 486,
    "failed" : 0,
    "p50" : 523,
    "p95" : 1138,
    "p99" : 1466,
    "max" : 2464,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 220,
      "p95" : 538,
      "p99" : 557,
      "max" : 557,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 530,
      "p95" : 812,
      "p99" : 930,
      "max" : 930,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 811,
      "p95" : 1082,
      "p99" : 2464,
      "max" : 2464,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 862,
      "p95" : 1163,
      "p99" : 1982,
      "max" : 1982,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 430,
      "p95" : 633,
      "p99" : 773,
      "max" : 773,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 621,
      "p95" : 1405,
      "p99" : 1618,
      "max" : 1618,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 510,
      "p95" : 703,
      "p99" : 1061,
      "max" : 1061,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 551,
  "esAppendsPerSecond" : 9.183333333333334,
  "ackedCommandsPerSecond" : 8.1,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 16,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 16,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 3,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 3,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 5,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 6,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 6,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    },
    "esHead" : 551
  },
  "commandsByOutcome" : {
    "ACK" : 486
  }
}
```

## Timeline

- +8,1 s: scenario start
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,3 s: load done: 486 requests
- +69,3 s: invariants: converging up to 90 s
- +73,5 s: cleanup start
- +75,4 s: cleanup done

### stack actions

```
```

