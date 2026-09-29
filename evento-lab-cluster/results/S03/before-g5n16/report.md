# S03: Kill an evento-server

- Label: `before-g5n16`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-09-29T21:32:22.162177300Z, finished: 2026-09-29T21:35:22.294664800Z (180 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=2, INCONCLUSIVE=1, AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s03-before-g5n16-74696a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"idempotencyKeys","available":false,"value":"CommandGateway.sendIdempotent"},{"feature":"zone","available":false,"value":"APAC"},{"feature":"failback","available":false,"value":"default window"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{completeRatio=0.5, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=110, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **KNOWN_DEFECT_REPRODUCED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | no APAC command acknowledged while evento-apac was down |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **INCONCLUSIVE** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | no APAC JVM was on another server while evento-apac was down |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **KNOWN_DEFECT_REPRODUCED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 3 gateway command(s) in flight: 3 failed after >= 25 s, 0 failed fast, kinds {timeout=3}; bundle side: 0 BrokerChangedException, 1 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 38 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 22631 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed (G5) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: Residual window, documented: a payment whose event could not be stored (append failed or the server died first) leaves no key behind, so its retry runs the handler again.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 38 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 38 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 22631 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=111 ack=90 failed=21 p50=611ms p95=3847ms p99=30400ms max=30489ms failures={error=18, timeout=3} |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : null,
  "apacAfterKill" : {
    "count" : 18,
    "acked" : 0,
    "failed" : 18,
    "p50" : 7,
    "p95" : 15,
    "p99" : 15,
    "max" : 15,
    "kinds" : {
      "error" : 18
    }
  },
  "otherCellsAfterKill" : {
    "count" : 60,
    "acked" : 60,
    "failed" : 0,
    "p50" : 641,
    "p95" : 1580,
    "p99" : 1700,
    "max" : 1700,
    "kinds" : { }
  },
  "apacInFlightAtKill" : 3,
  "apacRemoteWhileDown" : { },
  "apacStillRemoteAfterFailbackWait" : { },
  "apacAllHomeSecondsAfterServerReady" : 1.737,
  "logMarkers" : {
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
      "RequestTimeoutException" : 2
    },
    "order-query-global-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
    },
    "order-saga-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
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
    },
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "apacFailbacksCompleted" : 0,
  "apacFailbacksFailed" : 0,
  "apacHandshakes" : 0,
  "apacCorrelationExpired" : 4,
  "apacBundleBrokerChanged" : 0,
  "apacBundleRequestTimeouts" : 1,
  "failedRequestsByError" : {
    "error: bundle not ready: state=CONNECTING" : 2,
    "error: bundle not ready: state=RECONNECTING" : 16,
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 1
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 76,
      "APAC/order-query_OrderProjector_1_CINA" : 79,
      "APAC/order-query_OrderProjector_1_INDIA" : 74,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 76,
      "APAC/order-saga_PaymentSaga_1_CINA" : 79,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 74,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 5,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 5,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    },
    "esHead" : 109
  }
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-09-29T21:32:49.966442600Z onwards)

## Timeline

- +7,8 s: scenario start
- +7,8 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +28,3 s: killed evento-apac
- +128,6 s: load done: 111 requests
- +129,3 s: restarting evento-apac
- +151,3 s: evento-apac ready=true
- +155,2 s: invariants: converging up to 180 s
- +178,1 s: cleanup start
- +180,1 s: cleanup done

### log excerpts: order-command-apac

```
2026-09-29T21:32:50.142Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.582Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.918Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.918Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.334Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.236Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-cina

```
2026-09-29T21:32:50.143Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.699Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:52.573Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:52.572Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:53.130Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:54.239Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-apac

```
2026-09-29T21:32:50.120Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.639Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.954Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.954Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.484Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.337Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:33:20.277Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30352ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30352ms (timeout=30000)
```

### log excerpts: order-query-global-apac

```
2026-09-29T21:32:50.164Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.578Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.916Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.916Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.402Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.521Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-saga-apac

```
2026-09-29T21:32:50.142Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.731Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:52.598Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:52.597Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:53.108Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:54.269Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: gateway-cina

```
2026-09-29T21:32:50.176Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.589Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.925Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.925Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.527Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.486Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:33:18.548Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T21:33:00.231Z  WARN 1 --- [order-gateway] [mcat-handler-75] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-cina-17 region=CINA kind=error latencyMs=2 idempotencyKey=6de17818-a3f9-4773-a8ad-120e4cdbac03 error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:15.229Z  WARN 1 --- [order-gateway] [mcat-handler-79] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-cina-24 region=CINA kind=error latencyMs=1 idempotencyKey=a1ed74db-2086-4bf7-8bfd-70f1f7be7ee8 error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:18.548Z  WARN 1 --- [order-gateway] [mcat-handler-72] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-g5n16-74696a-cina-10 region=CINA kind=timeout latencyMs=30485 idempotencyKey=7f58c8a4-639e-45aa-a503-b46e3b379539 error=AddOrderItemCommand: request expired after 30481ms (timeout=30000)
```

### log excerpts: gateway-india

```
2026-09-29T21:32:50.122Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.583Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.917Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.917Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.368Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.516Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:33:15.839Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T21:33:00.447Z  WARN 1 --- [order-gateway] [mcat-handler-73] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-india-19 region=INDIA kind=error latencyMs=1 idempotencyKey=ea843bb3-13e9-420e-939a-830116c9dd4c error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:15.446Z  WARN 1 --- [order-gateway] [mcat-handler-77] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-india-26 region=INDIA kind=error latencyMs=1 idempotencyKey=0dac2ccc-0578-477b-a22a-8dd465377acb error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:15.839Z  WARN 1 --- [order-gateway] [mcat-handler-69] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-india-12 region=INDIA kind=timeout latencyMs=30396 idempotencyKey=5370cd11-9b85-453f-b7d9-3d1eeedd1525 error=CreateOrderCommand: request expired after 30392ms (timeout=30000)
```

### log excerpts: gateway-australia-nz

```
2026-09-29T21:32:50.171Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:50.658Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:32:53.978Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T21:32:53.978Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T21:32:54.536Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:32:55.568Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T21:33:19.785Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T21:33:00.196Z  WARN 1 --- [order-gateway] [mcat-handler-74] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-australia-nz-15 region=AUSTRALIA_NZ kind=error latencyMs=2 idempotencyKey=5a5ef056-adf3-44c7-8e84-8f892f6009be error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:15.195Z  WARN 1 --- [order-gateway] [mcat-handler-78] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-g5n16-74696a-australia-nz-22 region=AUSTRALIA_NZ kind=error latencyMs=0 idempotencyKey=b786dd97-d899-4667-8888-1559d3a4de51 error=bundle not ready: state=RECONNECTING
2026-09-29T21:33:19.785Z  WARN 1 --- [order-gateway] [mcat-handler-71] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-g5n16-74696a-australia-nz-8 region=AUSTRALIA_NZ kind=timeout latencyMs=30298 idempotencyKey=089bb8b3-8ced-4591-ac4d-e6ac3b5347ab error=AddOrderItemCommand: request expired after 30295ms (timeout=30000)
```

### stack actions

```
2026-09-29T21:32:49.966442600Z docker kill evento-apac
2026-09-29T21:34:31.432288500Z docker start evento-apac
```

