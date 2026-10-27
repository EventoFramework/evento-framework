# S00: Baseline, no faults

- Label: `c-m3-2-servers3`
- Gaps exercised: G9 (reference)
- Started: 2026-10-22T14:14:13.633337300Z, finished: 2026-10-22T14:16:25.981478700Z (132 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-c-m3-2-servers3-ca6f4b-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, gateway-sud-america=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=20.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 484/484 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 484 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 225 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 64 payments for 64 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 484 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 225 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=484 ack=484 failed=0 p50=433ms p95=900ms p99=1037ms max=1369ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 484,
    "acked" : 484,
    "failed" : 0,
    "p50" : 433,
    "p95" : 900,
    "p99" : 1037,
    "max" : 1369,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 175,
      "p95" : 346,
      "p99" : 364,
      "max" : 364,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 434,
      "p95" : 591,
      "p99" : 700,
      "max" : 700,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 607,
      "p95" : 928,
      "p99" : 1054,
      "max" : 1054,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 617,
      "p95" : 949,
      "p99" : 1369,
      "max" : 1369,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 73,
      "acked" : 73,
      "failed" : 0,
      "p50" : 175,
      "p95" : 271,
      "p99" : 356,
      "max" : 356,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 72,
      "acked" : 72,
      "failed" : 0,
      "p50" : 522,
      "p95" : 987,
      "p99" : 1169,
      "max" : 1169,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 69,
      "acked" : 69,
      "failed" : 0,
      "p50" : 446,
      "p95" : 710,
      "p99" : 888,
      "max" : 888,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 547,
  "esAppendsPerSecond" : 9.116666666666667,
  "ackedCommandsPerSecond" : 8.066666666666666,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 3,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 6,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 1,
      "EU/order-query_OrderProjector_1_*" : 1,
      "EU/order-query_OrderProjector_1_AFRICA" : 19,
      "EU/order-query_OrderProjector_1_EUROPA" : 17,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 19,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 17
    },
    "esHead" : 1684
  },
  "commandsByOutcome" : {
    "ACK" : 484
  }
}
```

## Timeline

- +7,8 s: placement: 9 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, gateway-sud-america=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +68,1 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, gateway-sud-america=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +68,2 s: scenario start
- +68,4 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +129,4 s: load done: 484 requests
- +129,4 s: invariants: converging up to 90 s
- +130,4 s: cleanup start
- +132,3 s: cleanup done

### stack actions

```
2026-10-22T14:14:21.477508700Z docker restart gateway-australia-nz gateway-cina gateway-india gateway-sud-america order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
```

