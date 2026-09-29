# S02: Kill one handler replica inside a cell

- Label: `after-g5n16`
- Gaps exercised: intra-server failover baseline
- Started: 2026-09-29T21:15:11.602877600Z, finished: 2026-09-29T21:16:51.939087200Z (100 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-after-g5n16-dd9116-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=60.0, regions=[EUROPA, AFRICA], durationSeconds=70, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 315 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 2 failed, kinds {}, 0 only after > 25 s |
| I1 holds for acknowledged orders | **AS_EXPECTED** | no acknowledged command lost |  | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |

- _in-flight commands on the killed replica_: A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 490 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3661 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=490 ack=490 failed=0 p50=174ms p95=266ms p99=281ms max=439ms |

## Metrics

```json
{
  "inFlightAtKill" : 2,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 315,
    "acked" : 315,
    "failed" : 0,
    "p50" : 174,
    "p95" : 264,
    "p99" : 274,
    "max" : 279,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 139,
    "acked" : 139,
    "failed" : 0,
    "p50" : 175,
    "p95" : 276,
    "p99" : 414,
    "max" : 439,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-saga_PaymentSaga_1_AMERICA" : 560,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 560,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 560,
      "APAC/order-saga_PaymentSaga_1_CINA" : 560,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 560,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 1,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 0,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 1
    },
    "esHead" : 560
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
- +15,6 s: order-command-eu-b ready=true
- +15,6 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +36,0 s: killed order-command-eu
- +86,2 s: load done: 490 requests
- +86,2 s: invariants: converging up to 120 s
- +90,5 s: cleanup start
- +100,3 s: cleanup done

### log excerpts: evento-eu

```
20260929211547372 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-09-29T21:15:19.422529400Z compose --profile replica up -d --no-deps order-command-eu-b
2026-09-29T21:15:47.222791800Z docker kill order-command-eu
2026-09-29T21:16:42.122482800Z docker start order-command-eu (restore)
2026-09-29T21:16:49.042882700Z compose rm -sf order-command-eu-b
```

