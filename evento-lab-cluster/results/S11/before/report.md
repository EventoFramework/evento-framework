# S11: Context isolation with underscore regions

- Label: `before`
- Gaps exercised: G15, G16, L6
- Started: 2026-09-29T08:45:37.523055100Z, finished: 2026-09-29T08:46:28.196592700Z (50 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=4, AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s11-before-222ae4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- syntheticOrders: `{SUDXAMERICA=s11-before-222ae4-synthetic-sudxamerica-6, AUSTRALIAXNZ=s11-before-222ae4-synthetic-australiaxnz-12}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| order_view_sud_america vs context SUDXAMERICA | **KNOWN_DEFECT_REPRODUCED** | only SUD_AMERICA events reach query-sud_america | LIKE 'SUD_AMERICA' matches SUDXAMERICA: the synthetic order leaks in (G15) | 5 own rows, leaked: [s11-before-222ae4-synthetic-sudxamerica-6] |
| order_view_australia_nz vs context AUSTRALIAXNZ | **KNOWN_DEFECT_REPRODUCED** | only AUSTRALIA_NZ events reach query-australia_nz | LIKE 'AUSTRALIA_NZ' matches AUSTRALIAXNZ: the synthetic order leaks in (G15) | 5 own rows, leaked: [s11-before-222ae4-synthetic-australiaxnz-12] |
| I4 regional isolation | **KNOWN_DEFECT_REPRODUCED** | order_view_<r> contains only context R | the synthetic SUDXAMERICA / AUSTRALIAXNZ rows are foreign rows in the regional views (G15) | 2 foreign row(s) in 12 |
| GUI consumer context (G16) | **KNOWN_DEFECT_REPRODUCED** | every consumer shows its full context (SUD_AMERICA, AUSTRALIA_NZ) | split('_') cuts names with '_' (e.g. SUD), and the bundle ids query-sud_america/query-australia_nz shift every field | wrong for [query-sud_america_SudAmericaOrderProjector_1_SUD_AMERICA, query-australia_nz_AustraliaNzOrderProjector_1_AUSTRALIA_NZ] |
| GUI lists one consumer per regional projector | **AS_EXPECTED** | 7 regional consumers (one per query-<region> bundle) | one row: consumers are grouped by component simple name, shared by all 7 bundles | 7 regional consumer row(s) |

- _GUI lists one consumer per regional projector_: core__component is keyed by the component's simple class name across bundles.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 22 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 12 aggregates invalid |
| I4 | regional isolation | **FAIL** | order_view_<r> contains only context R | 2 foreign row(s) in 12 |
| I5 | global completeness | **PASS** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 63 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=22 ack=22 failed=0 p50=1319ms p95=2222ms p99=2243ms max=2243ms |

<details><summary>I4: 2 violation(s)</summary>

- order_view_sud_america holds s11-before-222ae4-synthetic-sudxamerica-6 with context SUDXAMERICA
- order_view_australia_nz holds s11-before-222ae4-synthetic-australiaxnz-12 with context AUSTRALIAXNZ

</details>

## Timeline

- +3,9 s: scenario start
- +16,1 s: synthetic order s11-before-222ae4-synthetic-sudxamerica-6 with context SUDXAMERICA: ACK
- +38,6 s: synthetic order s11-before-222ae4-synthetic-australiaxnz-12 with context AUSTRALIAXNZ: ACK
- +43,1 s: synthetic orders in global + regional views: true
- +48,1 s: invariants: converging up to 90 s
- +49,5 s: cleanup start
- +50,7 s: cleanup done

### GUI /api/consumer/ on evento-am

```
cluster-saga_ClusterEventObserver_1_* -> bundle=cluster-saga context=* version=1
query-sud_america_SudAmericaOrderProjector_1_SUD_AMERICA -> bundle=query-sud_america context=1 version=SudAmericaOrderProjector
query-america_AmericaOrderProjector_1_AMERICA -> bundle=query-america context=AMERICA version=1
query-australia_nz_AustraliaNzOrderProjector_1_AUSTRALIA_NZ -> bundle=query-australia_nz context=1 version=AustraliaNzOrderProjector
query-cina_CinaOrderProjector_1_CINA -> bundle=query-cina context=CINA version=1
query-europa_EuropaOrderProjector_1_EUROPA -> bundle=query-europa context=EUROPA version=1
query-india_IndiaOrderProjector_1_INDIA -> bundle=query-india context=INDIA version=1
cluster-saga_PaymentSaga_1_* -> bundle=cluster-saga context=* version=1
cluster-query-global_GlobalOrderProjector_1_* -> bundle=cluster-query-global context=* version=1
query-africa_AfricaOrderProjector_1_AFRICA -> bundle=query-africa context=AFRICA version=1
```

### stack actions

```
```

