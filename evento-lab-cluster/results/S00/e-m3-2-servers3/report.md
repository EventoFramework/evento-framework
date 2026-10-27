# S00: Baseline, no faults

- Label: `e-m3-2-servers3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T17:05:43.950850100Z, finished: 2026-10-22T17:08:05.500359700Z (141 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m3-2-servers3-85f6a3-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, gateway-sud-america=evento-eu, order-command-am=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 489/489 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 489 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3532 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 69 payments for 69 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 489 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3532 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=489 ack=489 failed=0 p50=536ms p95=1035ms p99=1252ms max=1662ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 489,
    "acked" : 489,
    "failed" : 0,
    "p50" : 536,
    "p95" : 1035,
    "p99" : 1252,
    "max" : 1662,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 267,
      "p95" : 448,
      "p99" : 545,
      "max" : 545,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 528,
      "p95" : 728,
      "p99" : 866,
      "max" : 866,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 715,
      "p95" : 1211,
      "p99" : 1393,
      "max" : 1393,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 759,
      "p95" : 998,
      "p99" : 1662,
      "max" : 1662,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 105,
      "p95" : 181,
      "p99" : 193,
      "max" : 193,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 703,
      "p95" : 1171,
      "p99" : 1531,
      "max" : 1531,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 527,
      "p95" : 808,
      "p99" : 890,
      "max" : 890,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 556,
  "esAppendsPerSecond" : 9.266666666666667,
  "ackedCommandsPerSecond" : 8.15,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1650,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 9,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 5,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 5,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 17,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 17,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12
    }
  },
  "commandsByOutcome" : {
    "ACK" : 489
  }
}
```

## Timeline

- +8,0 s: placement: 10 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, gateway-sud-america=evento-eu, order-command-am=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +74,3 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, gateway-sud-america=true, order-command-am=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +74,4 s: scenario start
- +74,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +135,4 s: load done: 489 requests
- +135,5 s: invariants: converging up to 90 s
- +139,7 s: cleanup start
- +141,5 s: cleanup done

### stack actions

```
2026-10-22T17:05:51.918323800Z docker restart gateway-australia-nz gateway-cina gateway-india gateway-sud-america order-command-am order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
```

