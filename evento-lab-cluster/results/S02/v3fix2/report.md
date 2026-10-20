# S02: Kill one handler replica inside a cell

- Label: `v3fix2`
- Gaps exercised: intra-server failover baseline
- Started: 2026-10-20T18:21:21.153951300Z, finished: 2026-10-20T18:22:54.212506200Z (93 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-v3fix2-c37545-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=60.0, regions=[EUROPA, AFRICA], durationSeconds=70, itemsPerOrder=2}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 312 acknowledged; failures {} |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3630 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=487 ack=487 failed=0 p50=167ms p95=253ms p99=280ms max=342ms |

## Metrics

```json
{
  "inFlightAtKill" : 1,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 312,
    "acked" : 312,
    "failed" : 0,
    "p50" : 169,
    "p95" : 251,
    "p99" : 276,
    "max" : 284,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 138,
    "acked" : 138,
    "failed" : 0,
    "p50" : 145,
    "p95" : 254,
    "p99" : 279,
    "max" : 280,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 563,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 558,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 563,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 558,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 554,
      "APAC/order-query_OrderProjector_1_CINA" : 555,
      "APAC/order-query_OrderProjector_1_INDIA" : 556,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 554,
      "APAC/order-saga_PaymentSaga_1_CINA" : 555,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 556,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 2,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 2,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 871
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

- +7,6 s: scenario start
- +8,4 s: order-command-eu-b ready=true
- +8,4 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +28,8 s: killed order-command-eu
- +79,0 s: load done: 487 requests
- +79,0 s: invariants: converging up to 120 s
- +83,1 s: cleanup start
- +93,1 s: cleanup done

### log excerpts: evento-eu

```
20261020182149720 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-20T18:21:28.780657400Z compose --profile replica up -d --no-deps order-command-eu-b
2026-10-20T18:21:49.611793100Z docker kill order-command-eu
2026-10-20T18:22:44.340919800Z docker start order-command-eu (restore)
2026-10-20T18:22:51.260441Z compose rm -sf order-command-eu-b
```

