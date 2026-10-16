# S02: Kill one handler replica inside a cell

- Label: `after-versioning`
- Gaps exercised: intra-server failover baseline
- Started: 2026-10-16T00:08:07.866913700Z, finished: 2026-10-16T00:09:48.669980600Z (100 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s02-after-versioning-a1924b-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=2, durationSeconds=70, regions=[EUROPA, AFRICA], ratePerMinutePerRegion=60.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| new commands go to the surviving replica | **AS_EXPECTED** | commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes) |  | 100,0% of 315 acknowledged; failures {} |
| in-flight commands on the killed replica | **AS_EXPECTED** | fail promptly with 'peer disconnected' (the outcome is indeterminate) |  | 0 of 0 failed, kinds {}, 0 only after > 25 s |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3588 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=487 ack=487 failed=0 p50=104ms p95=177ms p99=188ms max=280ms |

## Metrics

```json
{
  "inFlightAtKill" : 0,
  "inFlightFailedKinds" : { },
  "latencyAfterKill" : {
    "count" : 315,
    "acked" : 315,
    "failed" : 0,
    "p50" : 103,
    "p95" : 176,
    "p99" : 184,
    "max" : 192,
    "kinds" : { }
  },
  "latencyBeforeKill" : {
    "count" : 140,
    "acked" : 140,
    "failed" : 0,
    "p50" : 107,
    "p95" : 180,
    "p99" : 237,
    "max" : 280,
    "kinds" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 582,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 554,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 572,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 554,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 572,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 561,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 561,
      "APAC/order-saga_PaymentSaga_1_CINA" : 582,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 582,
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

- +8,3 s: scenario start
- +16,1 s: order-command-eu-b ready=true
- +16,1 s: load start [EUROPA, AFRICA] @60.0/min for 70 s
- +36,5 s: killed order-command-eu
- +86,8 s: load done: 487 requests
- +86,9 s: invariants: converging up to 120 s
- +90,9 s: cleanup start
- +100,8 s: cleanup done

### log excerpts: evento-eu

```
20261016000844133 INFO  1 --- [nioEventLoopGroup-3-16                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-16T00:08:16.221765300Z compose --profile replica up -d --no-deps order-command-eu-b
2026-10-16T00:08:44.023030700Z docker kill order-command-eu
2026-10-16T00:09:38.792724500Z docker start order-command-eu (restore)
2026-10-16T00:09:45.778661600Z compose rm -sf order-command-eu-b
```

