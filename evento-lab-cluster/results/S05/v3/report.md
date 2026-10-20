# S05: Concurrent same-aggregate commands from two cells

- Label: `v3`
- Gaps exercised: G3, G6, G9
- Started: 2026-10-20T15:20:48.586126700Z, finished: 2026-10-20T15:21:39.409987500Z (50 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-v3-bdec08-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=763ms p95=2122ms p99=2602ms max=3731ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3613 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=763ms p95=2122ms p99=2602ms max=3731ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1145,
      "p95" : 2245,
      "p99" : 3731,
      "max" : 3731,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 209,
      "p95" : 1260,
      "p99" : 1412,
      "max" : 1412,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 763,
    "p95" : 2122,
    "p99" : 2602,
    "max" : 3731,
    "kinds" : { }
  },
  "maxAdvisoryLockWaiters" : 2,
  "logMarkers" : {
    "evento-eu" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-saga_PaymentSaga_1_AMERICA" : 137,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 137,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 137,
      "APAC/order-saga_PaymentSaga_1_CINA" : 137,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 137,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 137,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 137
  }
}
```

## Timeline

- +7,8 s: scenario start
- +8,4 s: hot orders [s05-v3-bdec08-hot-europa-1, s05-v3-bdec08-hot-europa-2]
- +8,4 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +44,9 s: contention done: 120 commands
- +45,0 s: invariants: converging up to 120 s
- +48,9 s: cleanup start
- +50,8 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-10-20T15:20:57.305172900Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:20:58.514792800Z granted/waiting/lockWaiters=4/2/2
2026-10-20T15:20:59.676484400Z granted/waiting/lockWaiters=2/1/1
2026-10-20T15:21:00.830236700Z granted/waiting/lockWaiters=4/1/1
2026-10-20T15:21:01.997791500Z granted/waiting/lockWaiters=5/1/1
2026-10-20T15:21:03.186301600Z granted/waiting/lockWaiters=6/1/1
2026-10-20T15:21:04.344586Z granted/waiting/lockWaiters=4/1/1
2026-10-20T15:21:05.505094900Z granted/waiting/lockWaiters=4/1/1
2026-10-20T15:21:06.693557900Z granted/waiting/lockWaiters=3/1/1
2026-10-20T15:21:07.887627800Z granted/waiting/lockWaiters=4/1/1
2026-10-20T15:21:09.059058800Z granted/waiting/lockWaiters=4/1/1
2026-10-20T15:21:10.219613300Z granted/waiting/lockWaiters=3/1/1
2026-10-20T15:21:11.387527Z granted/waiting/lockWaiters=3/1/1
2026-10-20T15:21:12.567113400Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:21:13.724826200Z granted/waiting/lockWaiters=1/0/0
2026-10-20T15:21:14.872175700Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:21:16.036322200Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:17.205122100Z granted/waiting/lockWaiters=2/0/0
2026-10-20T15:21:18.364364600Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:21:19.517678600Z granted/waiting/lockWaiters=2/0/0
2026-10-20T15:21:20.671493600Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:21.837367300Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:22.989478600Z granted/waiting/lockWaiters=2/0/0
2026-10-20T15:21:24.157591300Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:25.315747100Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:21:26.487701800Z granted/waiting/lockWaiters=4/0/0
2026-10-20T15:21:27.660985400Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:28.819227600Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:29.982740600Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:31.123060200Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:32.336840900Z granted/waiting/lockWaiters=3/0/0
2026-10-20T15:21:33.440807900Z granted/waiting/lockWaiters=
```

### stack actions

```
```

