# S05: Concurrent same-aggregate commands from two cells

- Label: `v3e`
- Gaps exercised: G3, G6, G9
- Started: 2026-10-22T16:05:10.286614Z, finished: 2026-10-22T16:05:56.156665700Z (45 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-v3e-0ef95c-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=414ms p95=1918ms p99=3720ms max=6066ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 398 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=414ms p95=1918ms p99=3720ms max=6066ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1111,
      "p95" : 2252,
      "p99" : 6066,
      "max" : 6066,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 229,
      "p95" : 1023,
      "p99" : 1256,
      "max" : 1256,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 414,
    "p95" : 1918,
    "p99" : 3720,
    "max" : 6066,
    "kinds" : { }
  },
  "maxAdvisoryLockWaiters" : 1,
  "logMarkers" : {
    "evento-eu" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-saga_PaymentSaga_1_AMERICA" : 136,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 136,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 136,
      "APAC/order-saga_PaymentSaga_1_CINA" : 136,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 136,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 136,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 136
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,5 s: hot orders [s05-v3e-0ef95c-hot-europa-1, s05-v3e-0ef95c-hot-europa-2]
- +8,5 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +43,1 s: contention done: 120 commands
- +43,3 s: invariants: converging up to 120 s
- +44,1 s: cleanup start
- +45,9 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-10-22T16:05:19.088098600Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:20.290297100Z granted/waiting/lockWaiters=5/1/1
2026-10-22T16:05:21.619671500Z granted/waiting/lockWaiters=6/1/1
2026-10-22T16:05:22.857263600Z granted/waiting/lockWaiters=7/0/0
2026-10-22T16:05:24.049724400Z granted/waiting/lockWaiters=7/1/1
2026-10-22T16:05:25.273762800Z granted/waiting/lockWaiters=8/1/1
2026-10-22T16:05:26.436402Z granted/waiting/lockWaiters=8/1/1
2026-10-22T16:05:27.594422400Z granted/waiting/lockWaiters=7/0/0
2026-10-22T16:05:28.785080200Z granted/waiting/lockWaiters=6/1/1
2026-10-22T16:05:29.953078Z granted/waiting/lockWaiters=7/0/0
2026-10-22T16:05:31.201731200Z granted/waiting/lockWaiters=8/1/1
2026-10-22T16:05:32.359887200Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:33.533446600Z granted/waiting/lockWaiters=3/0/0
2026-10-22T16:05:34.702765Z granted/waiting/lockWaiters=6/0/0
2026-10-22T16:05:35.896601500Z granted/waiting/lockWaiters=4/0/0
2026-10-22T16:05:37.040389800Z granted/waiting/lockWaiters=4/0/0
2026-10-22T16:05:38.218112300Z granted/waiting/lockWaiters=3/0/0
2026-10-22T16:05:39.382693600Z granted/waiting/lockWaiters=6/0/0
2026-10-22T16:05:40.535173300Z granted/waiting/lockWaiters=3/0/0
2026-10-22T16:05:41.699766900Z granted/waiting/lockWaiters=2/0/0
2026-10-22T16:05:42.852445400Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:44.016462100Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:45.173600Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:46.342393500Z granted/waiting/lockWaiters=4/0/0
2026-10-22T16:05:47.521691Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:48.688732200Z granted/waiting/lockWaiters=4/0/0
2026-10-22T16:05:49.849890600Z granted/waiting/lockWaiters=2/0/0
2026-10-22T16:05:51.070879400Z granted/waiting/lockWaiters=5/0/0
2026-10-22T16:05:52.231139400Z granted/waiting/lockWaiters=4/0/0
2026-10-22T16:05:53.385992900Z granted/waiting/lockWaiters=
```

### stack actions

```
```

