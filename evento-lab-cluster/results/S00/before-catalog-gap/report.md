# S00: Baseline, no faults

- Label: `before`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T12:16:15.964929200Z, finished: 2026-09-29T12:18:34.402835300Z (138 s)
- Verdicts: {UNEXPECTED=3, AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s00-before-f01020-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **UNEXPECTED** | 100% of commands 2xx with no fault injected |  | 223/314 acknowledged, failures {timeout=91} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 223 acknowledged ops missing; 85 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **UNEXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | 3 duplicate finding(s) |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 134 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 134 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 13056 ms |
| I7 catalog matches live bundles | **UNEXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 1 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 28 payments for 28 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 223 acknowledged ops missing; 85 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **FAIL** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | 3 duplicate finding(s) |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 134 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 134 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 13056 ms |
| I7 | catalog matches live bundles | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 1 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=314 ack=223 failed=91 p50=16945ms p95=30474ms p99=30489ms max=30505ms failures={timeout=91} |

<details><summary>I2: 3 violation(s)</summary>

- s00-before-f01020-sud-america-6: 3 x PaymentProcessedEvent
- payment for s00-before-f01020-sud-america-6 executed 3 times
- payment for s00-before-f01020-india-1 executed 2 times

</details>

<details><summary>I7: 1 violation(s)</summary>

- bundle order-observer has no core__handler rows

</details>

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 314,
    "acked" : 223,
    "failed" : 91,
    "p50" : 16945,
    "p95" : 30474,
    "p99" : 30489,
    "max" : 30505,
    "kinds" : {
      "timeout" : 91
    }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 11368,
      "p95" : 18566,
      "p99" : 19230,
      "max" : 19230,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 39,
      "acked" : 22,
      "failed" : 17,
      "p50" : 26233,
      "p95" : 30372,
      "p99" : 30415,
      "max" : 30415,
      "kinds" : {
        "timeout" : 17
      }
    },
    "AUSTRALIA_NZ" : {
      "count" : 31,
      "acked" : 11,
      "failed" : 20,
      "p50" : 30281,
      "p95" : 30477,
      "p99" : 30479,
      "max" : 30479,
      "kinds" : {
        "timeout" : 20
      }
    },
    "CINA" : {
      "count" : 30,
      "acked" : 11,
      "failed" : 19,
      "p50" : 30110,
      "p95" : 30405,
      "p99" : 30504,
      "max" : 30504,
      "kinds" : {
        "timeout" : 19
      }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 10394,
      "p95" : 18811,
      "p99" : 19321,
      "max" : 19321,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 31,
      "acked" : 13,
      "failed" : 18,
      "p50" : 30133,
      "p95" : 30456,
      "p99" : 30505,
      "max" : 30505,
      "kinds" : {
        "timeout" : 18
      }
    },
    "SUD_AMERICA" : {
      "count" : 39,
      "acked" : 22,
      "failed" : 17,
      "p50" : 27840,
      "p95" : 30489,
      "p99" : 30489,
      "max" : 30489,
      "kinds" : {
        "timeout" : 17
      }
    }
  },
  "esEventsAppended" : 309,
  "esAppendsPerSecond" : 5.15,
  "ackedCommandsPerSecond" : 3.716666666666667,
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 35,
    "timeout: CompleteOrderCommand: request expired after <n>ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 55
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 338,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 24,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 24,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 12,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 106,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 217,
      "EU/order-observer_NotificationObserver_1_*" : 1,
      "EU/order-query_OrderProjector_1_*" : 1,
      "EU/order-query_OrderProjector_1_AFRICA" : 5,
      "EU/order-query_OrderProjector_1_EUROPA" : 11,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 5,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 11
    }
  },
  "commandsByOutcome" : {
    "ACK" : 223,
    "ERROR" : 91
  }
}
```

## Timeline

- +6,0 s: scenario start
- +6,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +116,3 s: load done: 314 requests
- +116,3 s: invariants: converging up to 90 s
- +136,4 s: cleanup start
- +138,4 s: cleanup done

### stack actions

```
```

