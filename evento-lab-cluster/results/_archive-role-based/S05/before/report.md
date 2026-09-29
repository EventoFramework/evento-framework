# S05: Concurrent same-aggregate commands from two cells

- Label: `before`
- Gaps exercised: G3, G6, G9
- Started: 2026-09-29T08:50:52.324461200Z, finished: 2026-09-29T08:52:30.979792700Z (98 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s05-before-3eb076-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- wanTowardsEsPrimary: `[am-to-es-pg-eu=[wan-latency-up{latency=40, jitter=5}, wan-latency-down{latency=40, jitter=5}], apac-to-es-pg-eu=[wan-latency-up{latency=75, jitter=5}, wan-latency-down{latency=75, jitter=5}]]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| contended command latency | **AS_EXPECTED** | bounded by lock hand-off + WAN RTT: p99 well under 5 s | p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock | n=120 ack=117 failed=3 p50=2789ms p95=3348ms p99=4274ms max=4425ms failures={error=3} |
| cross-server advisory-lock deadlock | **KNOWN_DEFECT_REPRODUCED** | no deadlock: each lock waiter holds nothing else | all threads of a server share one pinned lock session (G3), so two servers can form a wait cycle on unrelated keys | 3 'deadlock detected' on the servers; failed commands {error: Failed to acquire advisory lock for key: RESOURCE:<id>=3} |
| no aggregate fork (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | a lost advisory lock lets two servers append from one story (G3 + G6) | 0 of 2 aggregates invalid |
| items stored = items acknowledged (I1, I2) | **AS_EXPECTED** | no loss, no duplicate |  | 0 of 117 acknowledged ops missing; 0 unacknowledged ops were stored anyway \| no duplicates |

- _cross-server advisory-lock deadlock_: Postgres breaks the cycle after deadlock_timeout (1 s) by failing one pg_advisory_lock: the command fails fast instead of stalling.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 117 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 2 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view_<r> contains only context R | 0 foreign row(s) in 2 |
| I5 | global completeness | **PASS** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 120 s | converged in 231 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=117 failed=3 p50=2789ms p95=3348ms p99=4274ms max=4425ms failures={error=3} |

## Metrics

```json
{
  "latencyByRegion" : {
    "CINA" : {
      "count" : 60,
      "acked" : 60,
      "failed" : 0,
      "p50" : 3013,
      "p95" : 3593,
      "p99" : 4425,
      "max" : 4425,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 60,
      "acked" : 57,
      "failed" : 3,
      "p50" : 207,
      "p95" : 2855,
      "p99" : 2954,
      "max" : 2954,
      "kinds" : {
        "error" : 3
      }
    }
  },
  "latencyAll" : {
    "count" : 120,
    "acked" : 117,
    "failed" : 3,
    "p50" : 2789,
    "p95" : 3348,
    "p99" : 4274,
    "max" : 4425,
    "kinds" : {
      "error" : 3
    }
  },
  "maxAdvisoryLockWaiters" : 2,
  "logMarkers" : {
    "evento-eu" : {
      "deadlock detected" : 3,
      "Failed to acquire advisory lock" : 3
    },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 3
  }
}
```

## Timeline

- +4,2 s: scenario start
- +4,4 s: hot orders [s05-before-3eb076-hot-europa-1, s05-before-3eb076-hot-europa-2]
- +4,4 s: contention start: [EUROPA, CINA] x 2 workers x 30
- +97,0 s: contention done: 120 commands
- +97,2 s: invariants: converging up to 120 s
- +97,5 s: cleanup start
- +98,7 s: cleanup done

### pg_locks samples (es-pg-eu)

```
2026-09-29T08:50:56.961335900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:50:58.122463400Z granted/waiting/lockWaiters=5/1/1
2026-09-29T08:50:59.292363Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:00.486391600Z granted/waiting/lockWaiters=5/1/1
2026-09-29T08:51:01.653345600Z granted/waiting/lockWaiters=5/1/1
2026-09-29T08:51:02.799678100Z granted/waiting/lockWaiters=4/1/1
2026-09-29T08:51:03.978909500Z granted/waiting/lockWaiters=7/1/1
2026-09-29T08:51:05.208502400Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:06.385471500Z granted/waiting/lockWaiters=9/2/2
2026-09-29T08:51:07.556147700Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:08.727316400Z granted/waiting/lockWaiters=4/1/1
2026-09-29T08:51:09.908878800Z granted/waiting/lockWaiters=9/1/1
2026-09-29T08:51:11.064014Z granted/waiting/lockWaiters=6/2/2
2026-09-29T08:51:12.227303400Z granted/waiting/lockWaiters=5/2/2
2026-09-29T08:51:13.430898200Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:14.594065700Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:15.787235700Z granted/waiting/lockWaiters=5/2/2
2026-09-29T08:51:16.950575900Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:18.127236300Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:19.300356400Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:20.474007100Z granted/waiting/lockWaiters=7/2/2
2026-09-29T08:51:21.650107Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:22.822541300Z granted/waiting/lockWaiters=6/1/1
2026-09-29T08:51:23.999570300Z granted/waiting/lockWaiters=7/1/1
2026-09-29T08:51:25.173633300Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:26.338843300Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:27.487508800Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:51:28.648212500Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:29.802531300Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:30.972247Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:32.134968800Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:33.292564900Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:34.485895200Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:35.637508100Z granted/waiting/lockWaiters=7/0/0
2026-09-29T08:51:36.797896200Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:37.962471Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:39.121288600Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:51:40.262314300Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:41.411518100Z granted/waiting/lockWaiters=9/0/0
2026-09-29T08:51:42.593545400Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:43.751832400Z granted/waiting/lockWaiters=7/0/0
2026-09-29T08:51:44.928870700Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:46.113628Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:51:47.280398500Z granted/waiting/lockWaiters=7/0/0
2026-09-29T08:51:48.439311800Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:49.611184300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:51:50.782489600Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:51:52.005616400Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:51:53.187466900Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:51:54.342729200Z granted/waiting/lockWaiters=2/0/0
2026-09-29T08:51:55.501116300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:51:56.655033900Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:51:57.806130100Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:51:58.958513500Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:52:00.130729300Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:52:01.329943100Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:52:02.495768100Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:03.659883700Z granted/waiting/lockWaiters=0/0/0
2026-09-29T08:52:04.825093500Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:52:05.984237400Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:07.153755300Z granted/waiting/lockWaiters=1/0/0
2026-09-29T08:52:08.339767700Z granted/waiting/lockWaiters=6/0/0
2026-09-29T08:52:09.531451700Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:10.710233800Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:52:11.858856900Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:13.000856700Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:52:14.165225300Z granted/waiting/lockWaiters=8/0/0
2026-09-29T08:52:15.303625700Z granted/waiting/lockWaiters=2/0/0
2026-09-29T08:52:16.468690600Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:17.627490400Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:52:18.767804700Z granted/waiting/lockWaiters=1/0/0
2026-09-29T08:52:19.951652700Z granted/waiting/lockWaiters=7/0/0
2026-09-29T08:52:21.111675400Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:22.260654Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:52:23.423868Z granted/waiting/lockWaiters=7/0/0
2026-09-29T08:52:24.567953400Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:52:25.711026300Z granted/waiting/lockWaiters=5/0/0
2026-09-29T08:52:26.870978800Z granted/waiting/lockWaiters=4/0/0
2026-09-29T08:52:28.019017300Z granted/waiting/lockWaiters=3/0/0
2026-09-29T08:52:29.203405500Z granted/waiting/lockWaiters=3/0/0
```

### log excerpts: evento-eu

```
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-3eb076-hot-europa-1
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-3eb076-hot-europa-1
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s05-before-3eb076-hot-europa-2
```

### stack actions

```
```

