# S00: Baseline, no faults

- Label: `after`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T14:10:33.424059400Z, finished: 2026-09-29T14:11:47.598397700Z (74 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-after-11dc19-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 492/492 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 492 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3545 ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3545 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=492 ack=492 failed=0 p50=846ms p95=1581ms p99=1901ms max=1980ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 492,
    "acked" : 492,
    "failed" : 0,
    "p50" : 846,
    "p95" : 1581,
    "p99" : 1901,
    "max" : 1980,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 74,
      "acked" : 74,
      "failed" : 0,
      "p50" : 131,
      "p95" : 341,
      "p99" : 449,
      "max" : 449,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 761,
      "p95" : 1126,
      "p99" : 1202,
      "max" : 1202,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1156,
      "p95" : 1665,
      "p99" : 1980,
      "max" : 1980,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1168,
      "p95" : 1581,
      "p99" : 1958,
      "max" : 1958,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 265,
      "p95" : 451,
      "p99" : 611,
      "max" : 611,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1178,
      "p95" : 1772,
      "p99" : 1927,
      "max" : 1927,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 74,
      "acked" : 74,
      "failed" : 0,
      "p50" : 733,
      "p95" : 1052,
      "p99" : 1390,
      "max" : 1390,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 561,
  "esAppendsPerSecond" : 9.35,
  "ackedCommandsPerSecond" : 8.2,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 564,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 13,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 13,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 5,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 5,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 22,
      "EU/order-query_OrderProjector_1_EUROPA" : 19,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 22,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 19
    }
  },
  "commandsByOutcome" : {
    "ACK" : 492
  }
}
```

## Timeline

- +5,9 s: scenario start
- +6,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +67,8 s: load done: 492 requests
- +67,9 s: invariants: converging up to 90 s
- +72,2 s: cleanup start
- +74,2 s: cleanup done

### stack actions

```
```

