# S03: Kill an evento-server

- Label: `v3e`
- Gaps exercised: G2, G4, G5, G8, G13, N16
- Started: 2026-10-22T15:53:09.487430300Z, finished: 2026-10-22T15:56:45.236912600Z (215 s)
- Verdicts: {AS_EXPECTED=11, INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s03-v3e-40efdb-`
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
| APAC bundles fail over to a healthy server | **AS_EXPECTED** | APAC commands acknowledged again within 60 s of the kill, through another server | stranded: the bundle redials only the first (dead) address (G2) | first APAC ack 10.737 s after the kill |
| APAC bundles fail back to evento-apac once it is healthy again (N16) | **AS_EXPECTED** | every APAC JVM that failed over is attached to evento-apac again, without a restart, within the stability window + jitter (+ one window for gateways) | no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a restart (N16); 2.4.6 never failed over, so there is nothing to fail back | 8 JVM(s) failed over {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}; still remote after 180 s: {}, all home 54.952 s after evento-apac was ready |
| catalog registration under mass reconnect | **AS_EXPECTED** | no duplicate-key errors while bundles re-register (G8 fix) | 'duplicate key' on core__bundle/component/payload during the reconnect storm (G8) | 0 duplicate-key log lines on the servers |
| in-flight APAC calls at the kill | **INCONCLUSIVE** | fail immediately with a typed broker-changed/disconnect error (G4 fix) | hang until the 30 s request timeout, outcome indeterminate (G4) | 0 gateway command(s) in flight: 0 failed after >= 25 s, 0 failed fast, kinds {}; bundle side: 0 BrokerChangedException, 0 RequestTimeoutException |
| I1 after the server returns | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 137 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 after the server returns | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I4 after the server returns | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 56 |
| I5 after the server returns | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 after the server returns | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 310 ms |
| I7 after the server returns | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 137 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 56 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 56 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 310 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=137 ack=137 failed=0 p50=417ms p95=692ms p99=943ms max=1192ms |

## Metrics

```json
{
  "apacFirstAckAfterKillSeconds" : 10.737,
  "apacAfterKill" : {
    "count" : 46,
    "acked" : 46,
    "failed" : 0,
    "p50" : 469,
    "p95" : 594,
    "p99" : 692,
    "max" : 692,
    "kinds" : { }
  },
  "otherCellsAfterKill" : {
    "count" : 58,
    "acked" : 58,
    "failed" : 0,
    "p50" : 278,
    "p95" : 631,
    "p99" : 651,
    "max" : 651,
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
  "apacAllHomeSecondsAfterServerReady" : 54.952,
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
      "Skipping unregister of bundle" : 1
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
    "esHead" : 742,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 8,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 8,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 7,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 7,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 16,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 16
    }
  },
  "serviceOutcomeReplayed" : 0
}
```

## Notes

- bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill until the invariant check (2026-10-22T15:53:37.375226800Z onwards)

## Timeline

- +7,8 s: scenario start
- +7,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 110 s
- +28,4 s: killed evento-apac
- +128,8 s: load done: 137 requests
- +129,5 s: restarting evento-apac
- +155,8 s: evento-apac ready=true
- +213,0 s: invariants: converging up to 180 s
- +213,6 s: cleanup start
- +215,7 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-22T15:53:40.638Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:56:25.456Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:53:37.597Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.122Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:39.983Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:39.983Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:25.166Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:25.459Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=293
```

### log excerpts: order-query-cina

```
2026-10-22T15:53:40.576Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[stream:1, revision:1, head:1, events:1]
2026-10-22T15:56:20.251Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[stream:1, revision:1, head:1, events:1]
2026-10-22T15:53:37.581Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.154Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:40.014Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:40.013Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:19.942Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:20.253Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=311
```

### log excerpts: order-query-apac

```
2026-10-22T15:53:42.076Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:56:34.153Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:53:37.600Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.081Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:41.390Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:41.390Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:33.872Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:34.155Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=283
```

### log excerpts: order-query-global-apac

```
2026-10-22T15:53:42.020Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:56:21.907Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:53:37.604Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.083Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:41.389Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:41.389Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:21.639Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:21.911Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=272
```

### log excerpts: order-saga-apac

```
2026-10-22T15:53:42.070Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:56:34.307Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:53:37.592Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.024Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:41.327Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:41.326Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:34.028Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:34.309Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=281
```

### log excerpts: gateway-cina

```
2026-10-22T15:53:42.118Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:56:36.509Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:53:37.604Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.077Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:41.389Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:41.389Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:36.223Z  INFO 1 --- [order-gateway] [ck-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:36.514Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=291
```

### log excerpts: gateway-india

```
2026-10-22T15:53:40.634Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:56:32.475Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[head:1, events:1, stream:1, revision:1]
2026-10-22T15:53:37.595Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.160Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:40.031Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:40.031Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:32.186Z  INFO 1 --- [order-gateway] [k-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:32.478Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=292
```

### log excerpts: gateway-australia-nz

```
2026-10-22T15:53:41.939Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:56:39.139Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[revision:1, stream:1, events:1, head:1]
2026-10-22T15:53:37.598Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:38.013Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T15:53:41.328Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=RECONNECTING reason=post_session force=true
2026-10-22T15:53:41.328Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: evento-apac: Name or service not known
2026-10-22T15:56:38.854Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_started from=toxi-apac:20313 from_instance=evento-eu to=evento-apac:3030 drain_timeout_ms=10000 inbound_in_flight=0 outbound_pending=0
2026-10-22T15:56:39.145Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=failback_completed from=toxi-apac:20313 to=evento-apac:3030 server_instance=evento-apac took_ms=291
```

### log excerpts: evento-eu

```
20261022155329825 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-22T15:53:37.375226800Z docker kill evento-apac
2026-10-22T15:55:18.955674Z docker start evento-apac
```

