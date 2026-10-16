# S05: Concurrent same-aggregate commands from two cells

- Label: `after-query`
- Gaps exercised: G3, G6, G9
- Started: 2026-10-15T13:53:51.273764400Z, finished: 2026-10-15T13:54:44.159144700Z (52 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-after-query-d42983-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=973ms p95=1888ms p99=2197ms max=3093ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3586 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=973ms p95=1888ms p99=2197ms max=3093ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1080,
      "p95" : 1995,
      "p99" : 3093,
      "max" : 3093,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 560,
      "p95" : 1271,
      "p99" : 1426,
      "max" : 1426,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 973,
    "p95" : 1888,
    "p99" : 2197,
    "max" : 3093,
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
    },
    "esHead" : 122
  }
}
```

## Timeline

- +8,0 s: scenario start
- +8,5 s: hot orders [s05-after-query-d42983-hot-europa-1, s05-after-query-d42983-hot-europa-2]
- +8,5 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +46,9 s: contention done: 120 commands
- +47,1 s: invariants: converging up to 120 s
- +51,0 s: cleanup start
- +52,9 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-10-15T13:54:00.056606500Z granted/waiting/lockWaiters=5/0/0
2026-10-15T13:54:01.270622300Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:02.451925700Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:03.628410300Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:04.795962300Z granted/waiting/lockWaiters=6/0/0
2026-10-15T13:54:05.959681Z granted/waiting/lockWaiters=5/0/0
2026-10-15T13:54:07.159795800Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:08.309685500Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:09.463151500Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:10.616589600Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:11.800697Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:12.968555600Z granted/waiting/lockWaiters=2/0/0
2026-10-15T13:54:14.134776700Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:15.288669200Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:16.455214600Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:17.636730900Z granted/waiting/lockWaiters=6/0/0
2026-10-15T13:54:18.793459Z granted/waiting/lockWaiters=2/0/0
2026-10-15T13:54:19.941984900Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:21.099651100Z granted/waiting/lockWaiters=2/0/0
2026-10-15T13:54:22.258762300Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:23.401051900Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:24.552761600Z granted/waiting/lockWaiters=3/1/1
2026-10-15T13:54:25.699947Z granted/waiting/lockWaiters=2/0/0
2026-10-15T13:54:26.882830600Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:28.044022600Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:29.197521400Z granted/waiting/lockWaiters=1/0/0
2026-10-15T13:54:30.380393600Z granted/waiting/lockWaiters=4/0/0
2026-10-15T13:54:31.543141100Z granted/waiting/lockWaiters=5/0/0
2026-10-15T13:54:32.732509Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:33.892359700Z granted/waiting/lockWaiters=5/1/1
2026-10-15T13:54:35.051884600Z granted/waiting/lockWaiters=3/0/0
2026-10-15T13:54:36.195134200Z granted/waiting/lockWaiters=2/0/0
2026-10-15T13:54:37.371799400Z granted/waiting/lockWaiters=2/0/0
```

### stack actions

```
```

