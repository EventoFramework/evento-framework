# S05: Concurrent same-aggregate commands from two cells

- Label: `after-versioning`
- Gaps exercised: G3, G6, G9
- Started: 2026-10-16T00:22:01.188782900Z, finished: 2026-10-16T00:22:52.444688500Z (51 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-after-versioning-639320-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=932ms p95=1787ms p99=2197ms max=2968ms |
| cross-server advisory-lock deadlock | **AS_EXPECTED** | no deadlock: each lock waiter holds nothing else | all threads of a server share one pinned lock session (G3), so two servers can form a wait cycle on unrelated keys | 0 'deadlock detected' on the servers; failed commands {} |
| no aggregate fork (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | a lost advisory lock lets two servers append from one story (G3 + G6) | 0 of 2 aggregates invalid |
| items stored = items acknowledged (I1, I2) | **AS_EXPECTED** | no loss, no duplicate |  | 0 of 120 acknowledged ops missing; 0 unacknowledged ops were stored anyway \| no duplicates |

- _cross-server advisory-lock deadlock_: Postgres breaks the cycle after deadlock_timeout (1 s) by failing one pg_advisory_lock: the command fails fast instead of stalling.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 2 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 2 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3546 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=932ms p95=1787ms p99=2197ms max=2968ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1028,
      "p95" : 1792,
      "p99" : 2968,
      "max" : 2968,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 363,
      "p95" : 1467,
      "p99" : 1872,
      "max" : 1872,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 932,
    "p95" : 1787,
    "p99" : 2197,
    "max" : 2968,
    "kinds" : { }
  },
  "maxAdvisoryLockWaiters" : 1,
  "logMarkers" : {
    "evento-eu" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 122,
    "lag" : {
      "AM/order-saga_PaymentSaga_1_AMERICA" : 122,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 122,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 122,
      "APAC/order-saga_PaymentSaga_1_CINA" : 122,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 122,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 122,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    }
  }
}
```

## Timeline

- +7,9 s: scenario start
- +8,4 s: hot orders [s05-after-versioning-639320-hot-europa-1, s05-after-versioning-639320-hot-europa-2]
- +8,4 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +45,4 s: contention done: 120 commands
- +45,5 s: invariants: converging up to 120 s
- +49,4 s: cleanup start
- +51,3 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-10-16T00:22:09.770164800Z granted/waiting/lockWaiters=5/0/0
2026-10-16T00:22:10.983877300Z granted/waiting/lockWaiters=2/0/0
2026-10-16T00:22:12.175276300Z granted/waiting/lockWaiters=4/0/0
2026-10-16T00:22:13.328343700Z granted/waiting/lockWaiters=5/0/0
2026-10-16T00:22:14.499967700Z granted/waiting/lockWaiters=1/0/0
2026-10-16T00:22:15.654985Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:16.860341Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:18.026299Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:19.184151800Z granted/waiting/lockWaiters=4/0/0
2026-10-16T00:22:20.433425800Z granted/waiting/lockWaiters=5/0/0
2026-10-16T00:22:21.617548600Z granted/waiting/lockWaiters=5/1/1
2026-10-16T00:22:22.805073400Z granted/waiting/lockWaiters=6/1/1
2026-10-16T00:22:24.017827600Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:25.189622700Z granted/waiting/lockWaiters=6/0/0
2026-10-16T00:22:26.390924Z granted/waiting/lockWaiters=6/0/0
2026-10-16T00:22:27.557958700Z granted/waiting/lockWaiters=5/0/0
2026-10-16T00:22:28.716183800Z granted/waiting/lockWaiters=1/0/0
2026-10-16T00:22:29.869585600Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:31.017356600Z granted/waiting/lockWaiters=2/0/0
2026-10-16T00:22:32.169014400Z granted/waiting/lockWaiters=2/0/0
2026-10-16T00:22:33.319263700Z granted/waiting/lockWaiters=6/0/0
2026-10-16T00:22:34.485158700Z granted/waiting/lockWaiters=5/0/0
2026-10-16T00:22:35.755845200Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:36.922522700Z granted/waiting/lockWaiters=1/0/0
2026-10-16T00:22:38.068200500Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:39.204065800Z granted/waiting/lockWaiters=3/0/0
2026-10-16T00:22:40.359485200Z granted/waiting/lockWaiters=4/0/0
2026-10-16T00:22:41.534191400Z granted/waiting/lockWaiters=2/0/0
2026-10-16T00:22:42.698514500Z granted/waiting/lockWaiters=2/0/0
2026-10-16T00:22:43.848142400Z granted/waiting/lockWaiters=4/0/0
2026-10-16T00:22:45.023947800Z granted/waiting/lockWaiters=4/0/0
2026-10-16T00:22:46.175867400Z granted/waiting/lockWaiters=2/0/0
```

### stack actions

```
```

