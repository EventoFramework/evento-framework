# S00: Baseline, no faults

- Label: `e-m2-srv252-bnd3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T16:55:20.062036600Z, finished: 2026-10-22T16:56:36.335089Z (76 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m2-srv252-bnd3-0213f5-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 481/481 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 481 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3404 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 61 payments for 61 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 481 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3404 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=481 ack=481 failed=0 p50=830ms p95=1586ms p99=1937ms max=3111ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 481,
    "acked" : 481,
    "failed" : 0,
    "p50" : 830,
    "p95" : 1586,
    "p99" : 1937,
    "max" : 3111,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 251,
      "p95" : 435,
      "p99" : 622,
      "max" : 622,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 671,
      "p95" : 1050,
      "p99" : 1160,
      "max" : 1160,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1149,
      "p95" : 1678,
      "p99" : 2150,
      "max" : 2150,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 1160,
      "p95" : 1791,
      "p99" : 3111,
      "max" : 3111,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 262,
      "p95" : 523,
      "p99" : 537,
      "max" : 537,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 64,
      "acked" : 64,
      "failed" : 0,
      "p50" : 1156,
      "p95" : 1749,
      "p99" : 2826,
      "max" : 2826,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 685,
      "p95" : 1037,
      "p99" : 1214,
      "max" : 1214,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 540,
  "esAppendsPerSecond" : 9.0,
  "ackedCommandsPerSecond" : 8.016666666666667,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 8,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 9,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 5,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 5,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 25,
      "EU/order-query_OrderProjector_1_EUROPA" : 15,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 25,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 15
    },
    "esHead" : 542
  },
  "commandsByOutcome" : {
    "ACK" : 481
  }
}
```

## Timeline

- +7,7 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,9 s: load done: 481 requests
- +70,0 s: invariants: converging up to 90 s
- +74,4 s: cleanup start
- +76,3 s: cleanup done

### stack actions

```
```

