# S02: Kill one handler replica inside a cell

- Label: `after`
- Gaps exercised: intra-server failover baseline
- Started: 2026-09-29T14:16:43.836470200Z, finished: 2026-09-29T14:18:22.559323300Z (98 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-after-fbbbf1-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[EUROPA, AFRICA], durationSeconds=70, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=60.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 313 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 2 failed, kinds {}, 0 only after > 25 s |
| I1 holds for acknowledged orders | **AS_EXPECTED** | no acknowledged command lost |  | 0 of 486 acknowledged ops missing; 0 unacknowledged ops were stored anyway |

- _in-flight commands on the killed replica_: A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 486 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3711 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=486 ack=486 failed=0 p50=175ms p95=266ms p99=288ms max=352ms |

## Metrics

```json
{
  "inFlightAtKill" : 2,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 313,
    "acked" : 313,
    "failed" : 0,
    "p50" : 176,
    "p95" : 265,
    "p99" : 283,
    "max" : 288,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 135,
    "acked" : 135,
    "failed" : 0,
    "p50" : 174,
    "p95" : 272,
    "p99" : 291,
    "max" : 297,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1170,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 552,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 559,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 552,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 559,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 570,
      "APAC/order-query_OrderProjector_1_CINA" : 596,
      "APAC/order-query_OrderProjector_1_INDIA" : 594,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 570,
      "APAC/order-saga_PaymentSaga_1_CINA" : 596,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 594,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 2,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 2,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0,
      "consumers/order-query_CatalogProjector_1_ALL" : 580
    }
  },
  "logMarkers" : {
    "evento-eu" : {
      "Skipping unregister of bundle" : 1
    },
    "order-command-eu-b" : { }
  }
}
```

## Timeline

- +6,1 s: scenario start
- +13,7 s: order-command-eu-b ready=true
- +13,7 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +34,0 s: killed order-command-eu
- +84,6 s: load done: 486 requests
- +84,6 s: invariants: converging up to 120 s
- +88,8 s: cleanup start
- +98,7 s: cleanup done

### log excerpts: evento-eu

```
20260929141717656 INFO  1 --- [nioEventLoopGroup-3-11                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-09-29T14:16:49.895799500Z compose --profile replica up -d --no-deps order-command-eu-b
2026-09-29T14:17:17.543556800Z docker kill order-command-eu
2026-09-29T14:18:12.747445900Z docker start order-command-eu (restore)
2026-09-29T14:18:19.709495900Z compose rm -sf order-command-eu-b
```

