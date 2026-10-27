# S00: Baseline, no faults

- Label: `c-m3-1-eu3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T14:10:44.858491200Z, finished: 2026-10-22T14:12:52.351095600Z (127 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m3-1-eu3-a68e5a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 485/485 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 485 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3458 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 65 payments for 65 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 485 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3458 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=485 ack=485 failed=0 p50=845ms p95=1462ms p99=1674ms max=1842ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 485,
    "acked" : 485,
    "failed" : 0,
    "p50" : 845,
    "p95" : 1462,
    "p99" : 1674,
    "max" : 1842,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 215,
      "p95" : 434,
      "p99" : 527,
      "max" : 527,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 715,
      "p95" : 1118,
      "p99" : 1209,
      "max" : 1209,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1075,
      "p95" : 1491,
      "p99" : 1549,
      "max" : 1549,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 1105,
      "p95" : 1547,
      "p99" : 1842,
      "max" : 1842,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 130,
      "p95" : 281,
      "p99" : 366,
      "max" : 366,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 1062,
      "p95" : 1674,
      "p99" : 1756,
      "max" : 1756,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 739,
      "p95" : 1098,
      "p99" : 1228,
      "max" : 1228,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 548,
  "esAppendsPerSecond" : 9.133333333333333,
  "ackedCommandsPerSecond" : 8.083333333333334,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1136,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 0,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 5,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 3,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 9,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 3,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 9,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 25,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 25
    }
  },
  "commandsByOutcome" : {
    "ACK" : 485
  }
}
```

## Timeline

- +7,8 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +60,0 s: placement restored, ready={gateway-africa=true, gateway-europa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +60,1 s: scenario start
- +60,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +121,4 s: load done: 485 requests
- +121,4 s: invariants: converging up to 90 s
- +125,7 s: cleanup start
- +127,5 s: cleanup done

### stack actions

```
2026-10-22T14:10:52.663119900Z docker restart gateway-africa gateway-europa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
```

