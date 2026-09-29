# S05: Concurrent same-aggregate commands from two cells

- Label: `after`
- Gaps exercised: G3, G6, G9
- Started: 2026-09-29T15:37:18.817048Z, finished: 2026-09-29T15:39:09.667084800Z (110 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s05-after-02fa45-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=120 failed=0 p50=933ms p95=1892ms p99=2762ms max=3315ms |
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
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 3650 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=120 failed=0 p50=933ms p95=1892ms p99=2762ms max=3315ms |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 1070,
      "p95" : 2049,
      "p99" : 3315,
      "max" : 3315,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 186,
      "p95" : 1193,
      "p99" : 2762,
      "max" : 2762,
      "kinds" : { }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 120,
    "failed" : 0,
    "p50" : 933,
    "p95" : 1892,
    "p99" : 2762,
    "max" : 3315,
    "kinds" : { }
  },
  "maxAdvisoryLockWaiters" : 1,
  "logMarkers" : {
    "evento-eu" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 308,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 135,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 139,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 135,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 139,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 122,
      "APAC/order-query_OrderProjector_1_CINA" : 124,
      "APAC/order-query_OrderProjector_1_INDIA" : 123,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 122,
      "APAC/order-saga_PaymentSaga_1_CINA" : 124,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 123,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 142,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 142,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    }
  }
}
```

## Timeline

- +7,4 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +66,4 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +66,5 s: scenario start
- +66,7 s: hot orders [s05-after-02fa45-hot-europa-1, s05-after-02fa45-hot-europa-2]
- +66,7 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +104,8 s: contention done: 120 commands
- +105,0 s: invariants: converging up to 120 s
- +109,0 s: cleanup start
- +110,9 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-09-29T15:38:25.720961300Z granted/waiting/lockWaiters=3/1/1
2026-09-29T15:38:26.886405400Z granted/waiting/lockWaiters=1/0/0
2026-09-29T15:38:28.052340900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:29.291417Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:30.491623700Z granted/waiting/lockWaiters=6/1/1
2026-09-29T15:38:31.658704100Z granted/waiting/lockWaiters=6/0/0
2026-09-29T15:38:32.891379800Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:34.052859500Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:35.212855400Z granted/waiting/lockWaiters=5/0/0
2026-09-29T15:38:36.381974900Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:38:37.534425700Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:38.752159600Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:39.913329200Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:41.063256300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:42.254884900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:43.423884100Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:44.594786500Z granted/waiting/lockWaiters=1/0/0
2026-09-29T15:38:45.759995Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:38:46.902090500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:48.075805400Z granted/waiting/lockWaiters=1/0/0
2026-09-29T15:38:49.221244900Z granted/waiting/lockWaiters=5/0/0
2026-09-29T15:38:50.376879400Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:38:51.534129200Z granted/waiting/lockWaiters=5/0/0
2026-09-29T15:38:52.686804800Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:53.835417300Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:38:54.977486600Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:38:56.139674800Z granted/waiting/lockWaiters=6/0/0
2026-09-29T15:38:57.280489600Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:38:58.429947Z granted/waiting/lockWaiters=4/0/0
2026-09-29T15:38:59.577982800Z granted/waiting/lockWaiters=2/0/0
2026-09-29T15:39:00.772272Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:39:01.917740300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T15:39:03.050815Z granted/waiting/lockWaiters=2/0/0
```

### stack actions

```
2026-09-29T15:37:26.241762400Z docker restart gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
```

