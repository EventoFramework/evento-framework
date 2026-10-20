# S00: Baseline, no faults

- Label: `m2-srv252-bnd3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:42:32.191217200Z, finished: 2026-10-20T19:43:48.238330700Z (76 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m2-srv252-bnd3-3a0259-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 488/488 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 488 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3391 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3391 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=488 ack=488 failed=0 p50=865ms p95=1731ms p99=2022ms max=2409ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 488,
    "acked" : 488,
    "failed" : 0,
    "p50" : 865,
    "p95" : 1731,
    "p99" : 2022,
    "max" : 2409,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 354,
      "p95" : 632,
      "p99" : 733,
      "max" : 733,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 65,
      "acked" : 65,
      "failed" : 0,
      "p50" : 804,
      "p95" : 1085,
      "p99" : 1568,
      "max" : 1568,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 1157,
      "p95" : 1771,
      "p99" : 2145,
      "max" : 2145,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 1180,
      "p95" : 1881,
      "p99" : 2409,
      "max" : 2409,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 255,
      "p95" : 452,
      "p99" : 524,
      "max" : 524,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1169,
      "p95" : 1877,
      "p99" : 1960,
      "max" : 1960,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 794,
      "p95" : 1091,
      "p99" : 1257,
      "max" : 1257,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 554,
  "esAppendsPerSecond" : 9.233333333333333,
  "ackedCommandsPerSecond" : 8.133333333333333,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 556,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 16,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 16,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 9,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 9,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 18,
      "EU/order-query_OrderProjector_1_EUROPA" : 28,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 18,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 28
    }
  },
  "commandsByOutcome" : {
    "ACK" : 488
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +70,2 s: load done: 488 requests
- +70,2 s: invariants: converging up to 90 s
- +74,2 s: cleanup start
- +76,0 s: cleanup done

### stack actions

```
```

