# S08: Event-store primary failover (promote es-pg-am)

- Label: `after-query`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-10-15T14:06:52.823160800Z, finished: 2026-10-15T14:13:14.580336400Z (381 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-after-query-9cf3a2-`
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
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 15.02 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 100 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 41 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 100 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 41 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 41 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 339 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=116 ack=100 failed=16 p50=878ms p95=30393ms p99=30404ms max=30406ms failures={error=2, timeout=14} |

## Metrics

```json
{
  "writeOutageSeconds" : 15.02,
  "afterKill" : {
    "count" : 81,
    "acked" : 65,
    "failed" : 16,
    "p50" : 1051,
    "p95" : 30397,
    "p99" : 30406,
    "max" : 30406,
    "kinds" : {
      "error" : 2,
      "timeout" : 14
    }
  },
  "logMarkers" : {
    "evento-eu" : {
      "Failed to acquire advisory lock" : 1
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 1,
    "error: PreparedStatementCallback; SQL [WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) INSERT INTO es__events (event_sequence_number, aggregate_id, event_message, event_name, context, ide..." : 1,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 14
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 3020,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 115,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 110,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 115,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 110,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 5,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 5,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 10,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 10,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    }
  },
  "esHeadAfterFailover" : 3020,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +8,3 s: scenario start
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,8 s: killed es-pg-eu (ES primary)
- +44,0 s: promoted es-pg-am: t
- +144,0 s: load done: 116 requests
- +144,4 s: invariants: converging up to 180 s
- +145,1 s: cleanup start
- +381,8 s: cleanup done

### log excerpts: evento-eu

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-after-query-9cf3a2-europa-18
```

### stack actions

```
2026-10-15T14:07:31.170556Z docker kill es-pg-eu
2026-10-15T14:07:36.603153500Z psql es-pg-am/evento: SELECT pg_promote()
2026-10-15T14:09:17.917635900Z compose down -v
2026-10-15T14:09:54.162530Z compose up -d --wait
2026-10-15T14:12:28.362689300Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-15T14:13:12.696761900Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

