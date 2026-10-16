# S03: Kill an evento-server

- Label: `after-versioning-rate60`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-10-16T01:13:51.355727700Z, finished: 2026-10-16T01:17:52.083749700Z (240 s)
- Verdicts: {AS_EXPECTED=12}

## Parameters

- orderIdPrefix: `s03-after-versioning-rate60-89d132-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"APAC"},{"feature":"failback","available":true,"value":"default window"},{"feature":"idempotencyKeys","available":true,"value":"CommandGateway.sendIdempotent"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{ratePerMinutePerRegion=60.0, completeRatio=0.5, itemsPerOrder=1, durationSeconds=110, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **AS_EXPECTED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | first APAC ack 5.465 s after the kill |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **AS_EXPECTED** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | 8 JVM(s) failed over {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}; still remote after 180 s: {}, all home 55.223 s after evento-apac was ready |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **AS_EXPECTED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 44 gateway command(s) in flight: 0 failed after >= 25 s, 43 failed fast, kinds {broker_changed=43}; bundle side: 3 BrokerChangedException, 10 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 835 acknowledged ops missing; 142 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 510 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 428 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 835 acknowledged ops missing; 142 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 510 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 510 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 428 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1293 ack=835 failed=458 p50=3967ms p95=30326ms p99=30332ms max=30503ms failures={broker_changed=44, error=29, timeout=385} |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : 5.465,
  "apacAfterKill" : {
    "count" : 326,
    "acked" : 71,
    "failed" : 255,
    "p50" : 30077,
    "p95" : 30332,
    "p99" : 30394,
    "max" : 30473,
    "kinds" : {
      "broker_changed" : 1,
      "error" : 17,
      "timeout" : 237
    }
  },
  "otherCellsAfterKill" : {
    "count" : 696,
    "acked" : 536,
    "failed" : 160,
    "p50" : 2095,
    "p95" : 30116,
    "p99" : 30263,
    "max" : 30503,
    "kinds" : {
      "error" : 12,
      "timeout" : 148
    }
  },
  "apacInFlightAtKill" : 44,
  "apacRemoteWhileDown" : {
    "gateway-australia-nz" : "evento-eu",
    "gateway-cina" : "evento-eu",
    "gateway-india" : "evento-eu",
    "order-command-apac" : "evento-eu",
    "order-query-apac" : "evento-eu",
    "order-query-cina" : "evento-eu",
    "order-query-global-apac" : "evento-eu",
    "order-saga-apac" : "evento-eu"
  },
  "apacStillRemoteAfterFailbackWait" : { },
  "apacAllHomeSecondsAfterServerReady" : 55.223,
  "logMarkers" : {
    "order-command-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-query-cina" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-query-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-query-global-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 4,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-saga-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 7,
      "RequestTimeoutException" : 16,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-cina" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 74,
      "event=gateway_command_failed" : 99,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-india" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 71,
      "event=gateway_command_failed" : 100,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-australia-nz" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 76,
      "event=gateway_command_failed" : 99,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "evento-eu" : {
      "Skipping unregister of bundle" : 3,
      "event=idempotent_replay" : 6
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "apacFailbacksCompleted" : 8,
  "apacFailbacksFailed" : 0,
  "apacHandshakes" : 16,
  "apacCorrelationExpired" : 230,
  "apacBundleBrokerChanged" : 3,
  "apacBundleRequestTimeouts" : 10,
  "failedRequestsByError" : {
    "broker_changed: AddOrderItemCommand: outcome indeterminate: sent via broker 'evento-apac', bundle is now attached to 'evento-eu' and the reply cannot follow it (the request may or may not have been applied)" : 16,
    "broker_changed: CompleteOrderCommand: outcome indeterminate: sent via broker 'evento-apac', bundle is now attached to 'evento-eu' and the reply cannot follow it (the request may or may not have been applied)" : 3,
    "broker_changed: CreateOrderCommand: outcome indeterminate: sent via broker 'evento-apac', bundle is now attached to 'evento-eu' and the reply cannot follow it (the request may or may not have been applied)" : 25,
    "error: AddOrderItemCommand: request <uuid> (AddOrderItemCommand) not executed: the caller's deadline passed 0 ms ago (stage before_append)" : 1,
    "error: AddOrderItemCommand: request <uuid> (AddOrderItemCommand) not executed: the caller's deadline passed 1 ms ago (stage before_append)" : 3,
    "error: AddOrderItemCommand: request <uuid> (AddOrderItemCommand) not executed: the caller's deadline passed 23 ms ago (stage before_forward)" : 1,
    "error: CreateOrderCommand: bundle not ready: state=CONNECTING" : 11,
    "error: CreateOrderCommand: bundle not ready: state=HANDSHAKING" : 1,
    "error: CreateOrderCommand: bundle not ready: state=RECONNECTING" : 2,
    "error: CreateOrderCommand: bundle not ready: state=REGISTERING" : 1,
    "error: CreateOrderCommand: request <uuid> (CreateOrderCommand) not executed: the caller's deadline passed 0 ms ago (stage before_append)" : 5,
    "error: CreateOrderCommand: request <uuid> (CreateOrderCommand) not executed: the caller's deadline passed 1 ms ago (stage before_append)" : 4,
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 35,
    "timeout: CompleteOrderCommand: request expired after <n>ms (timeout=30000)" : 7,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 343
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 125,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 127,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 125,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 127,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 6,
      "APAC/order-query_OrderProjector_1_CINA" : 3,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 6,
      "APAC/order-saga_PaymentSaga_1_CINA" : 3,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 12,
      "EU/order-query_OrderProjector_1_EUROPA" : 0,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 12,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 0
    },
    "esHead" : 1117
  },
  "serviceOutcomeReplayed" : 0
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-10-16T01:14:19.442088300Z onwards)

## Timeline

- +8,0 s: scenario start
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @60.0/min for 110 s
- +28,6 s: killed evento-apac
- +152,7 s: load done: 1293 requests
- +153,5 s: restarting evento-apac
- +180,3 s: evento-apac ready=true
- +237,8 s: invariants: converging up to 180 s
- +238,6 s: cleanup start
- +240,7 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-16T01:14:24.152Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1]
2026-10-16T01:17:26.020Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T01:14:19.657Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.128Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.466Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.466Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:17:25.366Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:26.023Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=657
```

### log excerpts: order-query-cina

```
2026-10-16T01:14:22.849Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, revision:1]
2026-10-16T01:17:30.149Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, revision:1]
2026-10-16T01:14:19.642Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.242Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:22.125Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:22.124Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:17:29.498Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:30.153Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=655
```

### log excerpts: order-query-apac

```
2026-10-16T01:14:22.831Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, revision:1]
2026-10-16T01:17:33.251Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, revision:1]
2026-10-16T01:14:19.641Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.241Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:22.108Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:22.108Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:17:32.604Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:33.253Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=649
```

### log excerpts: order-query-global-apac

```
2026-10-16T01:14:24.181Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1]
2026-10-16T01:17:24.837Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T01:14:19.662Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.124Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.461Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.460Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:15:18.365Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-16T01:16:14.875Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30332ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30332ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30389ms (timeout=30000)
2026-10-16T01:17:24.150Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=1
2026-10-16T01:17:24.841Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=691
```

### log excerpts: order-saga-apac

```
2026-10-16T01:14:24.200Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, revision:1]
2026-10-16T01:17:38.793Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, revision:1]
2026-10-16T01:14:19.650Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.198Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.512Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.512Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:14:55.545Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=1 byType={ProcessPaymentCommand=2}
2026-10-16T01:15:13.553Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={EventFetchRequest=1}
2026-10-16T01:15:26.554Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={ProcessPaymentCommand=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30277ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30277ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30290ms (timeout=30000)
2026-10-16T01:17:38.290Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:38.796Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=506
```

### log excerpts: gateway-cina

```
2026-10-16T01:14:24.201Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1]
2026-10-16T01:17:42.766Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T01:14:19.644Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.052Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.399Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.398Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:15:05.275Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=36 byType={AddOrderItemCommand=1, CreateOrderCommand=1}
2026-10-16T01:15:06.275Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=36 byType={CreateOrderCommand=1}
2026-10-16T01:15:07.276Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=36 byType={AddOrderItemCommand=1}
2026-10-16T01:14:20.240Z  WARN 1 --- [order-gateway] [mcat-handler-53] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-cina-146 region=CINA kind=error latencyMs=2 idempotencyKey=a811da1f-cfab-4129-8753-4ab62e9e5830 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:14:21.243Z  WARN 1 --- [order-gateway] [mcat-handler-54] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-cina-152 region=CINA kind=error latencyMs=1 idempotencyKey=95f54c4a-9567-454e-a6e9-3e26e2cf69ef error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:14:22.244Z  WARN 1 --- [order-gateway] [mcat-handler-55] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-cina-160 region=CINA kind=error latencyMs=1 idempotencyKey=c04cc84e-e122-43b9-b548-6b005eb6bf5a error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:17:42.239Z  INFO 1 --- [order-gateway] [ck-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:42.773Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=534
```

### log excerpts: gateway-india

```
2026-10-16T01:14:24.246Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1]
2026-10-16T01:17:42.401Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T01:14:19.646Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.083Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.420Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.420Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:15:05.565Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=37 byType={AddOrderItemCommand=1, CreateOrderCommand=1}
2026-10-16T01:15:06.566Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=37 byType={CreateOrderCommand=1}
2026-10-16T01:15:07.066Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=36 byType={AddOrderItemCommand=1}
2026-10-16T01:14:20.240Z  WARN 1 --- [order-gateway] [mcat-handler-53] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-india-145 region=INDIA kind=error latencyMs=1 idempotencyKey=4d40a2a5-dd40-48f6-be41-13b902014285 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:14:21.242Z  WARN 1 --- [order-gateway] [mcat-handler-54] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-india-153 region=INDIA kind=error latencyMs=1 idempotencyKey=9c395c6b-5979-4e2d-8cee-7463b4856a14 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:14:22.241Z  WARN 1 --- [order-gateway] [mcat-handler-55] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-india-159 region=INDIA kind=error latencyMs=1 idempotencyKey=55283f2b-dcea-488c-9371-febd725ee499 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:17:41.891Z  INFO 1 --- [order-gateway] [k-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:42.405Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=514
```

### log excerpts: gateway-australia-nz

```
2026-10-16T01:14:24.277Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1]
2026-10-16T01:17:37.510Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T01:14:19.650Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:20.082Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T01:14:23.399Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-16T01:14:23.399Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-16T01:15:05.086Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=36 byType={CreateOrderCommand=1}
2026-10-16T01:15:06.087Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=36 byType={CreateOrderCommand=1}
2026-10-16T01:15:06.588Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=35 byType={AddOrderItemCommand=1}
2026-10-16T01:14:19.914Z  WARN 1 --- [order-gateway] [mcat-handler-53] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-australia-nz-143 region=AUSTRALIA_NZ kind=error latencyMs=2 idempotencyKey=5aaf150d-88a5-41bf-9972-2b0c3ab6f5b7 error=CreateOrderCommand: bundle not ready: state=RECONNECTING
2026-10-16T01:14:20.905Z  WARN 1 --- [order-gateway] [mcat-handler-54] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-australia-nz-150 region=AUSTRALIA_NZ kind=error latencyMs=0 idempotencyKey=c7d1ea61-5c6e-4526-96cd-ac7b4ebd1e08 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:14:21.906Z  WARN 1 --- [order-gateway] [mcat-handler-55] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s03-after-versioning-rate60-89d132-australia-nz-157 region=AUSTRALIA_NZ kind=error latencyMs=0 idempotencyKey=a49b8b45-e570-44ca-ba2b-f363bcaf0e18 error=CreateOrderCommand: bundle not ready: state=CONNECTING
2026-10-16T01:17:36.864Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-16T01:17:37.513Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=650
```

### log excerpts: evento-eu

```
20261016011724429 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261016011725622 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261016011738551 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261016011425329 INFO  1 --- [bus-business-29                         ] CommandBrokerHandler                     : event=idempotent_replay payloadType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_CINA:129:ProcessPaymentCommand:0 correlationId=897b4115-e458-4212-bc64-b4242cee0a61 stage=before_lock seq=241
20261016011509343 INFO  1 --- [bus-business-51                         ] CommandBrokerHandler                     : event=idempotent_replay payloadType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_EUROPA:256:ProcessPaymentCommand:0 correlationId=888f4264-a41a-4858-bfa2-43c1e002fbd8 stage=before_lock seq=661
20261016011518707 INFO  1 --- [nioEventLoopGroup-3-15                  ] CommandBrokerHandler                     : event=idempotent_replay payloadType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_AFRICA:296:ProcessPaymentCommand:0 correlationId=7962a170-a99e-435d-bc62-f707053d4697 stage=after_wait seq=763
```

### stack actions

```
2026-10-16T01:14:19.442088300Z docker kill evento-apac
2026-10-16T01:16:24.822430600Z docker start evento-apac
```

