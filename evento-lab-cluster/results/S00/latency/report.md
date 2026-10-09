# S00: Baseline, no faults

- Label: `latency`
- Gaps exercised: G9 (reference)
- Started: 2026-10-09T13:57:35.989169300Z, finished: 2026-10-09T14:05:50.833888900Z (494 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-latency-8e6ef6-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=480, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=6.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 1177/1177 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1177 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 336 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 336 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 274 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 169 payments for 169 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1177 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 336 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 336 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 274 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1177 ack=1177 failed=0 p50=785ms p95=1490ms p99=1607ms max=1964ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 1177,
    "acked" : 1177,
    "failed" : 0,
    "p50" : 785,
    "p95" : 1490,
    "p99" : 1607,
    "max" : 1964,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 167,
      "acked" : 167,
      "failed" : 0,
      "p50" : 108,
      "p95" : 235,
      "p99" : 278,
      "max" : 335,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 168,
      "acked" : 168,
      "failed" : 0,
      "p50" : 675,
      "p95" : 970,
      "p99" : 1020,
      "max" : 1020,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 164,
      "acked" : 164,
      "failed" : 0,
      "p50" : 1057,
      "p95" : 1570,
      "p99" : 1632,
      "max" : 1825,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 168,
      "acked" : 168,
      "failed" : 0,
      "p50" : 1089,
      "p95" : 1563,
      "p99" : 1632,
      "max" : 1964,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 172,
      "acked" : 172,
      "failed" : 0,
      "p50" : 103,
      "p95" : 164,
      "p99" : 189,
      "max" : 413,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 166,
      "acked" : 166,
      "failed" : 0,
      "p50" : 1044,
      "p95" : 1519,
      "p99" : 1704,
      "max" : 1740,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 172,
      "acked" : 172,
      "failed" : 0,
      "p50" : 610,
      "p95" : 911,
      "p99" : 973,
      "max" : 1018,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 1346,
  "esAppendsPerSecond" : 2.8041666666666667,
  "ackedCommandsPerSecond" : 2.4520833333333334,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1347,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 11,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 11,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 5,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 5,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 19,
      "EU/order-query_OrderProjector_1_EUROPA" : 17,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 19,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 17
    }
  },
  "commandsByOutcome" : {
    "ACK" : 1177
  }
}
```

## Timeline

- +10,1 s: scenario start
- +10,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @6.0/min for 480 s
- +491,4 s: load done: 1177 requests
- +491,4 s: invariants: converging up to 90 s
- +492,5 s: cleanup start
- +494,8 s: cleanup done

### stack actions

```
```

