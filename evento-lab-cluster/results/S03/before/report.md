# S03: Kill an evento-server

- Label: `before`
- Gaps exercised: G2, G4, G5, G8, G13
- Started: 2026-09-29T12:46:22.928129300Z, finished: 2026-09-29T12:49:19.370824Z (176 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=2, AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s03-before-6d35dc-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"APAC"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=110, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **KNOWN_DEFECT_REPRODUCED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | no APAC command acknowledged while evento-apac was down |
| in-flight APAC calls at the kill | **KNOWN_DEFECT_REPRODUCED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 3 in flight: 3 failed after >= 25 s, 0 failed fast, kinds {timeout=3} |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 38 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 22914 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 38 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 38 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 22914 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=111 ack=90 failed=21 p50=181ms p95=3798ms p99=30177ms max=30474ms failures={error=18, timeout=3} |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : null,
  "apacAfterKill" : {
    "count" : 18,
    "acked" : 0,
    "failed" : 18,
    "p50" : 7,
    "p95" : 60,
    "p99" : 60,
    "max" : 60,
    "kinds" : {
      "error" : 18
    }
  },
  "otherCellsAfterKill" : {
    "count" : 63,
    "acked" : 63,
    "failed" : 0,
    "p50" : 193,
    "p95" : 1928,
    "p99" : 1994,
    "max" : 1994,
    "kinds" : { }
  },
  "apacInFlightAtKill" : 3,
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : { },
    "order-command-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
    },
    "order-query-cina" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
    },
    "order-query-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9,
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 4
    },
    "order-query-global-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
    },
    "order-saga-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9,
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 4
    },
    "gateway-cina" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9,
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 7
    },
    "gateway-india" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9,
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 7
    },
    "gateway-australia-nz" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9,
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 7
    }
  },
  "apacHandshakes" : 0,
  "apacCorrelationExpired" : 5,
  "failedRequestsByError" : {
    "error: bundle not ready: state=CONNECTING" : 2,
    "error: bundle not ready: state=RECONNECTING" : 16,
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 1
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 0,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 85,
      "APAC/order-query_OrderProjector_1_CINA" : 83,
      "APAC/order-query_OrderProjector_1_INDIA" : 81,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 85,
      "APAC/order-saga_PaymentSaga_1_CINA" : 94,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 93,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 4,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 4,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    },
    "esHead" : 713
  }
}
```

## Timeline

- +5,8 s: scenario start
- +5,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +26,3 s: killed evento-apac
- +126,8 s: load done: 111 requests
- +126,9 s: restarting evento-apac
- +149,0 s: evento-apac ready=true
- +151,0 s: invariants: converging up to 180 s
- +174,2 s: cleanup start
- +176,4 s: cleanup done

### log excerpts: order-command-apac

```
2026-09-29T12:46:49.000Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.553Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.880Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.880Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.481Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.596Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-cina

```
2026-09-29T12:46:49.040Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.477Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.802Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.801Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.336Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.209Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-apac

```
2026-09-29T12:46:49.014Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.500Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.833Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.833Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.350Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.263Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:47:18.931Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30158ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30158ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30287ms (timeout=30000)
```

### log excerpts: order-query-global-apac

```
2026-09-29T12:46:49.009Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.432Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.754Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.754Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.267Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.278Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-saga-apac

```
2026-09-29T12:46:49.013Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.609Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:51.484Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:51.484Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:51.955Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:52.926Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:47:18.729Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30087ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30087ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30098ms (timeout=30000)
```

### log excerpts: gateway-cina

```
2026-09-29T12:46:49.004Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.593Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:51.468Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:51.468Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:52.013Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:53.097Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:47:18.754Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T12:46:59.374Z  WARN 1 --- [order-gateway] [cat-handler-144] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-cina-18 region=CINA kind=error latencyMs=1 idempotencyKey=6ec6253b-ae8b-482a-b9fb-4badfe82f35c error=bundle not ready: state=RECONNECTING
2026-09-29T12:47:14.373Z  WARN 1 --- [order-gateway] [cat-handler-148] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-cina-25 region=CINA kind=error latencyMs=0 idempotencyKey=3077b68a-9cfe-43b3-acea-583da0677613 error=bundle not ready: state=RECONNECTING
2026-09-29T12:47:18.754Z  WARN 1 --- [order-gateway] [cat-handler-141] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-6d35dc-cina-11 region=CINA kind=timeout latencyMs=30468 idempotencyKey=efcdbd5d-6da5-4402-823b-1a2f4e684f34 error=AddOrderItemCommand: request expired after 30465ms (timeout=30000)
```

### log excerpts: gateway-india

```
2026-09-29T12:46:49.025Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.529Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.864Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.864Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.460Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.428Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:47:14.788Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T12:46:59.623Z  WARN 1 --- [order-gateway] [cat-handler-144] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-india-19 region=INDIA kind=error latencyMs=1 idempotencyKey=cce75a27-5ff9-4313-8e98-b26944ec8dbf error=bundle not ready: state=CONNECTING
2026-09-29T12:47:14.619Z  WARN 1 --- [order-gateway] [cat-handler-148] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-india-26 region=INDIA kind=error latencyMs=0 idempotencyKey=5655005e-633a-474f-86a0-de4c8df6c139 error=bundle not ready: state=RECONNECTING
2026-09-29T12:47:14.788Z  WARN 1 --- [order-gateway] [cat-handler-140] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-india-12 region=INDIA kind=timeout latencyMs=30169 idempotencyKey=e5da7a22-0761-4e50-95be-d504653788a9 error=CreateOrderCommand: request expired after 30165ms (timeout=30000)
```

### log excerpts: gateway-australia-nz

```
2026-09-29T12:46:49.000Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:49.577Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T12:46:52.915Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T12:46:52.915Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T12:46:53.415Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:46:54.545Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T12:47:17.089Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T12:46:59.147Z  WARN 1 --- [order-gateway] [cat-handler-168] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-australia-nz-17 region=AUSTRALIA_NZ kind=error latencyMs=1 idempotencyKey=938e8f9d-2b5f-4d96-9c8c-c121c15d2ddb error=bundle not ready: state=RECONNECTING
2026-09-29T12:47:14.144Z  WARN 1 --- [order-gateway] [cat-handler-172] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-6d35dc-australia-nz-24 region=AUSTRALIA_NZ kind=error latencyMs=0 idempotencyKey=a763ab34-1c56-4af1-b3d5-1a7189fbcc28 error=bundle not ready: state=RECONNECTING
2026-09-29T12:47:17.089Z  WARN 1 --- [order-gateway] [cat-handler-165] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-6d35dc-australia-nz-10 region=AUSTRALIA_NZ kind=timeout latencyMs=30072 idempotencyKey=2afc216b-bca9-4ac0-b997-9ec8393f9548 error=AddOrderItemCommand: request expired after 30069ms (timeout=30000)
```

### stack actions

```
2026-09-29T12:46:48.813700800Z docker kill evento-apac
2026-09-29T12:48:29.788607900Z docker start evento-apac
```

