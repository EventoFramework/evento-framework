# S08: Event-store primary failover (promote es-pg-am)

- Label: `after-versioning`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-10-16T00:34:56.240267600Z, finished: 2026-10-16T00:40:53.311614Z (357 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-after-versioning-8fba4e-`
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
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 15.07 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 127 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 53 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 127 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 53 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 53 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 356 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=131 ack=127 failed=4 p50=791ms p95=15899ms p99=30373ms max=30379ms failures={error=2, timeout=2} |

## Metrics

```json
{
  "writeOutageSeconds" : 15.07,
  "afterKill" : {
    "count" : 99,
    "acked" : 95,
    "failed" : 4,
    "p50" : 759,
    "p95" : 19847,
    "p99" : 30379,
    "max" : 30379,
    "kinds" : {
      "error" : 2,
      "timeout" : 2
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
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 2
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 3003,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 14,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 14,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 15,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 3,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 3,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 2,
      "EU/order-query_OrderProjector_1_EUROPA" : 4,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 2,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 4
    }
  },
  "esHeadAfterFailover" : 3003,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +7,9 s: scenario start
- +7,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +38,3 s: killed es-pg-eu (ES primary)
- +43,5 s: promoted es-pg-am: t
- +128,6 s: load done: 131 requests
- +128,9 s: invariants: converging up to 180 s
- +129,6 s: cleanup start
- +357,1 s: cleanup done

### log excerpts: evento-eu

```
java.lang.RuntimeException: Failed to acquire advisory lock for key: RESOURCE:s08-after-versioning-8fba4e-africa-16
```

### stack actions

```
2026-10-16T00:35:34.150083Z docker kill es-pg-eu
2026-10-16T00:35:39.550408500Z psql es-pg-am/evento: SELECT pg_promote()
2026-10-16T00:37:05.846278600Z compose down -v
2026-10-16T00:37:36.739494Z compose up -d --wait
2026-10-16T00:40:05.680364700Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-16T00:40:51.428801800Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

