# S02: Kill one handler replica inside a cell

- Label: `v3c`
- Gaps exercised: intra-server failover baseline
- Started: 2026-10-22T12:53:45.602604600Z, finished: 2026-10-22T12:55:21.872328600Z (96 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-v3c-2deb82-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA], ratePerMinutePerRegion=60.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=70}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 323 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 2 failed, kinds {}, 0 only after > 25 s |
| I1 holds for acknowledged orders | **AS_EXPECTED** | no acknowledged command lost |  | 0 of 496 acknowledged ops missing; 0 unacknowledged ops were stored anyway |

- _in-flight commands on the killed replica_: A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 496 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 396 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=496 ack=496 failed=0 p50=165ms p95=197ms p99=275ms max=310ms |

## Metrics

```json
{
  "inFlightAtKill" : 2,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 323,
    "acked" : 323,
    "failed" : 0,
    "p50" : 165,
    "p95" : 190,
    "p99" : 233,
    "max" : 310,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 135,
    "acked" : 135,
    "failed" : 0,
    "p50" : 168,
    "p95" : 208,
    "p99" : 292,
    "max" : 303,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 600,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 572,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 579,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 572,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 579,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 590,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 590,
      "APAC/order-saga_PaymentSaga_1_CINA" : 600,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 600,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 1,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 0,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 1
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

- +8,9 s: scenario start
- +9,8 s: order-command-eu-b ready=true
- +9,8 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +30,2 s: killed order-command-eu
- +80,6 s: load done: 496 requests
- +80,7 s: invariants: converging up to 120 s
- +81,6 s: cleanup start
- +96,3 s: cleanup done

### log excerpts: evento-eu

```
20261022125415567 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-22T12:53:54.547109600Z compose --profile replica up -d --no-deps order-command-eu-b
2026-10-22T12:54:15.382702200Z docker kill order-command-eu
2026-10-22T12:55:07.290484700Z docker start order-command-eu (restore)
2026-10-22T12:55:18.694323Z compose rm -sf order-command-eu-b
```

