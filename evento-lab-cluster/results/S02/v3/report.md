# S02: Kill one handler replica inside a cell

- Label: `v3`
- Gaps exercised: intra-server failover baseline
- Started: 2026-10-20T15:06:52.206353900Z, finished: 2026-10-20T15:08:25.521933700Z (93 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-v3-30b7d1-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, durationSeconds=70, regions=[EUROPA, AFRICA], ratePerMinutePerRegion=60.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 319 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 1 failed, kinds {}, 0 only after > 25 s |
| I1 holds for acknowledged orders | **AS_EXPECTED** | no acknowledged command lost |  | 0 of 497 acknowledged ops missing; 0 unacknowledged ops were stored anyway |

- _in-flight commands on the killed replica_: A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 497 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3634 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=497 ack=497 failed=0 p50=123ms p95=264ms p99=288ms max=353ms |

## Metrics

```json
{
  "inFlightAtKill" : 1,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 319,
    "acked" : 319,
    "failed" : 0,
    "p50" : 119,
    "p95" : 264,
    "p99" : 288,
    "max" : 353,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 141,
    "acked" : 141,
    "failed" : 0,
    "p50" : 171,
    "p95" : 259,
    "p99" : 272,
    "max" : 317,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 602,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 574,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 592,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 574,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 592,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 581,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 581,
      "APAC/order-saga_PaymentSaga_1_CINA" : 602,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 602,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 1,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 1,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
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

- +7,8 s: scenario start
- +8,6 s: order-command-eu-b ready=true
- +8,6 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +28,9 s: killed order-command-eu
- +79,2 s: load done: 497 requests
- +79,2 s: invariants: converging up to 120 s
- +83,4 s: cleanup start
- +93,3 s: cleanup done

### log excerpts: evento-eu

```
20261020150720911 INFO  1 --- [nioEventLoopGroup-3-1                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-20T15:06:59.989907700Z compose --profile replica up -d --no-deps order-command-eu-b
2026-10-20T15:07:20.805209700Z docker kill order-command-eu
2026-10-20T15:08:15.631848100Z docker start order-command-eu (restore)
2026-10-20T15:08:22.627930600Z compose rm -sf order-command-eu-b
```

