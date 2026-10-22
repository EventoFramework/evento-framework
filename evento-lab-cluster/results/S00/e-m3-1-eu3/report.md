# S00: Baseline, no faults

- Label: `e-m3-1-eu3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T17:02:17.609826400Z, finished: 2026-10-22T17:04:28.221044200Z (130 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-e-m3-1-eu3-7f764a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 480/480 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 480 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3432 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 60 payments for 60 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 480 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3432 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=480 ack=480 failed=0 p50=832ms p95=1480ms p99=1682ms max=1797ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 480,
    "acked" : 480,
    "failed" : 0,
    "p50" : 832,
    "p95" : 1480,
    "p99" : 1682,
    "max" : 1797,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 190,
      "p95" : 430,
      "p99" : 530,
      "max" : 530,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 66,
      "acked" : 66,
      "failed" : 0,
      "p50" : 665,
      "p95" : 1076,
      "p99" : 1129,
      "max" : 1129,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 1048,
      "p95" : 1579,
      "p99" : 1797,
      "max" : 1797,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 1098,
      "p95" : 1544,
      "p99" : 1718,
      "max" : 1718,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 171,
      "p95" : 334,
      "p99" : 443,
      "max" : 443,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 1161,
      "p95" : 1555,
      "p99" : 1705,
      "max" : 1705,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 656,
      "p95" : 1037,
      "p99" : 1107,
      "max" : 1107,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 538,
  "esAppendsPerSecond" : 8.966666666666667,
  "ackedCommandsPerSecond" : 8.0,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 11,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 11,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 8,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 8,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 25,
      "EU/order-query_OrderProjector_1_EUROPA" : 20,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 25,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 20
    },
    "esHead" : 1092
  },
  "commandsByOutcome" : {
    "ACK" : 480
  }
}
```

## Timeline

- +7,8 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, gateway-europa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +62,3 s: placement restored, ready={gateway-africa=true, gateway-europa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +62,3 s: scenario start
- +62,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +124,6 s: load done: 480 requests
- +124,6 s: invariants: converging up to 90 s
- +128,6 s: cleanup start
- +130,6 s: cleanup done

### stack actions

```
2026-10-22T17:02:25.389965600Z docker restart gateway-africa gateway-europa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
```

