# S08: Event-store primary failover (promote es-pg-am)

- Label: `v3e`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-10-22T16:16:10.494852600Z, finished: 2026-10-22T16:21:58.729799700Z (348 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-v3e-ff4587-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.5, itemsPerOrder=1, durationSeconds=120}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 12.406 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 131 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 54 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 131 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 54 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 54 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 414 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=134 ack=131 failed=3 p50=395ms p95=16542ms p99=30033ms max=30037ms failures={timeout=3} |

## Metrics

```json
{
  "writeOutageSeconds" : 12.406,
  "afterKill" : {
    "count" : 99,
    "acked" : 96,
    "failed" : 3,
    "p50" : 398,
    "p95" : 16953,
    "p99" : 30037,
    "max" : 30037,
    "kinds" : {
      "timeout" : 3
    }
  },
  "logMarkers" : {
    "evento-eu" : {
      "Failed to acquire advisory lock" : 1,
      "event=idempotent_replay" : 1
    },
    "evento-am" : {
      "Failed to acquire advisory lock" : 1
    },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 3
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 4454,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 12,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 8,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 5,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 5,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 2,
      "EU/order-query_OrderProjector_1_EUROPA" : 18,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 2,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 18
    }
  },
  "esHeadAfterFailover" : 4454,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +8,1 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,6 s: killed es-pg-eu (ES primary)
- +43,8 s: promoted es-pg-am: t
- +129,0 s: load done: 134 requests
- +129,2 s: invariants: converging up to 180 s
- +130,0 s: cleanup start
- +348,2 s: cleanup done

### log excerpts: evento-eu

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: ic__order-observer_NotificationObserver_OrderItemAddedEvent
20261022161700988 INFO  1 --- [bus-business-15                         ] CommandBrokerHandler                     : event=idempotent_replay payloadType=CreateOrderCommand idempotencyKey=13e91b3f-9cf6-4391-9069-3d0d0a249a5f correlationId=3070ffb0-6566-483a-ba2d-18db647b9243 stage=append_conflict seq=4329
```

### log excerpts: evento-am

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: ic__order-saga_PaymentSaga_PaymentProcessedEvent
```

### stack actions

```
2026-10-22T16:16:48.584378600Z docker kill es-pg-eu
2026-10-22T16:16:54.064653200Z psql es-pg-am/evento: SELECT pg_promote()
2026-10-22T16:18:20.528597200Z compose down -v
2026-10-22T16:18:38.571573700Z compose up -d --wait
2026-10-22T16:21:37.994545300Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-22T16:21:56.873028200Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

