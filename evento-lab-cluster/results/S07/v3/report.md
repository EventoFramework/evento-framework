# S07: Region partition (APAC off the WAN)

- Label: `v3`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-20T15:29:24.169419200Z, finished: 2026-10-20T15:31:45.870686400Z (141 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-v3-2724d2-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 44; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=5, EU/order-query_OrderProjector_1_*=5} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 224 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 119 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 224 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=131 ack=119 failed=12 p50=408ms p95=3774ms p99=45000ms max=45001ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 44,
    "acked" : 44,
    "failed" : 0,
    "p50" : 259,
    "p95" : 591,
    "p99" : 673,
    "max" : 673,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 2003,
    "p95" : 45001,
    "p99" : 45001,
    "max" : 45001,
    "kinds" : {
      "client_timeout" : 2,
      "http_504" : 10
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 5,
    "EU/order-query_OrderProjector_1_*" : 5
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
      "event=bundle_correlation_expired" : 3,
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
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 18,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 18,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 9,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 9,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 4,
      "EU/order-query_OrderProjector_1_EUROPA" : 6,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 4,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 6
    },
    "esHead" : 4277
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +7,8 s: scenario start
- +7,8 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,3 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +91,7 s: partition healed
- +128,8 s: load done: 131 requests
- +139,4 s: invariants: converging up to 240 s
- +139,9 s: cleanup start
- +141,7 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-20T15:29:31.994758Z, head=4127, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4127, EU/order-query_OrderProjector_1_*=4127}
{t=2026-10-20T15:29:37.330461600Z, head=4153, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4152, EU/order-query_OrderProjector_1_*=4152}
{t=2026-10-20T15:29:42.456874100Z, head=4153, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4153, EU/order-query_OrderProjector_1_*=4153}
{t=2026-10-20T15:29:47.556683400Z, head=4153, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4153, EU/order-query_OrderProjector_1_*=4153}
{t=2026-10-20T15:29:52.659379200Z, head=4173, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4173, EU/order-query_OrderProjector_1_*=4173}
{t=2026-10-20T15:29:57.775662300Z, head=4173, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4173, EU/order-query_OrderProjector_1_*=4173}
{t=2026-10-20T15:30:02.900872300Z, head=4176, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4173, EU/order-query_OrderProjector_1_*=4173}
{t=2026-10-20T15:30:08.014990Z, head=4187, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4187, EU/order-query_OrderProjector_1_*=4187}
{t=2026-10-20T15:30:13.129786500Z, head=4187, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4187, EU/order-query_OrderProjector_1_*=4187}
{t=2026-10-20T15:30:18.242578200Z, head=4194, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4187, EU/order-query_OrderProjector_1_*=4188}
{t=2026-10-20T15:30:23.371766700Z, head=4201, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4201, EU/order-query_OrderProjector_1_*=4201}
{t=2026-10-20T15:30:28.506573900Z, head=4201, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4201, EU/order-query_OrderProjector_1_*=4201}
{t=2026-10-20T15:30:33.613610700Z, head=4210, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4205, EU/order-query_OrderProjector_1_*=4205}
{t=2026-10-20T15:30:38.736606100Z, head=4215, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4215, EU/order-query_OrderProjector_1_*=4215}
{t=2026-10-20T15:30:43.838601500Z, head=4215, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4215, EU/order-query_OrderProjector_1_*=4215}
{t=2026-10-20T15:30:48.934454200Z, head=4226, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4223, EU/order-query_OrderProjector_1_*=4224}
{t=2026-10-20T15:30:54.032821800Z, head=4229, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4229, EU/order-query_OrderProjector_1_*=4229}
{t=2026-10-20T15:30:59.127697500Z, head=4229, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4229, EU/order-query_OrderProjector_1_*=4229}
{t=2026-10-20T15:31:04.220175900Z, head=4240, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4240, EU/order-query_OrderProjector_1_*=4240}
{t=2026-10-20T15:31:09.346231900Z, head=4250, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4250, EU/order-query_OrderProjector_1_*=4250}
{t=2026-10-20T15:31:14.452015900Z, head=4253, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4253, EU/order-query_OrderProjector_1_*=4253}
{t=2026-10-20T15:31:19.571813500Z, head=4273, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4262, EU/order-query_OrderProjector_1_*=4262}
{t=2026-10-20T15:31:24.688417300Z, head=4277, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4277, EU/order-query_OrderProjector_1_*=4277}
{t=2026-10-20T15:31:29.783339800Z, head=4277, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4277, EU/order-query_OrderProjector_1_*=4277}
{t=2026-10-20T15:31:34.895923600Z, head=4277, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4277, EU/order-query_OrderProjector_1_*=4277}
{t=2026-10-20T15:31:39.988704300Z, head=4277, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4277, EU/order-query_OrderProjector_1_*=4277}
```

### log excerpts: order-query-apac

```
2026-10-20T15:30:22.693Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-20T15:30:53.700Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30156ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30156ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30005ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-10-20T15:30:23.584Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={EventFetchRequest=1}
2026-10-20T15:30:53.591Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-10-20T15:30:54.591Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30425ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30425ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30226ms (timeout=30000)
```

### stack actions

```
2026-10-20T15:29:52.097077500Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-20T15:29:52.338296900Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-20T15:30:52.574099900Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-20T15:30:52.688011100Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-20T15:30:52.814813200Z docker restart lb
```

