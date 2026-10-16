# S07: Region partition (APAC off the WAN)

- Label: `after-versioning`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-16T00:32:27.378238200Z, finished: 2026-10-16T00:34:48.868626Z (141 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-after-versioning-7c733d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.6, itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 44; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=5, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 218 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 118 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 218 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=130 ack=118 failed=12 p50=680ms p95=4002ms p99=45000ms max=45002ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 44,
    "acked" : 44,
    "failed" : 0,
    "p50" : 191,
    "p95" : 920,
    "p99" : 928,
    "max" : 928,
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
    "EU/order-observer_NotificationObserver_1_*" : 5,
    "EU/order-query_OrderProjector_1_*" : 0
  },
  "logMarkers" : {
    "order-command-apac" : {
      "event=service_outcome_replayed" : 1
    },
    "order-query-cina" : { },
    "order-query-apac" : { },
    "order-query-global-apac" : { },
    "order-saga-apac" : {
      "event=bundle_correlation_expired" : 1,
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
    "esHead" : 2836,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 3,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 6,
      "APAC/order-query_OrderProjector_1_INDIA" : 4,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 6,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 4,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 15,
      "EU/order-query_OrderProjector_1_EUROPA" : 14,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 15,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 14
    }
  },
  "serviceOutcomeReplayed" : 1
}
```

## Timeline

- +7,7 s: scenario start
- +7,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,3 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +90,4 s: partition healed
- +128,5 s: load done: 130 requests
- +139,2 s: invariants: converging up to 240 s
- +139,7 s: cleanup start
- +141,5 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-16T00:32:35.120122900Z, head=2688, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2688, EU/order-query_OrderProjector_1_*=2688}
{t=2026-10-16T00:32:40.474322400Z, head=2709, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2708, EU/order-query_OrderProjector_1_*=2708}
{t=2026-10-16T00:32:45.737218200Z, head=2710, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2710, EU/order-query_OrderProjector_1_*=2710}
{t=2026-10-16T00:32:50.845488200Z, head=2714, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2712, EU/order-query_OrderProjector_1_*=2711}
{t=2026-10-16T00:32:56.009090400Z, head=2734, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2734, EU/order-query_OrderProjector_1_*=2734}
{t=2026-10-16T00:33:01.107694200Z, head=2734, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2734, EU/order-query_OrderProjector_1_*=2734}
{t=2026-10-16T00:33:06.211935400Z, head=2740, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2734, EU/order-query_OrderProjector_1_*=2735}
{t=2026-10-16T00:33:11.370953500Z, head=2746, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2746, EU/order-query_OrderProjector_1_*=2746}
{t=2026-10-16T00:33:16.470789300Z, head=2746, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2746, EU/order-query_OrderProjector_1_*=2746}
{t=2026-10-16T00:33:21.566871500Z, head=2756, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2749, EU/order-query_OrderProjector_1_*=2750}
{t=2026-10-16T00:33:26.671211800Z, head=2762, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2762, EU/order-query_OrderProjector_1_*=2762}
{t=2026-10-16T00:33:31.775920300Z, head=2762, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2762, EU/order-query_OrderProjector_1_*=2762}
{t=2026-10-16T00:33:36.884930400Z, head=2773, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2769, EU/order-query_OrderProjector_1_*=2769}
{t=2026-10-16T00:33:42.024764700Z, head=2776, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2776, EU/order-query_OrderProjector_1_*=2776}
{t=2026-10-16T00:33:47.155722100Z, head=2776, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2776, EU/order-query_OrderProjector_1_*=2776}
{t=2026-10-16T00:33:52.252552400Z, head=2789, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2784, EU/order-query_OrderProjector_1_*=2784}
{t=2026-10-16T00:33:57.357458Z, head=2790, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2790, EU/order-query_OrderProjector_1_*=2790}
{t=2026-10-16T00:34:02.474254700Z, head=2791, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2791, EU/order-query_OrderProjector_1_*=2791}
{t=2026-10-16T00:34:07.568672800Z, head=2804, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2802, EU/order-query_OrderProjector_1_*=2802}
{t=2026-10-16T00:34:12.671903500Z, head=2812, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2812, EU/order-query_OrderProjector_1_*=2812}
{t=2026-10-16T00:34:17.787838700Z, head=2812, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2812, EU/order-query_OrderProjector_1_*=2812}
{t=2026-10-16T00:34:22.901717900Z, head=2830, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2829, EU/order-query_OrderProjector_1_*=2830}
{t=2026-10-16T00:34:28.053665600Z, head=2836, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2836, EU/order-query_OrderProjector_1_*=2836}
{t=2026-10-16T00:34:33.221177100Z, head=2836, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2836, EU/order-query_OrderProjector_1_*=2836}
{t=2026-10-16T00:34:38.315876700Z, head=2836, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2836, EU/order-query_OrderProjector_1_*=2836}
{t=2026-10-16T00:34:43.412007200Z, head=2836, head:ALL=0, EU/order-observer_NotificationObserver_1_*=2836, EU/order-query_OrderProjector_1_*=2836}
```

### log excerpts: order-command-apac

```
2026-10-16T00:34:05.510Z  INFO 1 --- [order-command] [   virtual-2637] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_INDIA:2731:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-16T00:32:55.262026Z
```

### log excerpts: order-saga-apac

```
2026-10-16T00:33:24.878Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=1 byType={ProcessPaymentCommand=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30295ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30295ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30415ms (timeout=30000)
```

### stack actions

```
2026-10-16T00:32:55.218469700Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-16T00:32:55.477451400Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-16T00:33:55.735758300Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-16T00:33:55.886733500Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-16T00:33:56.015514900Z docker restart lb
```

