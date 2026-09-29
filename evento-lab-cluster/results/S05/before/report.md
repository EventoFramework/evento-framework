# S05: Concurrent same-aggregate commands from two cells

- Label: `before`
- Gaps exercised: G3, G6, G9
- Started: 2026-09-29T13:12:37.152081500Z, finished: 2026-09-29T13:14:16.185560800Z (99 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s05-before-5accdd-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=112 failed=8 p50=2288ms p95=3413ms p99=4761ms max=4768ms failures={error=8} |
| cross-server advisory-lock deadlock | **KNOWN_DEFECT_REPRODUCED** | no deadlock: each lock waiter holds nothing else | all threads of a server share one pinned lock session (G3), so two servers can form a wait cycle on unrelated keys | 8 'deadlock detected' on the servers; failed commands {error: Failed to acquire advisory lock for key: RESOURCE:<id>=8} |
| no aggregate fork (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | a lost advisory lock lets two servers append from one story (G3 + G6) | 0 of 2 aggregates invalid |
| items stored = items acknowledged (I1, I2) | **AS_EXPECTED** | no loss, no duplicate |  | 0 of 112 acknowledged ops missing; 0 unacknowledged ops were stored anyway \| no duplicates |

- _cross-server advisory-lock deadlock_: Postgres breaks the cycle after deadlock_timeout (1 s) by failing one pg_advisory_lock: the command fails fast instead of stalling.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 112 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 2 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 2 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 379 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=112 failed=8 p50=2288ms p95=3413ms p99=4761ms max=4768ms failures={error=8} |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 59,
      "failed" : 1,
      "p50" : 2930,
      "p95" : 3460,
      "p99" : 4563,
      "max" : 4563,
      "kinds" : {
        "error" : 1
      }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 53,
      "failed" : 7,
      "p50" : 1016,
      "p95" : 2751,
      "p99" : 4768,
      "max" : 4768,
      "kinds" : {
        "error" : 7
      }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 112,
    "failed" : 8,
    "p50" : 2288,
    "p95" : 3413,
    "p99" : 4761,
    "max" : 4768,
    "kinds" : {
      "error" : 8
    }
  },
  "maxAdvisoryLockWaiters" : 2,
  "logMarkers" : {
    "evento-eu" : {
      "deadlock detected" : 7,
      "Failed to acquire advisory lock" : 7
    },
    "evento-apac" : {
      "deadlock detected" : 1,
      "Failed to acquire advisory lock" : 1
    }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 8
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-saga_PaymentSaga_1_AMERICA" : 114,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 114,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 114,
      "APAC/order-saga_PaymentSaga_1_CINA" : 114,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 114,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 114,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 114
  }
}
```

## Timeline

- +5,9 s: scenario start
- +6,5 s: hot orders [s05-before-5accdd-hot-europa-1, s05-before-5accdd-hot-europa-2]
- +6,6 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +96,3 s: contention done: 120 commands
- +96,5 s: invariants: converging up to 120 s
- +97,2 s: cleanup start
- +99,0 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-09-29T13:12:43.899233100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:12:45.125850300Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:46.297508500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:12:47.475808600Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:48.735290300Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:49.916144900Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:51.088767900Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:52.226576Z granted/waiting/lockWaiters=4/2/2
2026-09-29T13:12:53.417433400Z granted/waiting/lockWaiters=3/2/2
2026-09-29T13:12:54.605098400Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:12:55.762991900Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:12:56.912101200Z granted/waiting/lockWaiters=6/2/2
2026-09-29T13:12:58.062814700Z granted/waiting/lockWaiters=3/1/1
2026-09-29T13:12:59.249839400Z granted/waiting/lockWaiters=4/1/1
2026-09-29T13:13:00.406914600Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:13:01.571757700Z granted/waiting/lockWaiters=6/2/2
2026-09-29T13:13:02.721044600Z granted/waiting/lockWaiters=5/2/2
2026-09-29T13:13:03.885835200Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:13:05.046872100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:06.195592Z granted/waiting/lockWaiters=5/1/1
2026-09-29T13:13:07.361901900Z granted/waiting/lockWaiters=3/1/1
2026-09-29T13:13:08.512603100Z granted/waiting/lockWaiters=2/1/1
2026-09-29T13:13:09.726605200Z granted/waiting/lockWaiters=4/2/2
2026-09-29T13:13:10.883544600Z granted/waiting/lockWaiters=4/1/1
2026-09-29T13:13:12.053324900Z granted/waiting/lockWaiters=4/2/2
2026-09-29T13:13:13.222674700Z granted/waiting/lockWaiters=3/1/1
2026-09-29T13:13:14.364634500Z granted/waiting/lockWaiters=4/1/1
2026-09-29T13:13:15.517521500Z granted/waiting/lockWaiters=4/1/1
2026-09-29T13:13:16.651422700Z granted/waiting/lockWaiters=4/1/1
2026-09-29T13:13:17.816921100Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:18.998129Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:20.195301500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:21.370757900Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:22.531128400Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:23.691462200Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:24.847902400Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:26.020209200Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:27.191803800Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:28.356993500Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:29.521383900Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:13:30.703212900Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:31.855291400Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:33.002594500Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:34.150536400Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:35.303227300Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:13:36.459891700Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:37.609585500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:38.767712400Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:39.908387800Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:41.095379500Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:42.240448Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:43.383840300Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:44.531545300Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:45.683537200Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:46.838721Z granted/waiting/lockWaiters=6/0/0
2026-09-29T13:13:47.981111700Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:13:49.142405900Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:13:50.339165600Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:51.503175Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:52.674140100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:53.822034800Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:54.978021700Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:13:56.135734800Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:13:57.288893300Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:13:58.442485Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:13:59.597453900Z granted/waiting/lockWaiters=1/0/0
2026-09-29T13:14:00.739032500Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:14:01.891155Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:14:03.040435100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:14:04.193896100Z granted/waiting/lockWaiters=4/0/0
2026-09-29T13:14:05.347348300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:14:06.502647500Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:14:07.650462500Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:14:08.820903200Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:14:09.964211600Z granted/waiting/lockWaiters=5/0/0
2026-09-29T13:14:11.119457400Z granted/waiting/lockWaiters=3/0/0
2026-09-29T13:14:12.288122400Z granted/waiting/lockWaiters=2/0/0
2026-09-29T13:14:13.405968300Z granted/waiting/lockWaiters=
```

### log excerpts: evento-eu

```
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-5accdd-hot-europa-1
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-5accdd-hot-europa-2
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-5accdd-hot-europa-1
```

### log excerpts: evento-apac

```
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-5accdd-hot-europa-2
```

### stack actions

```
```

