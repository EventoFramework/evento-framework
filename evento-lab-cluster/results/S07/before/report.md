# S07: Region partition (APAC off the WAN)

- Label: `before`
- Gaps exercised: G22, G25, G11
- Started: 2026-09-29T16:47:41.533507200Z, finished: 2026-09-29T16:50:02.851434100Z (141 s)
- Verdicts: {UNEXPECTED=1, AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s07-before-28f4ab-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.6, itemsPerOrder=1, durationSeconds=120}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **UNEXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 0,0% of 16; failures {client_error=4, error=1, timeout=11} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 166 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 46 acknowledged ops missing; 14 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 34 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 34 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 166 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=85 ack=46 failed=39 p50=2891ms p95=30232ms p99=45001ms max=45001ms failures={client_error=11, client_timeout=3, error=1, http_504=12, timeout=12} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 16,
    "acked" : 0,
    "failed" : 16,
    "p50" : 30085,
    "p95" : 30232,
    "p99" : 30232,
    "max" : 30232,
    "kinds" : {
      "client_error" : 4,
      "error" : 1,
      "timeout" : 11
    }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4002,
    "p95" : 4007,
    "p99" : 4007,
    "max" : 4007,
    "kinds" : {
      "http_504" : 12
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 0,
    "EU/order-query_OrderProjector_1_*" : 0
  },
  "logMarkers" : {
    "order-command-apac" : { },
    "order-query-cina" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 4
    },
    "order-query-apac" : {
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2
    },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 3,
      "RequestTimeoutException" : 6
    },
    "gateway-cina" : {
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 1
    },
    "gateway-india" : {
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 1
    },
    "gateway-australia-nz" : {
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 1
    }
  },
  "apacSupervisorSessionFailures" : 0,
  "failedRequestsByError" : {
    "client_error: java.io.IOException: HTTP/1.1 header parser received no bytes" : 11,
    "client_timeout: java.net.http.HttpTimeoutException: request timed out" : 3,
    "error: Failed to acquire advisory lock for key: es-lock" : 1,
    "http_504: <html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n<hr><center>nginx/1.31.3</center>\r\n</body>\r\n</html>\r\n" : 12,
    "timeout: CompleteOrderCommand: request expired after <n>ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 11
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 177,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 11,
      "APAC/order-query_OrderProjector_1_INDIA" : 9,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 11,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 9,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 23,
      "EU/order-query_OrderProjector_1_EUROPA" : 14,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 23,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 14
    }
  }
}
```

## Timeline

- +7,7 s: scenario start
- +7,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,2 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +99,7 s: partition healed
- +128,5 s: load done: 85 requests
- +139,1 s: invariants: converging up to 240 s
- +139,6 s: cleanup start
- +141,3 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-09-29T16:47:49.230169400Z, head=109, head:ALL=0, EU/order-observer_NotificationObserver_1_*=109, EU/order-query_OrderProjector_1_*=109}
{t=2026-09-29T16:47:54.611887300Z, head=122, head:ALL=0, EU/order-observer_NotificationObserver_1_*=122, EU/order-query_OrderProjector_1_*=121}
{t=2026-09-29T16:47:59.728447Z, head=128, head:ALL=0, EU/order-observer_NotificationObserver_1_*=128, EU/order-query_OrderProjector_1_*=128}
{t=2026-09-29T16:48:04.849216900Z, head=131, head:ALL=0, EU/order-observer_NotificationObserver_1_*=130, EU/order-query_OrderProjector_1_*=129}
{t=2026-09-29T16:48:10.005175300Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=141}
{t=2026-09-29T16:48:15.109049200Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:20.215374200Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:25.321124300Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:30.424748300Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:35.531533400Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:40.643850500Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:45.751744400Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:50.866478600Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:48:55.966449400Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:01.076455900Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:06.207008800Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:11.327008300Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:16.439490Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:21.580360500Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:26.688491400Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:31.819232200Z, head=142, head:ALL=0, EU/order-observer_NotificationObserver_1_*=142, EU/order-query_OrderProjector_1_*=142}
{t=2026-09-29T16:49:36.981693400Z, head=160, head:ALL=0, EU/order-observer_NotificationObserver_1_*=158, EU/order-query_OrderProjector_1_*=160}
{t=2026-09-29T16:49:42.086402700Z, head=170, head:ALL=0, EU/order-observer_NotificationObserver_1_*=169, EU/order-query_OrderProjector_1_*=170}
{t=2026-09-29T16:49:47.193779700Z, head=177, head:ALL=0, EU/order-observer_NotificationObserver_1_*=176, EU/order-query_OrderProjector_1_*=177}
{t=2026-09-29T16:49:52.297687800Z, head=177, head:ALL=0, EU/order-observer_NotificationObserver_1_*=177, EU/order-query_OrderProjector_1_*=177}
{t=2026-09-29T16:49:57.397284100Z, head=177, head:ALL=0, EU/order-observer_NotificationObserver_1_*=177, EU/order-query_OrderProjector_1_*=177}
```

### log excerpts: order-query-cina

```
2026-09-29T16:48:39.914Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-09-29T16:49:10.921Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30465ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30465ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30004ms (timeout=30000)
```

### log excerpts: order-query-apac

```
2026-09-29T16:49:10.706Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30350ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30350ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-09-29T16:48:40.656Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={EventFetchRequest=1}
2026-09-29T16:49:10.666Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-09-29T16:49:11.667Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30383ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30383ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30213ms (timeout=30000)
```

### log excerpts: gateway-cina

```
2026-09-29T16:48:37.161Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T16:48:37.162Z  WARN 1 --- [order-gateway] [cat-handler-122] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s07-before-28f4ab-cina-8 region=CINA kind=timeout latencyMs=30099 idempotencyKey=87d57a01-49a5-44ce-9960-345785d46794 error=AddOrderItemCommand: request expired after 30098ms (timeout=30000)
```

### log excerpts: gateway-india

```
2026-09-29T16:48:38.482Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={AddOrderItemCommand=1}
2026-09-29T16:48:38.482Z  WARN 1 --- [order-gateway] [cat-handler-123] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=add_item orderId=s07-before-28f4ab-india-10 region=INDIA kind=timeout latencyMs=30197 idempotencyKey=d5650ea2-9c26-4219-9932-6a3de868cf38 error=AddOrderItemCommand: request expired after 30195ms (timeout=30000)
```

### log excerpts: gateway-australia-nz

```
2026-09-29T16:48:34.669Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T16:48:34.669Z  WARN 1 --- [order-gateway] [cat-handler-118] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s07-before-28f4ab-australia-nz-11 region=AUSTRALIA_NZ kind=timeout latencyMs=30050 idempotencyKey=db0ed70f-3c5a-4e6f-894e-7f1f8da28af9 error=CreateOrderCommand: request expired after 30049ms (timeout=30000)
```

### stack actions

```
2026-09-29T16:48:09.332556800Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-09-29T16:48:09.580312500Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-09-29T16:49:09.799631100Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-09-29T16:49:09.918583800Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-09-29T16:49:10.039794Z docker restart lb
```

