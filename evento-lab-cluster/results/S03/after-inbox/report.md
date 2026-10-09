# S03: Kill an evento-server

- Label: `after-inbox`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-10-09T08:59:19.427898Z, finished: 2026-10-09T09:03:09.308573700Z (229 s)
- Verdicts: {AS_EXPECTED=12}

## Parameters

- orderIdPrefix: `s03-after-inbox-8e7c0e-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"idempotencyKeys","available":true,"value":"CommandGateway.sendIdempotent"},{"feature":"zone","available":true,"value":"APAC"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.5, itemsPerOrder=1, durationSeconds=110}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **AS_EXPECTED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | first APAC ack 10.003 s after the kill |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **AS_EXPECTED** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | 8 JVM(s) failed over {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}; still remote after 180 s: {}, all home 66.86 s after evento-apac was ready |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **AS_EXPECTED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 0 gateway command(s) in flight: 0 failed after >= 25 s, 0 failed fast, kinds {}; bundle side: 3 BrokerChangedException, 1 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 136 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 56 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 370 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 136 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 370 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=136 ack=136 failed=0 p50=496ms p95=1377ms p99=1933ms max=2102ms |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : 10.003,
  "apacAfterKill" : {
    "count" : 43,
    "acked" : 43,
    "failed" : 0,
    "p50" : 472,
    "p95" : 555,
    "p99" : 578,
    "max" : 578,
    "kinds" : { }
  },
  "otherCellsAfterKill" : {
    "count" : 58,
    "acked" : 58,
    "failed" : 0,
    "p50" : 279,
    "p95" : 1020,
    "p99" : 1048,
    "max" : 1048,
    "kinds" : { }
  },
  "apacInFlightAtKill" : 0,
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
  "apacAllHomeSecondsAfterServerReady" : 66.86,
  "logMarkers" : {
    "order-command-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1,
      "event=service_outcome_replayed" : 1
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
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-saga-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-cina" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-india" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "gateway-australia-nz" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "evento-eu" : {
      "Skipping unregister of bundle" : 3,
      "event=idempotent_replay" : 1
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "apacFailbacksCompleted" : 8,
  "apacFailbacksFailed" : 0,
  "apacHandshakes" : 16,
  "apacCorrelationExpired" : 1,
  "apacBundleBrokerChanged" : 3,
  "apacBundleRequestTimeouts" : 1,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 160,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 4,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 14,
      "APAC/order-query_OrderProjector_1_CINA" : 7,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 14,
      "APAC/order-saga_PaymentSaga_1_CINA" : 7,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 10,
      "EU/order-query_OrderProjector_1_EUROPA" : 3,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 10,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 3
    }
  },
  "serviceOutcomeReplayed" : 1
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-10-09T08:59:48.060262900Z onwards)

## Timeline

- +8,6 s: scenario start
- +8,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +29,0 s: killed evento-apac
- +129,6 s: load done: 136 requests
- +130,4 s: restarting evento-apac
- +157,4 s: evento-apac ready=true
- +226,8 s: invariants: converging up to 180 s
- +227,6 s: cleanup start
- +229,9 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-09T08:59:52.823Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:30.551Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.197Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.612Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:51.950Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:51.949Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:29.871Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:30.567Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=696
2026-10-09T09:00:57.965Z  INFO 1 --- [order-command] [    virtual-226] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_INDIA:40:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-09T08:59:48.052719Z
```

### log excerpts: order-query-cina

```
2026-10-09T08:59:52.923Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:28.161Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.210Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.761Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:52.090Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:52.089Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:27.482Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:28.163Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=681
```

### log excerpts: order-query-apac

```
2026-10-09T08:59:51.374Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:36.443Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.212Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.804Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:50.702Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:50.702Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:35.785Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:36.445Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=660
```

### log excerpts: order-query-global-apac

```
2026-10-09T08:59:52.775Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:39.680Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.190Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.760Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:52.090Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:52.090Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:39.010Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:39.684Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=674
```

### log excerpts: order-saga-apac

```
2026-10-09T08:59:51.485Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:39.536Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.205Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.803Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:50.685Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:50.685Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:00:53.933Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={ProcessPaymentCommand=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30221ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30221ms (timeout=30000)
2026-10-09T09:02:39.014Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=1
2026-10-09T09:02:39.538Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=524
```

### log excerpts: gateway-cina

```
2026-10-09T08:59:52.920Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:41.923Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.212Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.804Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:52.136Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:52.136Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:41.413Z  INFO 1 --- [order-gateway] [ck-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:41.927Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=514
```

### log excerpts: gateway-india

```
2026-10-09T08:59:52.965Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:54.802Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.194Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.790Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:52.121Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:52.120Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:54.141Z  INFO 1 --- [order-gateway] [k-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:54.807Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=666
```

### log excerpts: gateway-australia-nz

```
2026-10-09T08:59:52.801Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-10-09T09:02:41.555Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-10-09T08:59:48.220Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:48.739Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-09T08:59:52.073Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-09T08:59:52.073Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-09T09:02:41.019Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-09T09:02:41.559Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=540
```

### log excerpts: evento-eu

```
20261009090227757 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261009090230133 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261009090239279 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261009085952580 INFO  1 --- [bus-business-10                         ] CommandBrokerHandler                     : event=idempotent_replay payloadType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_CINA:38:ProcessPaymentCommand:0 correlationId=bef851c2-5f50-46d1-860d-b05391cec90b stage=before_lock seq=41
```

### stack actions

```
2026-10-09T08:59:48.060262900Z docker kill evento-apac
2026-10-09T09:01:29.792376400Z docker start evento-apac
```

