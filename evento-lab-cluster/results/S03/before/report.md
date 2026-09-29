# S03: Kill an evento-server

- Label: `before`
- Gaps exercised: G2, G4, G5, G8, G13
- Started: 2026-09-29T16:44:32.979443200Z, finished: 2026-09-29T16:47:34.603556100Z (181 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=2, AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s03-before-424d3e-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"APAC"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{completeRatio=0.5, itemsPerOrder=1, durationSeconds=110, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **KNOWN_DEFECT_REPRODUCED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | no APAC command acknowledged while evento-apac was down |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **KNOWN_DEFECT_REPRODUCED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 3 gateway command(s) in flight: 3 failed after >= 25 s, 0 failed fast, kinds {timeout=3}; bundle side: 0 BrokerChangedException, 0 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 38 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 25918 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 90 acknowledged ops missing; 1 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 38 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 38 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 25918 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=111 ack=90 failed=21 p50=197ms p95=3875ms p99=30054ms max=30483ms failures={error=18, timeout=3} |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : null,
  "apacAfterKill" : {
    "count" : 18,
    "acked" : 0,
    "failed" : 18,
    "p50" : 7,
    "p95" : 10,
    "p99" : 10,
    "max" : 10,
    "kinds" : {
      "error" : 18
    }
  },
  "otherCellsAfterKill" : {
    "count" : 62,
    "acked" : 62,
    "failed" : 0,
    "p50" : 197,
    "p95" : 1263,
    "p99" : 1333,
    "max" : 1333,
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
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 21,
      "event=supervisor_session_failed" : 8
    },
    "order-query-apac" : {
      "event=bundle_state_transition" : 19,
      "event=supervisor_session_failed" : 9
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
    }
  },
  "apacHandshakes" : 1,
  "apacCorrelationExpired" : 3,
  "apacBundleBrokerChanged" : 0,
  "apacBundleRequestTimeouts" : 0,
  "failedRequestsByError" : {
    "error: bundle not ready: state=CONNECTING" : 2,
    "error: bundle not ready: state=RECONNECTING" : 16,
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 1
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 109,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 3,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 76,
      "APAC/order-query_OrderProjector_1_CINA" : 81,
      "APAC/order-query_OrderProjector_1_INDIA" : 78,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 92,
      "APAC/order-saga_PaymentSaga_1_CINA" : 81,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 78,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 5,
      "EU/order-query_OrderProjector_1_EUROPA" : 7,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 5,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 7
    }
  }
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-09-29T16:45:00.884679900Z onwards)

## Timeline

- +7,9 s: scenario start
- +7,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +28,3 s: killed evento-apac
- +128,7 s: load done: 111 requests
- +128,7 s: restarting evento-apac
- +150,8 s: evento-apac ready=true
- +153,4 s: invariants: converging up to 180 s
- +179,7 s: cleanup start
- +181,6 s: cleanup done

### log excerpts: order-command-apac

```
2026-09-29T16:45:01.048Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.629Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:03.522Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:03.521Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:03.941Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:04.940Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-cina

```
2026-09-29T16:46:58.694Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
2026-09-29T16:45:01.038Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.468Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.796Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.796Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.329Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.375Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-apac

```
2026-09-29T16:45:01.050Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.556Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.970Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.970Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.491Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.539Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-query-global-apac

```
2026-09-29T16:45:01.045Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.580Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.973Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.973Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.537Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.398Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: order-saga-apac

```
2026-09-29T16:45:01.055Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.602Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.962Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.961Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.370Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.332Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
```

### log excerpts: gateway-cina

```
2026-09-29T16:45:01.065Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.651Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:03.538Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:03.538Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:04.119Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:05.307Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:29.111Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T16:45:11.167Z  WARN 1 --- [order-gateway] [mcat-handler-78] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-cina-16 region=CINA kind=error latencyMs=1 idempotencyKey=80f9505a-9905-415a-afa9-118bbf7abb4e error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:26.168Z  WARN 1 --- [order-gateway] [mcat-handler-82] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-cina-23 region=CINA kind=error latencyMs=0 idempotencyKey=718651d7-8620-4117-90be-ff57edaab8bc error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:29.111Z  WARN 1 --- [order-gateway] [mcat-handler-74] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-424d3e-cina-9 region=CINA kind=timeout latencyMs=30050 idempotencyKey=869a4bf9-4ffc-4a55-a267-17c4f3f10bef error=AddOrderItemCommand: request expired after 30047ms (timeout=30000)
```

### log excerpts: gateway-india

```
2026-09-29T16:45:01.063Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.518Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.859Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.858Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.387Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.425Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:30.429Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T16:45:11.610Z  WARN 1 --- [order-gateway] [mcat-handler-77] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-india-19 region=INDIA kind=error latencyMs=1 idempotencyKey=2165f451-066b-4ba9-9a06-e712fd040297 error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:26.612Z  WARN 1 --- [order-gateway] [mcat-handler-81] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-india-26 region=INDIA kind=error latencyMs=1 idempotencyKey=57fa16d5-ebe2-4e96-8d4e-cef4a28836f9 error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:30.429Z  WARN 1 --- [order-gateway] [mcat-handler-74] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s03-before-424d3e-india-12 region=INDIA kind=timeout latencyMs=30018 idempotencyKey=3726e704-cc9c-49db-bbc6-6462cb040f8b error=AddOrderItemCommand: request expired after 30016ms (timeout=30000)
```

### log excerpts: gateway-australia-nz

```
2026-09-29T16:45:01.051Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:01.621Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:45:04.980Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T16:45:04.979Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T16:45:05.509Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:06.654Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac
2026-09-29T16:45:27.115Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T16:45:11.640Z  WARN 1 --- [order-gateway] [mcat-handler-75] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-australia-nz-20 region=AUSTRALIA_NZ kind=error latencyMs=1 idempotencyKey=d4c88c91-1bd5-4586-8234-57a3037af48d error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:26.642Z  WARN 1 --- [order-gateway] [mcat-handler-79] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-australia-nz-27 region=AUSTRALIA_NZ kind=error latencyMs=0 idempotencyKey=c9c7f096-30f1-4bca-9cee-1d32501192f8 error=bundle not ready: state=RECONNECTING
2026-09-29T16:45:27.115Z  WARN 1 --- [order-gateway] [mcat-handler-71] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-before-424d3e-australia-nz-13 region=AUSTRALIA_NZ kind=timeout latencyMs=30478 idempotencyKey=f5b66a3d-3c15-4582-9866-7b84717d3d0d error=CreateOrderCommand: request expired after 30475ms (timeout=30000)
```

### stack actions

```
2026-09-29T16:45:00.884679900Z docker kill evento-apac
2026-09-29T16:46:41.725135Z docker start evento-apac
```

