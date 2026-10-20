# S00: Baseline, no faults

- Label: `v3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T14:53:21.540243500Z, finished: 2026-10-20T14:54:36.668726700Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-v3-608403-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 490/490 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3495 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3495 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=490 ack=490 failed=0 p50=481ms p95=1090ms p99=1301ms max=1913ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 490,
    "acked" : 490,
    "failed" : 0,
    "p50" : 481,
    "p95" : 1090,
    "p99" : 1301,
    "max" : 1913,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 353,
      "p95" : 534,
      "p99" : 618,
      "max" : 618,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 488,
      "p95" : 680,
      "p99" : 1301,
      "max" : 1301,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 685,
      "p95" : 1253,
      "p99" : 1746,
      "max" : 1746,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 553,
      "p95" : 1201,
      "p99" : 1501,
      "max" : 1501,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 191,
      "p95" : 465,
      "p99" : 615,
      "max" : 615,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 707,
      "p95" : 1183,
      "p99" : 1913,
      "max" : 1913,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 530,
      "p95" : 787,
      "p99" : 958,
      "max" : 958,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 558,
  "esAppendsPerSecond" : 9.3,
  "ackedCommandsPerSecond" : 8.166666666666666,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 4,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 9,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 7,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 7,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 16,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 16,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    },
    "esHead" : 560
  },
  "commandsByOutcome" : {
    "ACK" : 490
  }
}
```

## Timeline

- +8,0 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,1 s: load done: 490 requests
- +69,1 s: invariants: converging up to 90 s
- +73,3 s: cleanup start
- +75,1 s: cleanup done

### stack actions

```
```

