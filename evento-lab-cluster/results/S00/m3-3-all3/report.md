# S00: Baseline, no faults

- Label: `m3-3-all3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:58:44.042565400Z, finished: 2026-10-20T19:59:59.546295800Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m3-3-all3-899a59-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 489/489 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 489 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3437 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 69 payments for 69 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 489 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3437 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=489 ack=489 failed=0 p50=532ms p95=1175ms p99=1529ms max=1685ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 489,
    "acked" : 489,
    "failed" : 0,
    "p50" : 532,
    "p95" : 1175,
    "p99" : 1529,
    "max" : 1685,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 173,
      "p95" : 525,
      "p99" : 542,
      "max" : 542,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 610,
      "p95" : 768,
      "p99" : 818,
      "max" : 818,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 64,
      "acked" : 64,
      "failed" : 0,
      "p50" : 645,
      "p95" : 1462,
      "p99" : 1529,
      "max" : 1529,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 659,
      "p95" : 1361,
      "p99" : 1509,
      "max" : 1509,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 349,
      "p95" : 532,
      "p99" : 633,
      "max" : 633,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 670,
      "p95" : 1555,
      "p99" : 1685,
      "max" : 1685,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 600,
      "p95" : 830,
      "p99" : 892,
      "max" : 892,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 557,
  "esAppendsPerSecond" : 9.283333333333333,
  "ackedCommandsPerSecond" : 8.15,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 1,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 3,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 4,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 3,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 4,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 19,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 19
    },
    "esHead" : 2214
  },
  "commandsByOutcome" : {
    "ACK" : 489
  }
}
```

## Timeline

- +8,4 s: scenario start
- +8,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,5 s: load done: 489 requests
- +69,6 s: invariants: converging up to 90 s
- +73,7 s: cleanup start
- +75,5 s: cleanup done

### stack actions

```
```

