# S00: Baseline, no faults

- Label: `m3-2-servers3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-20T19:53:01.794395800Z, finished: 2026-10-20T19:55:18.034878Z (136 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-m3-2-servers3-35048a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-america=evento-eu, gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 493/493 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 493 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 3403 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 73 payments for 73 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 493 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3403 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=493 ack=493 failed=0 p50=534ms p95=1031ms p99=1334ms max=1589ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 493,
    "acked" : 493,
    "failed" : 0,
    "p50" : 534,
    "p95" : 1031,
    "p99" : 1334,
    "max" : 1589,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 70,
      "acked" : 70,
      "failed" : 0,
      "p50" : 107,
      "p95" : 195,
      "p99" : 440,
      "max" : 440,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 523,
      "p95" : 778,
      "p99" : 916,
      "max" : 916,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 714,
      "p95" : 1181,
      "p99" : 1584,
      "max" : 1584,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 783,
      "p95" : 1204,
      "p99" : 1361,
      "max" : 1361,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 322,
      "p95" : 541,
      "p99" : 715,
      "max" : 715,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 772,
      "p95" : 993,
      "p99" : 1589,
      "max" : 1589,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 71,
      "acked" : 71,
      "failed" : 0,
      "p50" : 533,
      "p95" : 790,
      "p99" : 815,
      "max" : 815,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 566,
  "esAppendsPerSecond" : 9.433333333333334,
  "ackedCommandsPerSecond" : 8.216666666666667,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 10,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 10,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 4,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 4,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 13,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 13,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 1656
  },
  "commandsByOutcome" : {
    "ACK" : 493
  }
}
```

## Timeline

- +8,0 s: placement: 9 JVM(s) on a remote server, restarting them: {gateway-america=evento-eu, gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +69,0 s: placement restored, ready={gateway-america=true, gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +69,1 s: scenario start
- +69,2 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +130,3 s: load done: 493 requests
- +130,3 s: invariants: converging up to 90 s
- +134,4 s: cleanup start
- +136,2 s: cleanup done

### stack actions

```
2026-10-20T19:53:09.827158200Z docker restart gateway-america gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
```

