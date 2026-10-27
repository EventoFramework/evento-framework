# S11: Context isolation with underscore regions

- Label: `v3e`
- Gaps exercised: G15, G16, L6
- Started: 2026-10-22T15:49:13.398852200Z, finished: 2026-10-22T15:50:43.168604400Z (89 s)
- Verdicts: {AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s11-v3e-49302d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- syntheticOrders: `{SUDXAMERICA=s11-v3e-49302d-synthetic-sudxamerica-6, AUSTRALIAXNZ=s11-v3e-49302d-synthetic-australiaxnz-12}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| app-pg-am.order_view vs context SUDXAMERICA | **AS_EXPECTED** | only SUD_AMERICA events reach OrderProjector's SUD_AMERICA engine in order-query-am | LIKE 'SUD_AMERICA' matches SUDXAMERICA: the synthetic order leaks in (G15) | 5 own rows, leaked: none |
| app-pg-apac.order_view vs context AUSTRALIAXNZ | **AS_EXPECTED** | only AUSTRALIA_NZ events reach OrderProjector's AUSTRALIA_NZ engine in order-query-apac | LIKE 'AUSTRALIA_NZ' matches AUSTRALIAXNZ: the synthetic order leaks in (G15) | 5 own rows, leaked: none |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts | the synthetic SUDXAMERICA / AUSTRALIAXNZ rows are foreign rows in the regional views (G15) | 0 foreign row(s) in 10 |
| GUI consumer context (G16) | **AS_EXPECTED** | every consumer shows its full context (SUD_AMERICA, AUSTRALIA_NZ) | split('_') cuts names with '_' (e.g. SUD_AMERICA is shown as SUD) | all contexts shown correctly ([order-saga_PaymentSaga_1_SUD_AMERICA, order-query_OrderProjector_1_SUD_AMERICA, order-saga_PaymentSaga_1_AUSTRALIA_NZ, order-query_OrderProjector_1_AUSTRALIA_NZ]) |
| GUI lists one consumer per regional engine | **AS_EXPECTED** | 7 regional consumers of order-query's OrderProjector (one per context) | fewer rows: the page groups consumers per component (N10), or registrations lost to the discovery race (G8) | 7 regional consumer row(s) on the page; 7 regional consumer id(s) in core__consumer |

- _GUI lists one consumer per regional engine_: One OrderProjector class, one engine per context (setComponentContexts).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 22 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 12 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 10 |
| I5 | global completeness | **FAIL** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 2 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 182 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=22 ack=22 failed=0 p50=439ms p95=930ms p99=1449ms max=1449ms |

<details><summary>I5: 2 violation(s)</summary>

- s11-v3e-49302d-synthetic-australiaxnz-12 (AUSTRALIAXNZ) in global but in no cell database
- s11-v3e-49302d-synthetic-sudxamerica-6 (SUDXAMERICA) in global but in no cell database

</details>

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 12,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 22,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 12,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 22,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 22,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 22,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 22
    },
    "esHead" : 22
  }
}
```

## Timeline

- +8,2 s: scenario start
- +12,8 s: synthetic order s11-v3e-49302d-synthetic-sudxamerica-6 with context SUDXAMERICA: ACK
- +19,9 s: synthetic order s11-v3e-49302d-synthetic-australiaxnz-12 with context AUSTRALIAXNZ: ACK
- +81,4 s: synthetic orders in global + regional views: false (not all leaked within 60 s)
- +86,4 s: invariants: converging up to 90 s
- +87,9 s: cleanup start
- +89,8 s: cleanup done

### GUI /api/consumer/ on evento-am

```
order-saga_PaymentSaga_1_AFRICA -> bundle=order-saga context=AFRICA version=1
order-saga_PaymentSaga_1_EUROPA -> bundle=order-saga context=EUROPA version=1
order-query_OrderProjector_1_AFRICA -> bundle=order-query context=AFRICA version=1
order-query_OrderProjector_1_EUROPA -> bundle=order-query context=EUROPA version=1
order-query_OrderProjector_1_* -> bundle=order-query context=* version=1
order-observer_NotificationObserver_1_* -> bundle=order-observer context=* version=1
order-query_CatalogProjector_1_ALL -> bundle=order-query context=ALL version=1
order-saga_PaymentSaga_1_AMERICA -> bundle=order-saga context=AMERICA version=1
order-saga_PaymentSaga_1_SUD_AMERICA -> bundle=order-saga context=SUD_AMERICA version=1
order-query_OrderProjector_1_SUD_AMERICA -> bundle=order-query context=SUD_AMERICA version=1
order-query_OrderProjector_1_AMERICA -> bundle=order-query context=AMERICA version=1
order-saga_PaymentSaga_1_INDIA -> bundle=order-saga context=INDIA version=1
order-saga_PaymentSaga_1_AUSTRALIA_NZ -> bundle=order-saga context=AUSTRALIA_NZ version=1
order-saga_PaymentSaga_1_CINA -> bundle=order-saga context=CINA version=1
order-query_OrderProjector_1_AUSTRALIA_NZ -> bundle=order-query context=AUSTRALIA_NZ version=1
order-query_OrderProjector_1_INDIA -> bundle=order-query context=INDIA version=1
order-query_OrderProjector_1_CINA -> bundle=order-query context=CINA version=1
```

### stack actions

```
```

