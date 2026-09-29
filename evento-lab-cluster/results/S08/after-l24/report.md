# S08: Event-store primary failover (promote es-pg-am)

- Label: `after`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-09-29T16:09:33.875017600Z, finished: 2026-09-29T16:16:03.623912200Z (389 s)
- Verdicts: {UNEXPECTED=1, AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s08-after-5d1be4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **UNEXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | no command acknowledged after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 34 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 14 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 34 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 14 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 14 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 468 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=76 ack=34 failed=42 p50=30041ms p95=30458ms p99=30467ms max=30467ms failures={error=9, timeout=33} |

## Metrics

```json
{
  "writeOutageSeconds" : null,
  "afterKill" : {
    "count" : 42,
    "acked" : 0,
    "failed" : 42,
    "p50" : 30204,
    "p95" : 30462,
    "p99" : 30467,
    "max" : 30467,
    "kinds" : {
      "error" : 9,
      "timeout" : 33
    }
  },
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : {
      "Failed to acquire advisory lock" : 1
    },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "error: Failed to acquire advisory lock for key: RESOURCE:<id>" : 1,
    "error: PreparedStatementCallback; SQL [WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) INSERT INTO es__events (event_sequence_number, aggregate_id, event_message, event_name, context) SEL..." : 8,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 33
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 5,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 5,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 10,
      "EU/order-query_OrderProjector_1_EUROPA" : 11,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 10,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 11
    },
    "esHead" : 4693
  },
  "esHeadAfterFailover" : 4693,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +7,9 s: scenario start
- +7,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,4 s: killed es-pg-eu (ES primary)
- +43,7 s: promoted es-pg-am: t
- +143,9 s: load done: 76 requests
- +144,3 s: invariants: converging up to 180 s
- +145,1 s: cleanup start
- +389,7 s: cleanup done

### log excerpts: evento-am

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-after-5d1be4-america-16
```

### stack actions

```
2026-09-29T16:10:11.775154300Z docker kill es-pg-eu
2026-09-29T16:10:17.320585500Z psql es-pg-am/evento: SELECT pg_promote()
2026-09-29T16:11:58.970202300Z compose down -v
2026-09-29T16:12:45.806593400Z compose up -d --wait
2026-09-29T16:15:14.826515100Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-09-29T16:16:01.584095300Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

