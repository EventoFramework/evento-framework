# S08: Event-store primary failover (promote es-pg-am)

- Label: `before`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-09-29T13:27:15.173490500Z, finished: 2026-09-29T13:32:50.180181Z (335 s)
- Verdicts: {UNEXPECTED=1, AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s08-before-763b7e-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **UNEXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | no command acknowledged after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 36 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 14 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 36 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 14 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 14 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 727 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=78 ack=36 failed=42 p50=30013ms p95=30455ms p99=30459ms max=30459ms failures={error=2, timeout=40} |

## Metrics

```json
{
  "writeOutageSeconds" : null,
  "afterKill" : {
    "count" : 42,
    "acked" : 0,
    "failed" : 42,
    "p50" : 30185,
    "p95" : 30456,
    "p99" : 30459,
    "max" : 30459,
    "kinds" : {
      "error" : 2,
      "timeout" : 40
    }
  },
  "logMarkers" : {
    "evento-eu" : {
      "Failed to acquire advisory lock" : 2
    },
    "evento-am" : {
      "Failed to acquire advisory lock" : 2
    },
    "evento-apac" : {
      "event=lock_release_failed" : 2,
      "Failed to acquire advisory lock" : 3
    }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 2,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 40
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1858,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 6,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 8,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 6,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 8,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 18,
      "EU/order-query_OrderProjector_1_EUROPA" : 14,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 18,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 14
    }
  },
  "esHeadAfterFailover" : 1858,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +6,2 s: scenario start
- +6,2 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +36,6 s: killed es-pg-eu (ES primary)
- +41,9 s: promoted es-pg-am: t
- +142,6 s: load done: 78 requests
- +145,2 s: invariants: converging up to 180 s
- +146,4 s: cleanup start
- +335,0 s: cleanup done

### log excerpts: evento-eu

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-before-763b7e-africa-19
java.lang.RuntimeException: Failed to acquire advisory lock for key: es-lock
```

### log excerpts: evento-am

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: es-lock
java.lang.RuntimeException: Failed to acquire advisory lock for key: es-lock
```

### log excerpts: evento-apac

```
20260929132814322 ERROR 1 --- [bus-business-19                         ] PgDistributedLock                        : event=lock_release_failed key=ic__order-query_OrderProjector_PaymentProcessedEvent critical section succeeded; its result is preserved and the advisory lock may be leaked
20260929132815676 ERROR 1 --- [bus-business-43                         ] PgDistributedLock                        : event=lock_release_failed key=order-saga_PaymentSaga_OrderCompletedEvent critical section succeeded; its result is preserved and the advisory lock may be leaked
java.lang.RuntimeException: Failed to acquire advisory lock for key: order-saga_PaymentSaga_PaymentProcessedEvent
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-before-763b7e-india-15
java.lang.RuntimeException: Failed to acquire advisory lock for key: es-lock
```

### stack actions

```
2026-09-29T13:27:51.428390400Z docker kill es-pg-eu
2026-09-29T13:27:56.807850600Z psql es-pg-am/evento: SELECT pg_promote()
2026-09-29T13:29:41.525827400Z compose down -v
2026-09-29T13:30:28.456127200Z compose up -d --wait
2026-09-29T13:32:47.927435900Z bash scripts/repair-catalog.sh (G8 workaround)
2026-09-29T13:32:48.273335700Z catalog repair exit 1: []
```

