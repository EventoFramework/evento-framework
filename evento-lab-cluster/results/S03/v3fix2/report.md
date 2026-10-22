# S03: Kill an evento-server

- Label: `v3fix2`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-10-20T18:08:49.044858100Z, finished: 2026-10-20T18:12:31.256927400Z (222 s)
- Verdicts: {AS_EXPECTED=11, INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s03-v3fix2-e06326-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- gateway-cina whoami: `{"bundleId":"order-gateway","instance":"gateway-cina","zone":"APAC","cell":"APAC","region":"CINA","contexts":null,"consumerPlan":{},"server":"evento-apac:3030","addresses":["evento-apac:3030","toxi-apac:20313","toxi-apac:20323"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":null,"consumerStateDb":null,"dataTargets":["eu","am","apac"],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"APAC"},{"feature":"failback","available":true,"value":"default window"},{"feature":"idempotencyKeys","available":true,"value":"CommandGateway.sendIdempotent"}],"busState":"READY","ready":true}`
- busMode: `direct address list`
- load: `{ratePerMinutePerRegion=4.0, completeRatio=0.5, itemsPerOrder=1, durationSeconds=110, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| APAC bundles fail over to a healthy server | **AS_EXPECTED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | first APAC ack 10.059 s after the kill |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **AS_EXPECTED** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | 8 JVM(s) failed over {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}; still remote after 180 s: {}, all home 54.924 s after evento-apac was ready |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **INCONCLUSIVE** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 0 gateway command(s) in flight: 0 failed after >= 25 s, 0 failed fast, kinds {}; bundle side: 0 BrokerChangedException, 0 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 138 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 56 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 367 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 138 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 367 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=138 ack=138 failed=0 p50=413ms p95=865ms p99=1612ms max=1743ms |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : 10.059,
  "apacAfterKill" : {
    "count" : 41,
    "acked" : 41,
    "failed" : 0,
    "p50" : 423,
    "p95" : 613,
    "p99" : 704,
    "max" : 704,
    "kinds" : { }
  },
  "otherCellsAfterKill" : {
    "count" : 62,
    "acked" : 62,
    "failed" : 0,
    "p50" : 286,
    "p95" : 650,
    "p99" : 690,
    "max" : 690,
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
  "apacAllHomeSecondsAfterServerReady" : 54.924,
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
  "apacBundleBrokerChanged" : 0,
  "apacBundleRequestTimeouts" : 0,
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 1,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 7,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 7,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 15,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 14,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 15,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 14,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 18,
      "EU/order-query_OrderProjector_1_EUROPA" : 2,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 18,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 2
    },
    "esHead" : 164
  },
  "serviceOutcomeReplayed" : 0
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-10-20T18:09:16.806015100Z onwards)

## Timeline

- +7,7 s: scenario start
- +7,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +28,2 s: killed evento-apac
- +128,7 s: load done: 138 requests
- +129,4 s: restarting evento-apac
- +162,3 s: evento-apac ready=true
- +219,5 s: invariants: converging up to 180 s
- +220,1 s: cleanup start
- +222,2 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-20T18:09:19.991Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, revision:1, events:1]
2026-10-20T18:12:03.390Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, revision:1, events:1]
2026-10-20T18:09:16.954Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.546Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:19.425Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:19.425Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:03.079Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:03.394Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=315
```

### log excerpts: order-query-cina

```
2026-10-20T18:09:21.326Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:12:03.841Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:09:16.961Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.451Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:20.759Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:20.759Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:03.563Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:03.844Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=281
```

### log excerpts: order-query-apac

```
2026-10-20T18:09:21.500Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, events:1, stream:1]
2026-10-20T18:12:09.970Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, events:1, stream:1]
2026-10-20T18:09:16.974Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.480Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:20.773Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:20.773Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:09.703Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:09.972Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=269
```

### log excerpts: order-query-global-apac

```
2026-10-20T18:09:21.411Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:12:10.640Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:09:16.966Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.500Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:20.812Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:20.812Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:10.374Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:10.647Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=273
```

### log excerpts: order-saga-apac

```
2026-10-20T18:09:20.181Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:12:09.210Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:09:16.960Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.558Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:19.425Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:19.425Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:08.944Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:09.212Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=268
```

### log excerpts: gateway-cina

```
2026-10-20T18:09:20.075Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:12:25.784Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:09:16.959Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.521Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:19.392Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:19.392Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:25.396Z  INFO 1 --- [order-gateway] [ck-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:25.788Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=392
```

### log excerpts: gateway-india

```
2026-10-20T18:09:21.338Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:12:24.873Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, events:1, revision:1]
2026-10-20T18:09:16.964Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.467Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:20.762Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:20.761Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:24.584Z  INFO 1 --- [order-gateway] [k-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:24.877Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=293
```

### log excerpts: gateway-australia-nz

```
2026-10-20T18:09:21.290Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[events:1, stream:1, revision:1]
2026-10-20T18:12:14.217Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[events:1, stream:1, revision:1]
2026-10-20T18:09:16.963Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:17.403Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-20T18:09:20.709Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-20T18:09:20.709Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-20T18:12:13.933Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-20T18:12:14.221Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=289
```

### log excerpts: evento-eu

```
20261020181203347 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020181203827 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020181209204 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-10-20T18:09:16.806015100Z docker kill evento-apac
2026-10-20T18:10:58.398704200Z docker start evento-apac
```

