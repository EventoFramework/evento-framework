# S11: Context isolation with underscore regions

- Label: `before`
- Gaps exercised: G15, G16, L6
- Started: 2026-09-29T12:42:33.317239300Z, finished: 2026-09-29T12:43:25.418587Z (52 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=4, INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s11-before-28d875-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- syntheticOrders: `{SUDXAMERICA=s11-before-28d875-synthetic-sudxamerica-6, AUSTRALIAXNZ=s11-before-28d875-synthetic-australiaxnz-12}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| app-pg-am.order_view vs context SUDXAMERICA | **KNOWN_DEFECT_REPRODUCED** | only SUD_AMERICA events reach OrderProjector's SUD_AMERICA engine in order-query-am | LIKE 'SUD_AMERICA' matches SUDXAMERICA: the synthetic order leaks in (G15) | 5 own rows, leaked: [s11-before-28d875-synthetic-sudxamerica-6] |
| app-pg-apac.order_view vs context AUSTRALIAXNZ | **KNOWN_DEFECT_REPRODUCED** | only AUSTRALIA_NZ events reach OrderProjector's AUSTRALIA_NZ engine in order-query-apac | LIKE 'AUSTRALIA_NZ' matches AUSTRALIAXNZ: the synthetic order leaks in (G15) | 5 own rows, leaked: [s11-before-28d875-synthetic-australiaxnz-12] |
| I4 regional isolation | **KNOWN_DEFECT_REPRODUCED** | order_view of each cell database contains only that cell's contexts | the synthetic SUDXAMERICA / AUSTRALIAXNZ rows are foreign rows in the regional views (G15) | 2 foreign row(s) in 12 |
| GUI consumer context (G16) | **INCONCLUSIVE** | every consumer shows its full context (SUD_AMERICA, AUSTRALIA_NZ) | split('_') cuts names with '_' (e.g. SUD_AMERICA is shown as SUD) | not testable: the page lists no consumer with '_' in its context |
| GUI lists one consumer per regional engine | **KNOWN_DEFECT_REPRODUCED** | 7 regional consumers of order-query's OrderProjector (one per context) | fewer rows: the page groups consumers per component (N10), or registrations lost to the discovery race (G8) | 0 regional consumer row(s) on the page; 7 regional consumer id(s) in core__consumer (N10: grouped by the page) |

- _GUI lists one consumer per regional engine_: One OrderProjector class, one engine per context (setComponentContexts).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 22 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 12 aggregates invalid |
| I4 | regional isolation | **FAIL** | order_view of each cell database contains only that cell's contexts | 2 foreign row(s) in 12 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 205 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=22 ack=22 failed=0 p50=1361ms p95=2214ms p99=2316ms max=2316ms |

<details><summary>I4: 2 violation(s)</summary>

- app-pg-am.order_view holds s11-before-28d875-synthetic-sudxamerica-6 with context SUDXAMERICA (cell AM serves [AMERICA, SUD_AMERICA])
- app-pg-apac.order_view holds s11-before-28d875-synthetic-australiaxnz-12 with context AUSTRALIAXNZ (cell APAC serves [CINA, INDIA, AUSTRALIA_NZ])

</details>

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 11,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 44,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 11,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 44,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 44,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 44,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 44
    },
    "esHead" : 44
  }
}
```

## Timeline

- +5,9 s: scenario start
- +18,8 s: synthetic order s11-before-28d875-synthetic-sudxamerica-6 with context SUDXAMERICA: ACK
- +41,4 s: synthetic order s11-before-28d875-synthetic-australiaxnz-12 with context AUSTRALIAXNZ: ACK
- +44,0 s: synthetic orders in global + regional views: true
- +49,0 s: invariants: converging up to 90 s
- +50,4 s: cleanup start
- +52,1 s: cleanup done

### GUI /api/consumer/ on evento-am

```
order-saga_PaymentSaga_1_AMERICA -> bundle=order-saga context=AMERICA version=1
order-query_OrderProjector_1_* -> bundle=order-query context=* version=1
order-query_CatalogProjector_1_ALL -> bundle=order-query context=ALL version=1
order-observer_NotificationObserver_1_* -> bundle=order-observer context=* version=1
```

### stack actions

```
```

