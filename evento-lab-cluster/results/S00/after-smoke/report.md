# S00: Baseline, no faults

- Label: `after-smoke`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T11:41:49.149399600Z, finished: 2026-09-29T11:43:03.749308200Z (74 s)
- Verdicts: {AS_EXPECTED=8, UNEXPECTED=1}

## Parameters

- orderIdPrefix: `s00-after-smoke-4e126c-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 498/498 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 498 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3565 ms |
| I7 catalog | **UNEXPECTED** |  |  | catalog could not be read |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 78 payments for 78 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 498 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3565 ms |
| I7 | catalog | **ERROR** |  | catalog could not be read |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=498 ack=498 failed=0 p50=846ms p95=1529ms p99=1675ms max=1899ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 498,
    "acked" : 498,
    "failed" : 0,
    "p50" : 846,
    "p95" : 1529,
    "p99" : 1675,
    "max" : 1899,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 116,
      "p95" : 337,
      "p99" : 462,
      "max" : 462,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 718,
      "p95" : 1024,
      "p99" : 1060,
      "max" : 1060,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 1121,
      "p95" : 1615,
      "p99" : 1714,
      "max" : 1714,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1122,
      "p95" : 1575,
      "p99" : 1666,
      "max" : 1666,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 74,
      "acked" : 74,
      "failed" : 0,
      "p50" : 267,
      "p95" : 467,
      "p99" : 563,
      "max" : 563,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 74,
      "acked" : 74,
      "failed" : 0,
      "p50" : 1109,
      "p95" : 1675,
      "p99" : 1899,
      "max" : 1899,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 729,
      "p95" : 1005,
      "p99" : 1059,
      "max" : 1059,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 575,
  "esAppendsPerSecond" : 9.583333333333334,
  "ackedCommandsPerSecond" : 8.3,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 707,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 15,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 4,
      "APAC/order-query_OrderProjector_1_INDIA" : 6,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 4,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 6,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 32,
      "EU/order-query_OrderProjector_1_EUROPA" : 22,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 32,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 22,
      "consumers/order-query_CatalogProjector_1_ALL" : 576
    }
  },
  "commandsByOutcome" : {
    "ACK" : 498
  }
}
```

## Timeline

- +6,3 s: scenario start
- +6,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +68,5 s: load done: 498 requests
- +68,6 s: invariants: converging up to 90 s
- +72,8 s: cleanup start
- +74,6 s: cleanup done

### stack actions

```
```

