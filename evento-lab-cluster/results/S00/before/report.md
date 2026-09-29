# S00: Baseline, no faults

- Label: `before`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T12:22:52.826265300Z, finished: 2026-09-29T12:25:15.951597Z (143 s)
- Verdicts: {UNEXPECTED=3, AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s00-before-7136d4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **UNEXPECTED** | 100% of commands 2xx with no fault injected |  | 227/319 acknowledged, failures {timeout=92} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 227 acknowledged ops missing; 83 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **UNEXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | 5 duplicate finding(s) |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 131 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 131 |
| I5 global completeness | **UNEXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 1 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 13123 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 30 payments for 30 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 227 acknowledged ops missing; 83 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **FAIL** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | 5 duplicate finding(s) |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 131 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 131 |
| I5 | global completeness | **FAIL** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 1 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 13123 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=319 ack=227 failed=92 p50=16353ms p95=30358ms p99=30474ms max=30501ms failures={timeout=92} |

<details><summary>I2: 5 violation(s)</summary>

- s00-before-7136d4-america-12: 2 x PaymentProcessedEvent
- s00-before-7136d4-sud-america-18: 2 x PaymentProcessedEvent
- payment for s00-before-7136d4-america-12 executed 2 times
- payment for s00-before-7136d4-sud-america-18 executed 2 times
- payment for s00-before-7136d4-india-1 executed 2 times

</details>

<details><summary>I5: 1 violation(s)</summary>

- s00-before-7136d4-india-50 global=CREATED/1/INDIA regional=CREATED/0/INDIA

</details>

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 319,
    "acked" : 227,
    "failed" : 92,
    "p50" : 16353,
    "p95" : 30358,
    "p99" : 30474,
    "max" : 30501,
    "kinds" : {
      "timeout" : 92
    }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 10347,
      "p95" : 17425,
      "p99" : 17894,
      "max" : 17894,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 41,
      "acked" : 24,
      "failed" : 17,
      "p50" : 26584,
      "p95" : 30252,
      "p99" : 30500,
      "max" : 30500,
      "kinds" : {
        "timeout" : 17
      }
    },
    "AUSTRALIA_NZ" : {
      "count" : 31,
      "acked" : 11,
      "failed" : 20,
      "p50" : 30121,
      "p95" : 30307,
      "p99" : 30494,
      "max" : 30494,
      "kinds" : {
        "timeout" : 20
      }
    },
    "CINA" : {
      "count" : 31,
      "acked" : 11,
      "failed" : 20,
      "p50" : 30103,
      "p95" : 30450,
      "p99" : 30501,
      "max" : 30501,
      "kinds" : {
        "timeout" : 20
      }
    },
    "EUROPA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 10319,
      "p95" : 17259,
      "p99" : 17894,
      "max" : 17894,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 32,
      "acked" : 14,
      "failed" : 18,
      "p50" : 30035,
      "p95" : 30363,
      "p99" : 30364,
      "max" : 30364,
      "kinds" : {
        "timeout" : 18
      }
    },
    "SUD_AMERICA" : {
      "count" : 40,
      "acked" : 23,
      "failed" : 17,
      "p50" : 25004,
      "p95" : 30221,
      "p99" : 30474,
      "max" : 30474,
      "kinds" : {
        "timeout" : 17
      }
    }
  },
  "esEventsAppended" : 310,
  "esAppendsPerSecond" : 5.166666666666667,
  "ackedCommandsPerSecond" : 3.783333333333333,
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 35,
    "timeout: CompleteOrderCommand: request expired after <n>ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 54
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 687,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 4,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 3,
      "APAC/order-query_OrderProjector_1_INDIA" : 8,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 100,
      "APAC/order-saga_PaymentSaga_1_CINA" : 208,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 252,
      "EU/order-observer_NotificationObserver_1_*" : 1,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 11,
      "EU/order-query_OrderProjector_1_EUROPA" : 7,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 11,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 7
    }
  },
  "commandsByOutcome" : {
    "ACK" : 227,
    "ERROR" : 92
  }
}
```

## Timeline

- +6,0 s: scenario start
- +6,2 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +114,5 s: load done: 319 requests
- +114,6 s: invariants: converging up to 90 s
- +141,2 s: cleanup start
- +143,1 s: cleanup done

### stack actions

```
```

