# S08: Event-store primary failover (promote es-pg-am)

- Label: `before`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-09-29T17:00:06.163206100Z, finished: 2026-09-29T17:10:15.839260200Z (609 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-before-10de47-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 7.569 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 144 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 56 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 144 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 417 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=145 ack=144 failed=1 p50=2551ms p95=9921ms p99=28474ms max=29420ms failures={error=1} |

## Metrics

```json
{
  "writeOutageSeconds" : 7.569,
  "afterKill" : {
    "count" : 106,
    "acked" : 105,
    "failed" : 1,
    "p50" : 2419,
    "p95" : 16176,
    "p99" : 28474,
    "max" : 29420,
    "kinds" : {
      "error" : 1
    }
  },
  "logMarkers" : {
    "evento-eu" : {
      "Failed to acquire advisory lock" : 1
    },
    "evento-am" : { },
    "evento-apac" : {
      "event=lock_release_failed" : 2,
      "Failed to acquire advisory lock" : 2
    }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 1
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1343,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 15,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 19,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 19,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 3,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 16,
      "EU/order-query_OrderProjector_1_EUROPA" : 9,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 16,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 9
    }
  },
  "esHeadAfterFailover" : 1343,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +8,3 s: scenario start
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,8 s: killed es-pg-eu (ES primary)
- +44,0 s: promoted es-pg-am: t
- +129,2 s: load done: 145 requests
- +129,5 s: invariants: converging up to 180 s
- +130,3 s: cleanup start
- +609,7 s: cleanup done

### log excerpts: evento-eu

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-before-10de47-africa-15
```

### log excerpts: evento-apac

```
20260929170044623 ERROR 1 --- [bus-business-1                          ] PgDistributedLock                        : event=lock_release_failed key=ic__order-query_OrderProjector_PaymentProcessedEvent critical section succeeded; its result is preserved and the advisory lock may be leaked
20260929170044623 ERROR 1 --- [bus-business-11                         ] PgDistributedLock                        : event=lock_release_failed key=ic__order-command_PaymentService_ProcessPaymentCommand critical section succeeded; its result is preserved and the advisory lock may be leaked
java.lang.RuntimeException: Failed to acquire advisory lock for key: ic__order-saga_PaymentSaga_PaymentProcessedEvent
java.lang.RuntimeException: Failed to acquire advisory lock for key: order-saga_PaymentSaga_PaymentProcessedEvent
```

### stack actions

```
2026-09-29T17:00:44.475136400Z docker kill es-pg-eu
2026-09-29T17:00:49.914647600Z psql es-pg-am/evento: SELECT pg_promote()
2026-09-29T17:02:16.500328200Z compose down -v
2026-09-29T17:02:51.554616400Z compose up -d --wait
2026-09-29T17:05:09.980246700Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-09-29T17:10:14.085845100Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 missing=order-observer:consumers=0/1 order-query-eu:consumers=0/2 order-saga-eu:consumers=0/2, [lab] event=lab_catalog_repair container=order-query-eu step=helper_start, [lab] event=lab_catalog_repair container=order-observer step=helper_start, [lab] event=lab_catalog_repair container=order-saga-eu step=helper_start, [lab] event=lab_catalog_repair container=order-saga-eu step=restart, [lab] event=lab_catalog_repair container=order-observer step=restart, [lab] event=lab_catalog_repair container=order-query-eu step=restart, [lab] event=lab_catalog_repair container=order-saga-eu step=helper_remove, [lab] event=lab_catalog_repair container=order-observer step=helper_remove, [lab] event=lab_catalog_repair container=order-query-eu step=helper_remove, [lab] event=lab_catalog_repair pass=2 status=complete]
```

