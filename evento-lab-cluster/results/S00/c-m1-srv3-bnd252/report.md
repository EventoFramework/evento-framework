# S00: Baseline, no faults

- Label: `c-m1-srv3-bnd252`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T13:54:33.187238400Z, finished: 2026-10-22T13:55:48.275163200Z (75 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m1-srv3-bnd252-61a5cb-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 492/492 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 492 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3480 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 72 payments for 72 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 492 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3480 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=492 ack=492 failed=0 p50=543ms p95=1201ms p99=1504ms max=2013ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 492,
    "acked" : 492,
    "failed" : 0,
    "p50" : 543,
    "p95" : 1201,
    "p99" : 1504,
    "max" : 2013,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 402,
      "p95" : 569,
      "p99" : 669,
      "max" : 669,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 569,
      "p95" : 729,
      "p99" : 893,
      "max" : 893,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 723,
      "p95" : 1428,
      "p99" : 1612,
      "max" : 1612,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 816,
      "p95" : 1197,
      "p99" : 1869,
      "max" : 1869,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 438,
      "p95" : 624,
      "p99" : 721,
      "max" : 721,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 734,
      "p95" : 1316,
      "p99" : 2013,
      "max" : 2013,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 508,
      "p95" : 778,
      "p99" : 1032,
      "max" : 1032,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 562,
  "esAppendsPerSecond" : 9.366666666666667,
  "ackedCommandsPerSecond" : 8.2,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 564,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 17,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 17,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 5,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 6,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 6,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 4,
      "EU/order-query_OrderProjector_1_EUROPA" : 3,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 4,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 3
    }
  },
  "commandsByOutcome" : {
    "ACK" : 492
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +69,0 s: load done: 492 requests
- +69,0 s: invariants: converging up to 90 s
- +73,2 s: cleanup start
- +75,1 s: cleanup done

### stack actions

```
```

