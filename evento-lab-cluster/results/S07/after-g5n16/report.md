# S07: Region partition (APAC off the WAN)

- Label: `after-g5n16`
- Gaps exercised: G22, G25, G11
- Started: 2026-09-29T20:32:18.933533Z, finished: 2026-09-29T20:34:40.639357500Z (141 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-after-g5n16-32134f-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 44; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 205 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed (G5) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: Residual window, documented: a payment whose event could not be stored (append failed or the server died first) leaves no key behind, so its retry runs the handler again.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 116 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 205 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=128 ack=116 failed=12 p50=824ms p95=4002ms p99=44999ms max=45001ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 44,
    "acked" : 44,
    "failed" : 0,
    "p50" : 666,
    "p95" : 1063,
    "p99" : 1096,
    "max" : 1096,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 2004,
    "p95" : 45001,
    "p99" : 45001,
    "max" : 45001,
    "kinds" : {
      "client_timeout" : 2,
      "http_504" : 10
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 0,
    "EU/order-query_OrderProjector_1_*" : 0
  },
  "logMarkers" : {
    "order-command-apac" : { },
    "order-query-cina" : { },
    "order-query-apac" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 6
    },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 6
    },
    "gateway-cina" : { },
    "gateway-india" : { },
    "gateway-australia-nz" : { }
  },
  "apacSupervisorSessionFailures" : 0,
  "failedRequestsByError" : {
    "client_timeout: java.net.http.HttpTimeoutException: request timed out" : 2,
    "http_504: <html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n<hr><center>nginx/1.31.3</center>\r\n</body>\r\n</html>\r\n" : 10
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 3,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 5,
      "APAC/order-query_OrderProjector_1_INDIA" : 4,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 5,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 4,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 11,
      "EU/order-query_OrderProjector_1_EUROPA" : 10,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 11,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 10
    },
    "esHead" : 144
  }
}
```

## Timeline

- +8,0 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,6 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +92,5 s: partition healed
- +128,6 s: load done: 128 requests
- +139,4 s: invariants: converging up to 240 s
- +139,9 s: cleanup start
- +141,7 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-09-29T20:32:26.914560500Z, head=0, head:ALL=0, EU/order-observer_NotificationObserver_1_*=0}
{t=2026-09-29T20:32:32.309548100Z, head=20, head:ALL=0, EU/order-observer_NotificationObserver_1_*=18, EU/order-query_OrderProjector_1_*=20}
{t=2026-09-29T20:32:37.470767400Z, head=22, head:ALL=0, EU/order-observer_NotificationObserver_1_*=22, EU/order-query_OrderProjector_1_*=22}
{t=2026-09-29T20:32:42.591785Z, head=24, head:ALL=0, EU/order-observer_NotificationObserver_1_*=22, EU/order-query_OrderProjector_1_*=23}
{t=2026-09-29T20:32:47.759377100Z, head=43, head:ALL=0, EU/order-observer_NotificationObserver_1_*=43, EU/order-query_OrderProjector_1_*=43}
{t=2026-09-29T20:32:52.900230800Z, head=43, head:ALL=0, EU/order-observer_NotificationObserver_1_*=43, EU/order-query_OrderProjector_1_*=43}
{t=2026-09-29T20:32:58.025166200Z, head=50, head:ALL=0, EU/order-observer_NotificationObserver_1_*=45, EU/order-query_OrderProjector_1_*=48}
{t=2026-09-29T20:33:03.150334700Z, head=57, head:ALL=0, EU/order-observer_NotificationObserver_1_*=57, EU/order-query_OrderProjector_1_*=57}
{t=2026-09-29T20:33:08.263959600Z, head=57, head:ALL=0, EU/order-observer_NotificationObserver_1_*=57, EU/order-query_OrderProjector_1_*=57}
{t=2026-09-29T20:33:13.371247Z, head=64, head:ALL=0, EU/order-observer_NotificationObserver_1_*=62, EU/order-query_OrderProjector_1_*=64}
{t=2026-09-29T20:33:18.517798100Z, head=71, head:ALL=0, EU/order-observer_NotificationObserver_1_*=71, EU/order-query_OrderProjector_1_*=71}
{t=2026-09-29T20:33:23.640781600Z, head=71, head:ALL=0, EU/order-observer_NotificationObserver_1_*=71, EU/order-query_OrderProjector_1_*=71}
{t=2026-09-29T20:33:28.758271Z, head=80, head:ALL=0, EU/order-observer_NotificationObserver_1_*=78, EU/order-query_OrderProjector_1_*=78}
{t=2026-09-29T20:33:33.882264200Z, head=85, head:ALL=0, EU/order-observer_NotificationObserver_1_*=85, EU/order-query_OrderProjector_1_*=85}
{t=2026-09-29T20:33:39.003347100Z, head=85, head:ALL=0, EU/order-observer_NotificationObserver_1_*=85, EU/order-query_OrderProjector_1_*=85}
{t=2026-09-29T20:33:44.157796900Z, head=95, head:ALL=0, EU/order-observer_NotificationObserver_1_*=93, EU/order-query_OrderProjector_1_*=94}
{t=2026-09-29T20:33:49.304424Z, head=99, head:ALL=0, EU/order-observer_NotificationObserver_1_*=99, EU/order-query_OrderProjector_1_*=99}
{t=2026-09-29T20:33:54.447475100Z, head=99, head:ALL=0, EU/order-observer_NotificationObserver_1_*=99, EU/order-query_OrderProjector_1_*=99}
{t=2026-09-29T20:33:59.594970Z, head=110, head:ALL=0, EU/order-observer_NotificationObserver_1_*=107, EU/order-query_OrderProjector_1_*=109}
{t=2026-09-29T20:34:04.731079600Z, head=121, head:ALL=0, EU/order-observer_NotificationObserver_1_*=121, EU/order-query_OrderProjector_1_*=121}
{t=2026-09-29T20:34:09.883942100Z, head=122, head:ALL=0, EU/order-observer_NotificationObserver_1_*=122, EU/order-query_OrderProjector_1_*=122}
{t=2026-09-29T20:34:14.997965300Z, head=138, head:ALL=0, EU/order-observer_NotificationObserver_1_*=137, EU/order-query_OrderProjector_1_*=138}
{t=2026-09-29T20:34:20.150325900Z, head=144, head:ALL=0, EU/order-observer_NotificationObserver_1_*=144, EU/order-query_OrderProjector_1_*=144}
{t=2026-09-29T20:34:25.268048700Z, head=144, head:ALL=0, EU/order-observer_NotificationObserver_1_*=144, EU/order-query_OrderProjector_1_*=144}
{t=2026-09-29T20:34:30.380048300Z, head=144, head:ALL=0, EU/order-observer_NotificationObserver_1_*=144, EU/order-query_OrderProjector_1_*=144}
{t=2026-09-29T20:34:35.484478400Z, head=144, head:ALL=0, EU/order-observer_NotificationObserver_1_*=144, EU/order-query_OrderProjector_1_*=144}
```

### log excerpts: order-query-apac

```
2026-09-29T20:33:17.841Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-09-29T20:33:48.851Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30186ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30186ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30256ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-09-29T20:33:17.737Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-09-29T20:33:48.747Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30056ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30056ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30168ms (timeout=30000)
```

### stack actions

```
2026-09-29T20:32:47.013616200Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-09-29T20:32:47.291123700Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-09-29T20:33:47.560196700Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-09-29T20:33:47.694671900Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-09-29T20:33:47.836980400Z docker restart lb
```

