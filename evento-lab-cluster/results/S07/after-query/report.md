# S07: Region partition (APAC off the WAN)

- Label: `after-query`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-15T14:04:23.722703400Z, finished: 2026-10-15T14:06:45.336732400Z (141 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-after-query-4c4b06-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 38; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=5, EU/order-query_OrderProjector_1_*=5} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 194 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 111 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 194 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=123 ack=111 failed=12 p50=669ms p95=4003ms p99=45000ms max=45000ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 38,
    "acked" : 38,
    "failed" : 0,
    "p50" : 178,
    "p95" : 850,
    "p99" : 881,
    "max" : 881,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4002,
    "p95" : 45000,
    "p99" : 45000,
    "max" : 45000,
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
    "order-command-apac" : {
      "event=service_outcome_replayed" : 1
    },
    "order-query-cina" : { },
    "order-query-apac" : { },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 4
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
      "AM/order-query_OrderProjector_1_AMERICA" : 6,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 6,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 3,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 20,
      "EU/order-query_OrderProjector_1_EUROPA" : 21,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 20,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 21
    },
    "esHead" : 2869
  },
  "serviceOutcomeReplayed" : 1
}
```

## Timeline

- +7,7 s: scenario start
- +7,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,3 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +90,1 s: partition healed
- +128,7 s: load done: 123 requests
- +139,3 s: invariants: converging up to 240 s
- +139,8 s: cleanup start
- +141,6 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-15T14:04:31.425478600Z, head=2735, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2735, EU/order-query_OrderProjector_1_*=2735}
{t=2026-10-15T14:04:36.702529800Z, head=2757, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2757, EU/order-query_OrderProjector_1_*=2757}
{t=2026-10-15T14:04:41.816567600Z, head=2757, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2757, EU/order-query_OrderProjector_1_*=2757}
{t=2026-10-15T14:04:46.966058400Z, head=2759, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2757, EU/order-query_OrderProjector_1_*=2757}
{t=2026-10-15T14:04:52.108452600Z, head=2778, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2777, EU/order-query_OrderProjector_1_*=2777}
{t=2026-10-15T14:04:57.205274900Z, head=2778, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2778, EU/order-query_OrderProjector_1_*=2778}
{t=2026-10-15T14:05:02.303072600Z, head=2782, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2778, EU/order-query_OrderProjector_1_*=2778}
{t=2026-10-15T14:05:07.418838500Z, head=2786, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2786, EU/order-query_OrderProjector_1_*=2786}
{t=2026-10-15T14:05:12.518148Z, head=2786, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2786, EU/order-query_OrderProjector_1_*=2786}
{t=2026-10-15T14:05:17.614355500Z, head=2791, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2786, EU/order-query_OrderProjector_1_*=2786}
{t=2026-10-15T14:05:22.733802900Z, head=2798, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2798, EU/order-query_OrderProjector_1_*=2798}
{t=2026-10-15T14:05:27.846849200Z, head=2798, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2798, EU/order-query_OrderProjector_1_*=2798}
{t=2026-10-15T14:05:32.984164300Z, head=2806, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2801, EU/order-query_OrderProjector_1_*=2802}
{t=2026-10-15T14:05:38.126247900Z, head=2810, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2810, EU/order-query_OrderProjector_1_*=2810}
{t=2026-10-15T14:05:43.227023800Z, head=2810, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2810, EU/order-query_OrderProjector_1_*=2810}
{t=2026-10-15T14:05:48.320576300Z, head=2818, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2815, EU/order-query_OrderProjector_1_*=2815}
{t=2026-10-15T14:05:53.418892900Z, head=2822, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2822, EU/order-query_OrderProjector_1_*=2822}
{t=2026-10-15T14:05:58.523863200Z, head=2822, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2822, EU/order-query_OrderProjector_1_*=2822}
{t=2026-10-15T14:06:03.639963600Z, head=2830, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2826, EU/order-query_OrderProjector_1_*=2826}
{t=2026-10-15T14:06:08.786076600Z, head=2837, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2836, EU/order-query_OrderProjector_1_*=2836}
{t=2026-10-15T14:06:13.932806700Z, head=2843, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2843, EU/order-query_OrderProjector_1_*=2843}
{t=2026-10-15T14:06:19.027565500Z, head=2858, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2852, EU/order-query_OrderProjector_1_*=2852}
{t=2026-10-15T14:06:24.123150800Z, head=2869, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2869, EU/order-query_OrderProjector_1_*=2869}
{t=2026-10-15T14:06:29.227589400Z, head=2869, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2869, EU/order-query_OrderProjector_1_*=2869}
{t=2026-10-15T14:06:34.321753400Z, head=2869, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2869, EU/order-query_OrderProjector_1_*=2869}
{t=2026-10-15T14:06:39.418258300Z, head=2869, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2869, EU/order-query_OrderProjector_1_*=2869}
```

### log excerpts: order-command-apac

```
2026-10-15T14:06:06.466Z  INFO 1 --- [order-command] [   virtual-2717] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_INDIA:2775:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-15T14:04:51.313951Z
```

### log excerpts: order-saga-apac

```
2026-10-15T14:05:20.756Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={ProcessPaymentCommand=1}
2026-10-15T14:05:21.257Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={ProcessPaymentCommand=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30037ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30037ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30499ms (timeout=30000)
```

### stack actions

```
2026-10-15T14:04:51.534723600Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-15T14:04:51.791513700Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-15T14:05:52.041450500Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-15T14:05:52.166493Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-15T14:05:52.304012400Z docker restart lb
```

