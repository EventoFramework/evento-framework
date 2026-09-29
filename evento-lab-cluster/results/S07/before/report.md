# S07: Region partition (APAC off the WAN)

- Label: `before`
- Gaps exercised: G22, G25, G11
- Started: 2026-09-29T13:24:10.910681600Z, finished: 2026-09-29T13:26:45.561281200Z (154 s)
- Verdicts: {UNEXPECTED=1, KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s07-before-ceec1a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **UNEXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 0,0% of 16; failures {client_error=5, error=1, timeout=10} |
| global consumer lanes during the partition | **KNOWN_DEFECT_REPRODUCED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=50, EU/order-query_OrderProjector_1_*=50} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 188 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 28 acknowledged ops missing; 21 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 33 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 33 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 188 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=73 ack=28 failed=45 p50=3266ms p95=44999ms p99=45001ms max=45001ms failures={client_error=12, client_timeout=5, error=2, http_504=12, timeout=14} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 16,
    "acked" : 0,
    "failed" : 16,
    "p50" : 30197,
    "p95" : 30427,
    "p99" : 30427,
    "max" : 30427,
    "kinds" : {
      "client_error" : 5,
      "error" : 1,
      "timeout" : 10
    }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4001,
    "p95" : 4005,
    "p99" : 4005,
    "max" : 4005,
    "kinds" : {
      "http_504" : 12
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 50,
    "EU/order-query_OrderProjector_1_*" : 50
  },
  "logMarkers" : {
    "order-command-apac" : { },
    "order-query-cina" : {
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2
    },
    "order-query-apac" : {
      "event=bundle_correlation_expired" : 4,
      "RequestTimeoutException" : 8
    },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 4,
      "RequestTimeoutException" : 8
    },
    "gateway-cina" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 2,
      "event=bundle_correlation_expired" : 3,
      "event=gateway_command_failed" : 3
    },
    "gateway-india" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 4,
      "event=bundle_correlation_expired" : 2,
      "event=gateway_command_failed" : 3
    },
    "gateway-australia-nz" : {
      "event=bundle_correlation_expired" : 3,
      "event=gateway_command_failed" : 3
    }
  },
  "apacSupervisorSessionFailures" : 0,
  "failedRequestsByError" : {
    "client_error: java.io.IOException: HTTP/1.1 header parser received no bytes" : 12,
    "client_timeout: java.net.http.HttpTimeoutException: request timed out" : 5,
    "error: Failed to acquire advisory lock for key: es-lock" : 1,
    "error: bundle not ready: state=RECONNECTING" : 1,
    "http_504: <html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n<hr><center>nginx/1.31.3</center>\r\n</body>\r\n</html>\r\n" : 12,
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 13
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 29,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 23,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 29,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 23,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 36,
      "EU/order-query_OrderProjector_1_EUROPA" : 33,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 36,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 33
    },
    "esHead" : 1814
  }
}
```

## Timeline

- +5,9 s: scenario start
- +5,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +26,4 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +98,1 s: partition healed
- +141,8 s: load done: 73 requests
- +152,4 s: invariants: converging up to 240 s
- +152,9 s: cleanup start
- +154,7 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-09-29T13:24:16.839750500Z, EU/order-observer_NotificationObserver_1_*=1715, EU/order-query_OrderProjector_1_*=1716}
{t=2026-09-29T13:24:22.175740600Z, EU/order-observer_NotificationObserver_1_*=1726, EU/order-query_OrderProjector_1_*=1727}
{t=2026-09-29T13:24:27.262814800Z, EU/order-observer_NotificationObserver_1_*=1733, EU/order-query_OrderProjector_1_*=1734}
{t=2026-09-29T13:24:32.339025600Z, EU/order-observer_NotificationObserver_1_*=1738, EU/order-query_OrderProjector_1_*=1739}
{t=2026-09-29T13:24:37.425463400Z, EU/order-observer_NotificationObserver_1_*=1751, EU/order-query_OrderProjector_1_*=1750}
{t=2026-09-29T13:24:42.498651100Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:24:47.591233300Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:24:52.659858Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:24:57.744387300Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:02.834966200Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:07.906070700Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:12.983830500Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:18.053832500Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:23.120556900Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:28.184624500Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:33.247833900Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:38.322167Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:43.407840600Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:48.494262200Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:53.595866800Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:25:58.669800400Z, EU/order-observer_NotificationObserver_1_*=1752, EU/order-query_OrderProjector_1_*=1752}
{t=2026-09-29T13:26:03.740781100Z, EU/order-observer_NotificationObserver_1_*=1760, EU/order-query_OrderProjector_1_*=1759}
{t=2026-09-29T13:26:08.809310Z, EU/order-observer_NotificationObserver_1_*=1775, EU/order-query_OrderProjector_1_*=1774}
{t=2026-09-29T13:26:13.899054200Z, EU/order-observer_NotificationObserver_1_*=1785, EU/order-query_OrderProjector_1_*=1785}
{t=2026-09-29T13:26:18.967743300Z, EU/order-observer_NotificationObserver_1_*=1793, EU/order-query_OrderProjector_1_*=1793}
{t=2026-09-29T13:26:24.048434200Z, EU/order-observer_NotificationObserver_1_*=1798, EU/order-query_OrderProjector_1_*=1798}
{t=2026-09-29T13:26:29.134164600Z, EU/order-observer_NotificationObserver_1_*=1803, EU/order-query_OrderProjector_1_*=1802}
{t=2026-09-29T13:26:34.203939800Z, EU/order-observer_NotificationObserver_1_*=1808, EU/order-query_OrderProjector_1_*=1807}
{t=2026-09-29T13:26:39.269986900Z, EU/order-observer_NotificationObserver_1_*=1813, EU/order-query_OrderProjector_1_*=1812}
```

### log excerpts: order-query-cina

```
2026-09-29T13:25:38.742Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30235ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30235ms (timeout=30000)
```

### log excerpts: order-query-apac

```
2026-09-29T13:25:06.846Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-09-29T13:25:08.346Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-09-29T13:25:37.853Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30128ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30128ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30397ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-09-29T13:25:06.817Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={EventFetchRequest=1}
2026-09-29T13:25:08.317Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-09-29T13:25:38.824Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30018ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30018ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30418ms (timeout=30000)
```

### log excerpts: gateway-cina

```
2026-09-29T13:24:17.084Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
2026-09-29T13:24:17.084Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=HANDSHAKING to=REGISTERING reason=after_welcome force=true
2026-09-29T13:24:17.087Z  INFO 1 --- [order-gateway] [or-gateway-cina] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=REGISTERING to=READY reason=registered force=true
2026-09-29T13:24:47.645Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={CreateOrderCommand=1}
2026-09-29T13:25:02.649Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:26:32.668Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:24:47.645Z  WARN 1 --- [order-gateway] [cat-handler-626] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-cina-6 region=CINA kind=timeout latencyMs=30107 idempotencyKey=c3f73002-8f49-434c-a368-437536a6ba94 error=CreateOrderCommand: request expired after 30106ms (timeout=30000)
2026-09-29T13:25:02.649Z  WARN 1 --- [order-gateway] [cat-handler-630] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-cina-13 region=CINA kind=timeout latencyMs=30110 idempotencyKey=4fb36986-cf09-4c41-814a-e766b61b1d78 error=CreateOrderCommand: request expired after 30108ms (timeout=30000)
2026-09-29T13:26:32.668Z  WARN 1 --- [order-gateway] [cat-handler-649] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-cina-55 region=CINA kind=timeout latencyMs=30124 idempotencyKey=2aad62e5-a899-47de-94b0-512f582e3675 error=CreateOrderCommand: request expired after 30122ms (timeout=30000)
```

### log excerpts: gateway-india

```
2026-09-29T13:24:19.017Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
2026-09-29T13:24:17.802Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T13:24:17.805Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T13:24:19.017Z  INFO 1 --- [order-gateway] [r-gateway-india] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=HANDSHAKING to=REGISTERING reason=after_welcome force=true
2026-09-29T13:25:02.211Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:26:32.230Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:24:17.179Z  WARN 1 --- [order-gateway] [cat-handler-564] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-india-2 region=INDIA kind=error latencyMs=0 idempotencyKey=05ca8d41-4271-4314-bd26-f9d195c1323c error=bundle not ready: state=RECONNECTING
2026-09-29T13:25:02.211Z  WARN 1 --- [order-gateway] [cat-handler-568] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-india-9 region=INDIA kind=timeout latencyMs=30033 idempotencyKey=de0a29cd-7f9e-48b9-b89f-9aa3674c6143 error=CreateOrderCommand: request expired after 30032ms (timeout=30000)
2026-09-29T13:26:32.230Z  WARN 1 --- [order-gateway] [cat-handler-587] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-india-51 region=INDIA kind=timeout latencyMs=30047 idempotencyKey=b33f3168-4159-41f7-9888-0c2d1ac2ca42 error=CreateOrderCommand: request expired after 30046ms (timeout=30000)
```

### log excerpts: gateway-australia-nz

```
2026-09-29T13:24:47.577Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={CreateOrderCommand=1}
2026-09-29T13:25:02.584Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:26:32.604Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T13:24:47.577Z  WARN 1 --- [order-gateway] [cat-handler-569] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-australia-nz-5 region=AUSTRALIA_NZ kind=timeout latencyMs=30113 idempotencyKey=1d95ce69-ad34-4ae4-bfa8-785b4b5e8392 error=CreateOrderCommand: request expired after 30112ms (timeout=30000)
2026-09-29T13:25:02.584Z  WARN 1 --- [order-gateway] [cat-handler-573] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-australia-nz-12 region=AUSTRALIA_NZ kind=timeout latencyMs=30117 idempotencyKey=07eabdea-cf8e-4d9e-8d8f-e806c96f1005 error=CreateOrderCommand: request expired after 30115ms (timeout=30000)
2026-09-29T13:26:32.604Z  WARN 1 --- [order-gateway] [cat-handler-592] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-ceec1a-australia-nz-54 region=AUSTRALIA_NZ kind=timeout latencyMs=30131 idempotencyKey=28e401e7-1cb1-4d1b-a04c-ef4f301777e9 error=CreateOrderCommand: request expired after 30130ms (timeout=30000)
```

### stack actions

```
2026-09-29T13:24:36.930964800Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-09-29T13:24:37.174304400Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-09-29T13:25:37.412833300Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-09-29T13:25:37.545477500Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-09-29T13:25:37.699795Z docker restart lb
```

