# S08: Event-store primary failover (promote es-pg-am)

- Label: `v3c`
- Gaps exercised: G3, G5, G6, G19
- Started: 2026-10-22T13:19:20.187302700Z, finished: 2026-10-22T13:25:17.935859Z (357 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s08-v3c-e6a465-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- promoted: `es-pg-am`
- replication: `synchronous`
- load: `{ratePerMinutePerRegion=4.0, completeRatio=0.5, itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| writes resume after promotion | **AS_EXPECTED** | commands acknowledged again within 60 s (servers follow the multi-host URL) |  | first ack 15.061 s after the kill |
| no acknowledged event lost (I1) | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 130 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| no duplicate after ambiguous commits (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | a client retry after an ambiguous commit duplicates (G5) | no duplicates |
| no fork although every advisory lock vanished (I3) | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique | locks lost with the old primary let two servers append from one story (G3, G6) | 0 of 52 aggregates invalid |
| no consumer checkpoint above the new ES head | **AS_EXPECTED** | every checkpoint <= head |  | none |

- _no duplicate after ambiguous commits (I2)_: The load generator does not retry, so duplicates here come from inside the stack.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 130 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 52 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 52 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 471 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=134 ack=130 failed=4 p50=362ms p95=2421ms p99=27407ms max=27684ms failures={error=4} |

## Metrics

```json
{
  "writeOutageSeconds" : 15.061,
  "afterKill" : {
    "count" : 98,
    "acked" : 94,
    "failed" : 4,
    "p50" : 356,
    "p95" : 13091,
    "p99" : 27684,
    "max" : 27684,
    "kinds" : {
      "error" : 4
    }
  },
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "failedRequestsByError" : {
    "error: Unable to rollback against JDBC Connection" : 4
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 20,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 17,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 20,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 17,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 7,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 7
    },
    "esHead" : 4381
  },
  "esHeadAfterFailover" : 4381,
  "checkpointsAboveHead" : { }
}
```

## Timeline

- +9,3 s: scenario start
- +9,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +39,9 s: killed es-pg-eu (ES primary)
- +45,1 s: promoted es-pg-am: t
- +130,2 s: load done: 134 requests
- +130,5 s: invariants: converging up to 180 s
- +131,5 s: cleanup start
- +357,7 s: cleanup done

### stack actions

```
2026-10-22T13:19:59.559328Z docker kill es-pg-eu
2026-10-22T13:20:05.052783700Z psql es-pg-am/evento: SELECT pg_promote()
2026-10-22T13:21:31.703233900Z compose down -v
2026-10-22T13:21:50.660481800Z compose up -d --wait
2026-10-22T13:24:56.250281700Z C:\Program Files\Git\bin\bash.exe scripts/repair-catalog.sh (G8 workaround)
2026-10-22T13:25:15.994659Z catalog repair exit 0: [[lab] event=lab_catalog_repair pass=1 status=complete]
```

