# S07: Region partition (APAC off the WAN)

- Label: `v3e`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-22T16:13:41.641352900Z, finished: 2026-10-22T16:16:03.373252500Z (141 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-v3e-597076-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{durationSeconds=120, itemsPerOrder=1, completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 43; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 165 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 121 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 165 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=133 ack=121 failed=12 p50=439ms p95=4002ms p99=45000ms max=45002ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 43,
    "acked" : 43,
    "failed" : 0,
    "p50" : 278,
    "p95" : 574,
    "p99" : 581,
    "max" : 581,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4002,
    "p95" : 45002,
    "p99" : 45002,
    "max" : 45002,
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
      "RequestTimeoutException" : 4
    },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 8
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
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 7,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 7,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12
    },
    "esHead" : 4286
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +8,0 s: scenario start
- +8,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,6 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +90,9 s: partition healed
- +128,7 s: load done: 133 requests
- +139,4 s: invariants: converging up to 240 s
- +139,8 s: cleanup start
- +141,7 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-22T16:13:49.622321900Z, head=4132, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4132, EU/order-query_OrderProjector_1_*=4132}
{t=2026-10-22T16:13:55.039920Z, head=4156, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4156, EU/order-query_OrderProjector_1_*=4156}
{t=2026-10-22T16:14:00.142497400Z, head=4156, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4156, EU/order-query_OrderProjector_1_*=4156}
{t=2026-10-22T16:14:05.262236100Z, head=4158, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4158, EU/order-query_OrderProjector_1_*=4158}
{t=2026-10-22T16:14:10.384657100Z, head=4182, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4182, EU/order-query_OrderProjector_1_*=4182}
{t=2026-10-22T16:14:15.510501200Z, head=4182, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4182, EU/order-query_OrderProjector_1_*=4182}
{t=2026-10-22T16:14:20.607970200Z, head=4188, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4188, EU/order-query_OrderProjector_1_*=4188}
{t=2026-10-22T16:14:25.729547400Z, head=4194, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4194, EU/order-query_OrderProjector_1_*=4194}
{t=2026-10-22T16:14:30.852480300Z, head=4194, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4194, EU/order-query_OrderProjector_1_*=4194}
{t=2026-10-22T16:14:35.953209700Z, head=4204, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4204, EU/order-query_OrderProjector_1_*=4204}
{t=2026-10-22T16:14:41.086355500Z, head=4208, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4208, EU/order-query_OrderProjector_1_*=4208}
{t=2026-10-22T16:14:46.200443300Z, head=4208, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4208, EU/order-query_OrderProjector_1_*=4208}
{t=2026-10-22T16:14:51.302644100Z, head=4220, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4220, EU/order-query_OrderProjector_1_*=4220}
{t=2026-10-22T16:14:56.403396300Z, head=4222, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4222, EU/order-query_OrderProjector_1_*=4222}
{t=2026-10-22T16:15:01.498997700Z, head=4222, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4222, EU/order-query_OrderProjector_1_*=4222}
{t=2026-10-22T16:15:06.596267800Z, head=4234, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4234, EU/order-query_OrderProjector_1_*=4234}
{t=2026-10-22T16:15:11.690071300Z, head=4236, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4236, EU/order-query_OrderProjector_1_*=4236}
{t=2026-10-22T16:15:16.785438900Z, head=4236, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4236, EU/order-query_OrderProjector_1_*=4236}
{t=2026-10-22T16:15:21.891776500Z, head=4255, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4255, EU/order-query_OrderProjector_1_*=4255}
{t=2026-10-22T16:15:27.065549200Z, head=4264, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4264, EU/order-query_OrderProjector_1_*=4264}
{t=2026-10-22T16:15:32.164937500Z, head=4264, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4264, EU/order-query_OrderProjector_1_*=4264}
{t=2026-10-22T16:15:37.264911400Z, head=4284, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4284, EU/order-query_OrderProjector_1_*=4284}
{t=2026-10-22T16:15:42.375331200Z, head=4286, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4286, EU/order-query_OrderProjector_1_*=4286}
{t=2026-10-22T16:15:47.472809Z, head=4286, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4286, EU/order-query_OrderProjector_1_*=4286}
{t=2026-10-22T16:15:52.569908800Z, head=4286, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4286, EU/order-query_OrderProjector_1_*=4286}
{t=2026-10-22T16:15:57.666954900Z, head=4286, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4286, EU/order-query_OrderProjector_1_*=4286}
```

### log excerpts: order-query-apac

```
2026-10-22T16:14:40.430Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-22T16:15:11.438Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30263ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30263ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30006ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-10-22T16:14:40.233Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-22T16:15:11.239Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=3 pending=0 byType={EventFetchRequest=3}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30072ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30072ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30081ms (timeout=30000)
```

### stack actions

```
2026-10-22T16:14:09.731460800Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-22T16:14:10.027532200Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-22T16:15:10.254762500Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-22T16:15:10.376785200Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-22T16:15:10.525720700Z docker restart lb
```

