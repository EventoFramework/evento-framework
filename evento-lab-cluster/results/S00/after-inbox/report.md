# S00: Baseline, no faults

- Label: `after-inbox`
- Gaps exercised: G9 (reference)
- Started: 2026-10-09T08:54:05.786150Z, finished: 2026-10-09T08:55:23.483059600Z (77 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-after-inbox-972168-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 496/496 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 496 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3509 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 76 payments for 76 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 496 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3509 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=496 ack=496 failed=0 p50=851ms p95=1703ms p99=2307ms max=3719ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 496,
    "acked" : 496,
    "failed" : 0,
    "p50" : 851,
    "p95" : 1703,
    "p99" : 2307,
    "max" : 3719,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 262,
      "p95" : 527,
      "p99" : 851,
      "max" : 851,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 667,
      "p95" : 1158,
      "p99" : 1783,
      "max" : 1783,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1181,
      "p95" : 2067,
      "p99" : 3544,
      "max" : 3544,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1135,
      "p95" : 2037,
      "p99" : 3719,
      "max" : 3719,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 264,
      "p95" : 482,
      "p99" : 775,
      "max" : 775,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1220,
      "p95" : 2067,
      "p99" : 2308,
      "max" : 2308,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 75,
      "acked" : 75,
      "failed" : 0,
      "p50" : 704,
      "p95" : 1141,
      "p99" : 1448,
      "max" : 1448,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 570,
  "esAppendsPerSecond" : 9.5,
  "ackedCommandsPerSecond" : 8.266666666666667,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 572,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 12,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 8,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 5,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 5,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 27,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 27
    }
  },
  "commandsByOutcome" : {
    "ACK" : 496
  }
}
```

## Timeline

- +8,8 s: scenario start
- +9,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +71,2 s: load done: 496 requests
- +71,3 s: invariants: converging up to 90 s
- +75,7 s: cleanup start
- +77,7 s: cleanup done

### stack actions

```
```

