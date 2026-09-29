# S07: Region partition (APAC off the WAN)

- Label: `after`
- Gaps exercised: G22, G25, G11
- Started: 2026-09-29T14:35:32.429503900Z, finished: 2026-09-29T14:37:52.312865100Z (139 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s07-after-b78d29-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.6, itemsPerOrder=1, durationSeconds=120}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 42; failures {} |
| global consumer lanes during the partition | **KNOWN_DEFECT_REPRODUCED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=10, EU/order-query_OrderProjector_1_*=10, consumers/order-query_CatalogProjector_1_ALL=55} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 193 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 107 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 193 ms |
| I7 | catalog matches live bundles | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 7 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=119 ack=107 failed=12 p50=470ms p95=4003ms p99=44999ms max=45001ms failures={client_timeout=2, http_504=10} |

<details><summary>I7: 7 violation(s)</summary>

- no core__consumer row for order-query_OrderProjector_1_CINA*
- no core__consumer row for order-query_OrderProjector_1_INDIA*
- no core__consumer row for order-query_OrderProjector_1_AUSTRALIA_NZ*
- no core__consumer row for order-query_OrderProjector_1_**
- no core__consumer row for order-saga_PaymentSaga_1_CINA*
- no core__consumer row for order-saga_PaymentSaga_1_INDIA*
- no core__consumer row for order-saga_PaymentSaga_1_AUSTRALIA_NZ*

</details>

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 42,
    "acked" : 42,
    "failed" : 0,
    "p50" : 179,
    "p95" : 841,
    "p99" : 864,
    "max" : 864,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4002,
    "p95" : 45001,
    "p99" : 45001,
    "max" : 45001,
    "kinds" : {
      "client_timeout" : 2,
      "http_504" : 10
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 10,
    "EU/order-query_OrderProjector_1_*" : 10,
    "consumers/order-query_CatalogProjector_1_ALL" : 55
  },
  "logMarkers" : {
    "order-command-apac" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7
    },
    "order-query-cina" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7,
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 4
    },
    "order-query-apac" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7,
      "event=bundle_correlation_expired" : 4,
      "RequestTimeoutException" : 8
    },
    "order-query-global-apac" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 26,
      "event=supervisor_session_failed" : 9
    },
    "order-saga-apac" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7,
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 12
    },
    "gateway-cina" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7
    },
    "gateway-india" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7
    },
    "gateway-australia-nz" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7
    }
  },
  "apacSupervisorSessionFailures" : 58,
  "failedRequestsByError" : {
    "client_timeout: java.net.http.HttpTimeoutException: request timed out" : 2,
    "http_504: <html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n<hr><center>nginx/1.31.3</center>\r\n</body>\r\n</html>\r\n" : 10
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 4171,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 9,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 9,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 11,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 11,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12,
      "consumers/order-query_CatalogProjector_1_ALL" : 3581
    }
  }
}
```

## Timeline

- +5,9 s: scenario start
- +5,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +26,5 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +90,2 s: partition healed
- +126,9 s: load done: 119 requests
- +137,6 s: invariants: converging up to 240 s
- +138,0 s: cleanup start
- +139,9 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-09-29T14:35:38.341307600Z, EU/order-observer_NotificationObserver_1_*=4045, EU/order-query_OrderProjector_1_*=4045, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:35:43.633735400Z, EU/order-observer_NotificationObserver_1_*=4065, EU/order-query_OrderProjector_1_*=4065, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:35:48.710126Z, EU/order-observer_NotificationObserver_1_*=4065, EU/order-query_OrderProjector_1_*=4065, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:35:53.780376300Z, EU/order-observer_NotificationObserver_1_*=4065, EU/order-query_OrderProjector_1_*=4065, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:35:58.898519700Z, EU/order-observer_NotificationObserver_1_*=4083, EU/order-query_OrderProjector_1_*=4083, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:03.979808600Z, EU/order-observer_NotificationObserver_1_*=4083, EU/order-query_OrderProjector_1_*=4083, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:09.053673100Z, EU/order-observer_NotificationObserver_1_*=4083, EU/order-query_OrderProjector_1_*=4083, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:14.140158200Z, EU/order-observer_NotificationObserver_1_*=4097, EU/order-query_OrderProjector_1_*=4097, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:19.215208Z, EU/order-observer_NotificationObserver_1_*=4097, EU/order-query_OrderProjector_1_*=4097, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:24.283477Z, EU/order-observer_NotificationObserver_1_*=4097, EU/order-query_OrderProjector_1_*=4097, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:29.366597100Z, EU/order-observer_NotificationObserver_1_*=4111, EU/order-query_OrderProjector_1_*=4111, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:34.436081100Z, EU/order-observer_NotificationObserver_1_*=4111, EU/order-query_OrderProjector_1_*=4111, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:39.508485300Z, EU/order-observer_NotificationObserver_1_*=4114, EU/order-query_OrderProjector_1_*=4116, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:44.592197500Z, EU/order-observer_NotificationObserver_1_*=4121, EU/order-query_OrderProjector_1_*=4121, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:49.661006800Z, EU/order-observer_NotificationObserver_1_*=4121, EU/order-query_OrderProjector_1_*=4121, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:54.770152600Z, EU/order-observer_NotificationObserver_1_*=4126, EU/order-query_OrderProjector_1_*=4125, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:36:59.848309100Z, EU/order-observer_NotificationObserver_1_*=4135, EU/order-query_OrderProjector_1_*=4135, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:04.919785300Z, EU/order-observer_NotificationObserver_1_*=4135, EU/order-query_OrderProjector_1_*=4135, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:10.007362100Z, EU/order-observer_NotificationObserver_1_*=4142, EU/order-query_OrderProjector_1_*=4140, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:15.074454600Z, EU/order-observer_NotificationObserver_1_*=4152, EU/order-query_OrderProjector_1_*=4152, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:20.139659Z, EU/order-observer_NotificationObserver_1_*=4153, EU/order-query_OrderProjector_1_*=4153, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:25.208551300Z, EU/order-observer_NotificationObserver_1_*=4164, EU/order-query_OrderProjector_1_*=4159, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:30.279001800Z, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:35.351307900Z, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:40.452788600Z, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171, consumers/order-query_CatalogProjector_1_ALL=590}
{t=2026-09-29T14:37:45.517786200Z, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171, consumers/order-query_CatalogProjector_1_ALL=590}
```

### log excerpts: order-command-apac

```
2026-09-29T14:37:03.695Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:39.954Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:40.386Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:40.390Z  INFO 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:45.394Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:47.771Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:48.260Z  WARN 1 --- [order-command] [er-command-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
```

### log excerpts: order-query-cina

```
2026-09-29T14:37:05.042Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:42.672Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:43.167Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:43.171Z  INFO 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:48.177Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:48.649Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
2026-09-29T14:36:49.091Z  WARN 1 --- [order-query] [rder-query-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
2026-09-29T14:36:29.125Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-09-29T14:37:00.132Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30462ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30462ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30005ms (timeout=30000)
```

### log excerpts: order-query-apac

```
2026-09-29T14:37:07.711Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:43.497Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:44.099Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:44.102Z  INFO 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:49.107Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:49.690Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
2026-09-29T14:36:52.125Z  WARN 1 --- [order-query] [rder-query-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:28.677Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-09-29T14:36:29.678Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-09-29T14:36:59.690Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30095ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30095ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30213ms (timeout=30000)
```

### log excerpts: order-query-global-apac

```
2026-09-29T14:37:11.097Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=evento-apac:3030 accepted_caps=[]
2026-09-29T14:36:42.727Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:43.256Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:43.260Z  INFO 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:48.265Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:48.829Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
2026-09-29T14:36:49.428Z  WARN 1 --- [order-query] [ery-global-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
```

### log excerpts: order-saga-apac

```
2026-09-29T14:37:08.657Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:42.795Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:43.310Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:43.313Z  INFO 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:48.318Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:52.114Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:52.648Z  WARN 1 --- [order-saga] [order-saga-apac] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
2026-09-29T14:36:28.903Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=3 pending=0 byType={EventFetchRequest=3}
2026-09-29T14:36:59.909Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=3 pending=0 byType={EventFetchRequest=3}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30109ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30109ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30304ms (timeout=30000)
```

### log excerpts: gateway-cina

```
2026-09-29T14:37:05.305Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:39.553Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:40.133Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:40.137Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:45.140Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:48.867Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:49.327Z  WARN 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
```

### log excerpts: gateway-india

```
2026-09-29T14:37:03.942Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:39.716Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:40.265Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:40.270Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:45.274Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:47.646Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:48.198Z  WARN 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
```

### log excerpts: gateway-australia-nz

```
2026-09-29T14:37:04.653Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-eu cluster=evento-lab-cluster endpoint=toxi-apac:20313 accepted_caps=[]
2026-09-29T14:36:40.200Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:36:40.612Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:36:40.616Z  INFO 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:36:45.618Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=evento-apac:3030 cause=java.util.concurrent.TimeoutException
2026-09-29T14:36:47.881Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=toxi-apac:20313 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac: Name or service not known
2026-09-29T14:36:48.449Z  WARN 1 --- [order-gateway] [ay-australia-nz] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=toxi-apac:20323 cause=java.util.concurrent.ExecutionException: java.net.UnknownHostException: toxi-apac
```

### stack actions

```
2026-09-29T14:35:58.454108500Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-09-29T14:35:58.726422600Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-09-29T14:36:58.961844200Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-09-29T14:36:59.144735700Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-09-29T14:36:59.285742300Z docker restart lb
```

