# S02: Kill one handler replica inside a cell

- Label: `before`
- Gaps exercised: intra-server failover baseline
- Started: 2026-09-29T12:44:29.370773Z, finished: 2026-09-29T12:46:08.110250100Z (98 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-before-2929da-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[EUROPA, AFRICA], durationSeconds=70, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=60.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 316 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 1 failed, kinds {}, 0 only after > 25 s |
| I1 holds for acknowledged orders | **AS_EXPECTED** | no acknowledged command lost |  | 0 of 487 acknowledged ops missing; 0 unacknowledged ops were stored anyway |

- _in-flight commands on the killed replica_: A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 487 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3825 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=487 ack=487 failed=0 p50=167ms p95=268ms p99=296ms max=348ms |

## Metrics

```json
{
  "inFlightAtKill" : 1,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 316,
    "acked" : 316,
    "failed" : 0,
    "p50" : 166,
    "p95" : 268,
    "p99" : 296,
    "max" : 348,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 134,
    "acked" : 134,
    "failed" : 0,
    "p50" : 163,
    "p95" : 275,
    "p99" : 294,
    "max" : 300,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 604,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 554,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 571,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 554,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 571,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 560,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 560,
      "APAC/order-saga_PaymentSaga_1_CINA" : 604,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 604,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 2,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 0,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 2
    }
  },
  "logMarkers" : {
    "evento-eu" : { },
    "order-command-eu-b" : { }
  }
}
```

## Timeline

- +6,1 s: scenario start
- +13,7 s: order-command-eu-b ready=true
- +13,7 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +34,1 s: killed order-command-eu
- +84,5 s: load done: 487 requests
- +84,5 s: invariants: converging up to 120 s
- +88,9 s: cleanup start
- +98,7 s: cleanup done

### stack actions

```
2026-09-29T12:44:35.489927200Z compose --profile replica up -d --no-deps order-command-eu-b
2026-09-29T12:45:03.101393Z docker kill order-command-eu
2026-09-29T12:45:58.384151200Z docker start order-command-eu (restore)
2026-09-29T12:46:05.249020100Z compose rm -sf order-command-eu-b
```

