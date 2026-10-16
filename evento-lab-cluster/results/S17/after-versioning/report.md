# S17: Context routing: one component class, one engine per configured context

- Label: `after-versioning`
- Gaps exercised: setComponentContexts, G15, G16, G8 (registration)
- Started: 2026-10-15T23:55:50.231724700Z, finished: 2026-10-15T23:56:18.049196600Z (27 s)
- Verdicts: {AS_EXPECTED=8}

## Parameters

- orderIdPrefix: `s17-after-versioning-5620ad-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- order-query-eu whoami: `{"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA","AFRICA"],"consumerPlan":{"OrderProjector":["EUROPA","AFRICA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| one JVM, two contexts, two engines | **AS_EXPECTED** | order-query-eu (EVENTO_CONTEXTS=EUROPA,AFRICA) plans OrderProjector engines [EUROPA, AFRICA] and no CatalogProjector engine |  | plan {"OrderProjector":["EUROPA","AFRICA"],"CatalogProjector":[]} |
| consumer ids per context, per instance | **AS_EXPECTED** | every deployment registers exactly <bundleId>_<Component>_1_<ctx> for each configured context | rows missing where the discovery race lost the registration (G8) | all 11 deployments match |
| each lane's state in its deployment's database | **AS_EXPECTED** | regional engines in their cell database, the ALL lane in 'consumers', wildcard lanes in EU |  | 16 checkpoints, all in place |
| no cross-context rows | **AS_EXPECTED** | each order only in its region's cell database, with its region as context |  | 14 orders, all in place |
| each region projected by its own deployment | **AS_EXPECTED** | order_view.updated_by = the order-query deployment whose EVENTO_CONTEXTS lists the region |  | all rows written by the owning deployment |
| the wildcard deployment sees every region | **AS_EXPECTED** | order_view_global holds all 14 orders, 2 per region |  | 14 orders, by context {AFRICA=2, AMERICA=2, AUSTRALIA_NZ=2, CINA=2, EUROPA=2, INDIA=2, SUD_AMERICA=2} |
| I4 regional isolation | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 14 |
| ALL events never reach an order read model | **AS_EXPECTED** | no order_view row with context ALL |  | 0 such rows |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 14 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 14 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 14 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 3356 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=14 ack=14 failed=0 p50=821ms p95=1477ms p99=1477ms max=1477ms |

## Metrics

```json
{
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 566,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 8,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 6,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 6,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 4,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 4,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 10,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 10,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12
    }
  }
}
```

## Timeline

- +8,5 s: scenario start
- +21,1 s: invariants: converging up to 90 s
- +25,9 s: cleanup start
- +27,8 s: cleanup done

### core__consumer by instance

```
order-observer -> [order-observer_NotificationObserver_1_*]
order-query-am -> [order-query_OrderProjector_1_AMERICA, order-query_OrderProjector_1_SUD_AMERICA]
order-query-apac -> [order-query_OrderProjector_1_AUSTRALIA_NZ, order-query_OrderProjector_1_INDIA]
order-query-cina -> [order-query_OrderProjector_1_CINA]
order-query-eu -> [order-query_OrderProjector_1_AFRICA, order-query_OrderProjector_1_EUROPA]
order-query-global-apac -> [order-query_CatalogProjector_1_ALL]
order-query-global-eu -> [order-query_CatalogProjector_1_ALL]
order-query-wildcard -> [order-query_OrderProjector_1_*]
order-saga-am -> [order-saga_PaymentSaga_1_AMERICA, order-saga_PaymentSaga_1_SUD_AMERICA]
order-saga-apac -> [order-saga_PaymentSaga_1_AUSTRALIA_NZ, order-saga_PaymentSaga_1_CINA, order-saga_PaymentSaga_1_INDIA]
order-saga-eu -> [order-saga_PaymentSaga_1_AFRICA, order-saga_PaymentSaga_1_EUROPA]
```

### consumer checkpoints

```
EU/order-observer_NotificationObserver_1_*=552
EU/order-query_OrderProjector_1_*=552
EU/order-query_OrderProjector_1_AFRICA=534
EU/order-query_OrderProjector_1_EUROPA=524
EU/order-saga_PaymentSaga_1_AFRICA=534
EU/order-saga_PaymentSaga_1_EUROPA=524
AM/order-query_OrderProjector_1_AMERICA=541
AM/order-query_OrderProjector_1_SUD_AMERICA=548
AM/order-saga_PaymentSaga_1_AMERICA=541
AM/order-saga_PaymentSaga_1_SUD_AMERICA=548
APAC/order-query_OrderProjector_1_AUSTRALIA_NZ=547
APAC/order-query_OrderProjector_1_CINA=552
APAC/order-query_OrderProjector_1_INDIA=545
APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ=547
APAC/order-saga_PaymentSaga_1_CINA=552
APAC/order-saga_PaymentSaga_1_INDIA=545
```

### stack actions

```
```

