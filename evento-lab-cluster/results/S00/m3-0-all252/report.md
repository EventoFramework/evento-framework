# S00: Baseline, no faults

- Label: `m3-0-all252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:47:52.643366200Z, finished: 2026-10-20T19:49:08.915928400Z (76 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m3-0-all252-df6fcb-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 483/483 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 483 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3818 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 63 payments for 63 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 483 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3818 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=483 ack=483 failed=0 p50=857ms p95=1756ms p99=2272ms max=3042ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 483,
    "acked" : 483,
    "failed" : 0,
    "p50" : 857,
    "p95" : 1756,
    "p99" : 2272,
    "max" : 3042,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 282,
      "p95" : 604,
      "p99" : 721,
      "max" : 721,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 703,
      "p95" : 1240,
      "p99" : 1432,
      "max" : 1432,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 1192,
      "p95" : 1846,
      "p99" : 2944,
      "max" : 2944,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1254,
      "p95" : 1959,
      "p99" : 3042,
      "max" : 3042,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 348,
      "p95" : 603,
      "p99" : 716,
      "max" : 716,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1161,
      "p95" : 2084,
      "p99" : 2337,
      "max" : 2337,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 710,
      "p95" : 1270,
      "p99" : 1613,
      "max" : 1613,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 543,
  "esAppendsPerSecond" : 9.05,
  "ackedCommandsPerSecond" : 8.05,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 546,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 12,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 21,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 21
    }
  },
  "commandsByOutcome" : {
    "ACK" : 483
  }
}
```

## Timeline

- +7,9 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +70,0 s: load done: 483 requests
- +70,0 s: invariants: converging up to 90 s
- +74,5 s: cleanup start
- +76,3 s: cleanup done

### stack actions

```
```

