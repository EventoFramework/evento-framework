# S05: Concurrent same-aggregate commands from two cells

- Label: `after-g5n16`
- Gaps exercised: G3, G6, G9
- Started: 2026-09-29T21:21:19.940084900Z, finished: 2026-09-29T21:22:19.038436Z (59 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-after-g5n16-95ef3a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=1159ms p95=1870ms p99=3011ms max=4279ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3568 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=1159ms p95=1870ms p99=3011ms max=4279ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1317,
      "p95" : 2029,
      "p99" : 4279,
      "max" : 4279,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 212,
      "p95" : 1395,
      "p99" : 1838,
      "max" : 1838,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 1159,
    "p95" : 1870,
    "p99" : 3011,
    "max" : 4279,
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

- +7,7 s: scenario start
- +8,2 s: hot orders [s05-after-g5n16-95ef3a-hot-europa-1, s05-after-g5n16-95ef3a-hot-europa-2]
- +8,2 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +53,2 s: contention done: 120 commands
- +53,4 s: invariants: converging up to 120 s
- +57,3 s: cleanup start
- +59,1 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-09-29T21:21:28.348092900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:29.557640200Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:21:30.733847100Z granted/waiting/lockWaiters=5/0/0
2026-09-29T21:21:31.903130900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:33.069182100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:34.255040500Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:21:35.407470500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:36.564695Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:37.719826500Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:38.887410700Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:40.073749500Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:41.214871Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:42.406861300Z granted/waiting/lockWaiters=6/0/0
2026-09-29T21:21:43.617579400Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:44.787367400Z granted/waiting/lockWaiters=6/1/1
2026-09-29T21:21:45.936303200Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:47.087127900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:48.230208800Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:49.375822700Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:50.570228500Z granted/waiting/lockWaiters=1/0/0
2026-09-29T21:21:51.736202600Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:52.911782200Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:54.067439700Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:55.219353800Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:56.362465300Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:57.539947800Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:21:58.701769700Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:21:59.887572300Z granted/waiting/lockWaiters=1/0/0
2026-09-29T21:22:01.091678900Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:22:02.248110200Z granted/waiting/lockWaiters=1/0/0
2026-09-29T21:22:03.446447800Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:22:04.610403Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:22:05.763914800Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:22:06.923942900Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:22:08.061700700Z granted/waiting/lockWaiters=2/0/0
2026-09-29T21:22:09.213489600Z granted/waiting/lockWaiters=3/0/0
2026-09-29T21:22:10.366111300Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:22:11.534848200Z granted/waiting/lockWaiters=4/0/0
2026-09-29T21:22:12.714006Z granted/waiting/lockWaiters=3/0/0
```

### stack actions

```
```

