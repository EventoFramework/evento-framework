# S00: Baseline, no faults

- Label: `m3-1-eu3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:49:36.266434Z, finished: 2026-10-20T19:51:44.788829Z (128 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m3-1-eu3-75b372-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 482/482 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 482 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3554 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 62 payments for 62 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 482 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3554 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=482 ack=482 failed=0 p50=849ms p95=1515ms p99=1690ms max=1845ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 482,
    "acked" : 482,
    "failed" : 0,
    "p50" : 849,
    "p95" : 1515,
    "p99" : 1690,
    "max" : 1845,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 343,
      "p95" : 464,
      "p99" : 535,
      "max" : 535,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 708,
      "p95" : 1067,
      "p99" : 1219,
      "max" : 1219,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1165,
      "p95" : 1656,
      "p99" : 1845,
      "max" : 1845,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 66,
      "acked" : 66,
      "failed" : 0,
      "p50" : 1060,
      "p95" : 1611,
      "p99" : 1777,
      "max" : 1777,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 177,
      "p95" : 527,
      "p99" : 589,
      "max" : 589,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1171,
      "p95" : 1614,
      "p99" : 1663,
      "max" : 1663,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 66,
      "acked" : 66,
      "failed" : 0,
      "p50" : 646,
      "p95" : 1121,
      "p99" : 1294,
      "max" : 1294,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 543,
  "esAppendsPerSecond" : 9.05,
  "ackedCommandsPerSecond" : 8.033333333333333,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1090,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 11,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 11,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 4,
      "APAC/order-query_OrderProjector_1_INDIA" : 3,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 4,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 3,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 19,
      "EU/order-query_OrderProjector_1_EUROPA" : 26,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 19,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 26
    }
  },
  "commandsByOutcome" : {
    "ACK" : 482
  }
}
```

## Timeline

- +7,9 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +61,2 s: placement restored, ready={gateway-africa=true, gateway-europa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +61,3 s: scenario start
- +61,4 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +122,4 s: load done: 482 requests
- +122,4 s: invariants: converging up to 90 s
- +126,6 s: cleanup start
- +128,5 s: cleanup done

### stack actions

```
2026-10-20T19:49:44.152569500Z docker restart gateway-africa gateway-europa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
```

