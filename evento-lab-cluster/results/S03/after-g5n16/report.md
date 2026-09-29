# S03: Kill an evento-server

- Label: `after-g5n16`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-09-29T20:23:40.499669800Z, finished: 2026-09-29T20:27:18.816115400Z (218 s)
- Verdicts: {AS_EXPECTED=12}

## Parameters

- orderIdPrefix: `s03-after-g5n16-4a0a2d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"APAC"},{"feature":"failback","available":true,"value":"default window"},{"feature":"idempotencyKeys","available":true,"value":"CommandGateway.sendIdempotent"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{itemsPerOrder=1, durationSeconds=110, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **AS_EXPECTED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | first APAC ack 10.859 s after the kill |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **AS_EXPECTED** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | 8 JVM(s) failed over {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}; still remote after 180 s: {}, all home 55.713 s after evento-apac was ready |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **AS_EXPECTED** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 0 gateway command(s) in flight: 0 failed after >= 25 s, 0 failed fast, kinds {}; bundle side: 3 BrokerChangedException, 0 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 134 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 56 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 377 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed (G5) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: Residual window, documented: a payment whose event could not be stored (append failed or the server died first) leaves no key behind, so its retry runs the handler again.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 134 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 377 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=134 ack=134 failed=0 p50=519ms p95=1851ms p99=2716ms max=2796ms |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : 10.859,
  "apacAfterKill" : {
    "count" : 46,
    "acked" : 46,
    "failed" : 0,
    "p50" : 449,
    "p95" : 641,
    "p99" : 686,
    "max" : 686,
    "kinds" : { }
  },
  "otherCellsAfterKill" : {
    "count" : 57,
    "acked" : 57,
    "failed" : 0,
    "p50" : 673,
    "p95" : 1319,
    "p99" : 1388,
    "max" : 1388,
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
  "apacAllHomeSecondsAfterServerReady" : 55.713,
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
      "event=failback_started" : 1,
      "event=failback_completed" : 1
    },
    "order-saga-apac" : {
      "event=handshake_complete" : 2,
      "event=bundle_state_transition" : 12,
      "event=supervisor_session_failed" : 1,
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
      "Skipping unregister of bundle" : 3
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "apacFailbacksCompleted" : 8,
  "apacFailbacksFailed" : 0,
  "apacHandshakes" : 16,
  "apacCorrelationExpired" : 0,
  "apacBundleBrokerChanged" : 3,
  "apacBundleRequestTimeouts" : 0,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 156,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 0,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 9,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 7,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 7,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 20,
      "EU/order-query_OrderProjector_1_EUROPA" : 8,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 20,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 8
    }
  }
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-09-29T20:24:09.264856700Z onwards)

## Timeline

- +8,7 s: scenario start
- +8,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +29,2 s: killed evento-apac
- +129,7 s: load done: 134 requests
- +130,4 s: restarting evento-apac
- +157,1 s: evento-apac ready=true
- +215,3 s: invariants: converging up to 180 s
- +216,1 s: cleanup start
- +218,3 s: cleanup done

### log excerpts: order-command-apac

```
2026-09-29T20:24:12.558Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:26:53.808Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.438Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.994Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:11.895Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:11.894Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:26:53.138Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:26:53.812Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=674
```

### log excerpts: order-query-cina

```
2026-09-29T20:24:13.982Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:26:57.035Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.432Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.893Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:13.231Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:13.231Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:26:56.534Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=1
2026-09-29T20:26:57.037Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=503
```

### log excerpts: order-query-apac

```
2026-09-29T20:24:14.029Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:26:52.693Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.438Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.940Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:13.263Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:13.262Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:26:52.019Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:26:52.695Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=676
```

### log excerpts: order-query-global-apac

```
2026-09-29T20:24:13.938Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:26:56.149Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.427Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.862Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:13.187Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:13.186Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:26:55.538Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:26:56.152Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=614
```

### log excerpts: order-saga-apac

```
2026-09-29T20:24:12.715Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:26:58.723Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.417Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:10.006Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:11.899Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:11.898Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:26:58.207Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:26:58.726Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=519
```

### log excerpts: gateway-cina

```
2026-09-29T20:24:13.822Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:27:05.512Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.431Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.856Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:13.185Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:13.185Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:27:04.856Z  INFO 1 --- [order-gateway] [ck-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:27:05.515Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=659
```

### log excerpts: gateway-india

```
2026-09-29T20:24:12.556Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:27:09.558Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.417Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.940Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:11.838Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:11.837Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:27:08.907Z  INFO 1 --- [order-gateway] [k-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:27:09.561Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=654
```

### log excerpts: gateway-australia-nz

```
2026-09-29T20:24:13.893Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T20:27:06.000Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T20:24:09.431Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:09.888Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:24:13.215Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-09-29T20:24:13.215Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-09-29T20:27:05.490Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-09-29T20:27:06.004Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=514
```

### log excerpts: evento-eu

```
20260929202652291 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20260929202653402 INFO  1 --- [nioEventLoopGroup-3-1                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20260929202658473 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-09-29T20:24:09.264856700Z docker kill evento-apac
2026-09-29T20:25:50.898223700Z docker start evento-apac
```

