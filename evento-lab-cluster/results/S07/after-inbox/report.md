# S07: Region partition (APAC off the WAN)

- Label: `after-inbox`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-09T09:07:26.913009600Z, finished: 2026-10-09T09:09:51.042654Z (144 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-after-inbox-ee3d79-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.6, ratePerMinutePerRegion=4.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=120, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 40; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 212 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 108 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 212 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=120 ack=108 failed=12 p50=681ms p95=4004ms p99=45000ms max=45000ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 40,
    "acked" : 40,
    "failed" : 0,
    "p50" : 215,
    "p95" : 856,
    "p99" : 908,
    "max" : 908,
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
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2
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
      "AM/order-query_OrderProjector_1_AMERICA" : 10,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 10,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 2,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 8,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 8,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 18,
      "EU/order-query_OrderProjector_1_EUROPA" : 16,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 18,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 16
    },
    "esHead" : 128
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +9,6 s: scenario start
- +9,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +30,4 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +91,9 s: partition healed
- +130,7 s: load done: 120 requests
- +141,5 s: invariants: converging up to 240 s
- +142,1 s: cleanup start
- +144,1 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-09T09:07:36.565431200Z, head=0, head:ALL=0, EU/order-observer_NotificationObserver_1_*=0}
{t=2026-10-09T09:07:42.081901100Z, head=19, head:ALL=0, EU/order-observer_NotificationObserver_1_*=19, EU/order-query_OrderProjector_1_*=19}
{t=2026-10-09T09:07:47.273552800Z, head=20, head:ALL=0, EU/order-observer_NotificationObserver_1_*=20, EU/order-query_OrderProjector_1_*=20}
{t=2026-10-09T09:07:52.414358100Z, head=20, head:ALL=0, EU/order-observer_NotificationObserver_1_*=20, EU/order-query_OrderProjector_1_*=20}
{t=2026-10-09T09:07:57.587647300Z, head=39, head:ALL=0, EU/order-observer_NotificationObserver_1_*=39, EU/order-query_OrderProjector_1_*=39}
{t=2026-10-09T09:08:02.736192800Z, head=39, head:ALL=0, EU/order-observer_NotificationObserver_1_*=39, EU/order-query_OrderProjector_1_*=39}
{t=2026-10-09T09:08:07.855426Z, head=44, head:ALL=0, EU/order-observer_NotificationObserver_1_*=41, EU/order-query_OrderProjector_1_*=42}
{t=2026-10-09T09:08:13.015415300Z, head=51, head:ALL=0, EU/order-observer_NotificationObserver_1_*=51, EU/order-query_OrderProjector_1_*=51}
{t=2026-10-09T09:08:18.170079100Z, head=51, head:ALL=0, EU/order-observer_NotificationObserver_1_*=51, EU/order-query_OrderProjector_1_*=51}
{t=2026-10-09T09:08:23.296492Z, head=58, head:ALL=0, EU/order-observer_NotificationObserver_1_*=57, EU/order-query_OrderProjector_1_*=57}
{t=2026-10-09T09:08:28.443484100Z, head=65, head:ALL=0, EU/order-observer_NotificationObserver_1_*=65, EU/order-query_OrderProjector_1_*=65}
{t=2026-10-09T09:08:33.568842Z, head=65, head:ALL=0, EU/order-observer_NotificationObserver_1_*=65, EU/order-query_OrderProjector_1_*=65}
{t=2026-10-09T09:08:38.691500800Z, head=72, head:ALL=0, EU/order-observer_NotificationObserver_1_*=71, EU/order-query_OrderProjector_1_*=71}
{t=2026-10-09T09:08:43.840841200Z, head=73, head:ALL=0, EU/order-observer_NotificationObserver_1_*=73, EU/order-query_OrderProjector_1_*=73}
{t=2026-10-09T09:08:48.987289400Z, head=73, head:ALL=0, EU/order-observer_NotificationObserver_1_*=73, EU/order-query_OrderProjector_1_*=73}
{t=2026-10-09T09:08:54.108399500Z, head=85, head:ALL=0, EU/order-observer_NotificationObserver_1_*=83, EU/order-query_OrderProjector_1_*=83}
{t=2026-10-09T09:08:59.266127200Z, head=87, head:ALL=0, EU/order-observer_NotificationObserver_1_*=87, EU/order-query_OrderProjector_1_*=87}
{t=2026-10-09T09:09:04.385820400Z, head=87, head:ALL=0, EU/order-observer_NotificationObserver_1_*=87, EU/order-query_OrderProjector_1_*=87}
{t=2026-10-09T09:09:09.514139500Z, head=98, head:ALL=0, EU/order-observer_NotificationObserver_1_*=98, EU/order-query_OrderProjector_1_*=98}
{t=2026-10-09T09:09:14.633813100Z, head=106, head:ALL=0, EU/order-observer_NotificationObserver_1_*=106, EU/order-query_OrderProjector_1_*=106}
{t=2026-10-09T09:09:19.777689900Z, head=108, head:ALL=0, EU/order-observer_NotificationObserver_1_*=108, EU/order-query_OrderProjector_1_*=108}
{t=2026-10-09T09:09:24.910554500Z, head=123, head:ALL=0, EU/order-observer_NotificationObserver_1_*=123, EU/order-query_OrderProjector_1_*=123}
{t=2026-10-09T09:09:30.039284300Z, head=128, head:ALL=0, EU/order-observer_NotificationObserver_1_*=128, EU/order-query_OrderProjector_1_*=128}
{t=2026-10-09T09:09:35.155779300Z, head=128, head:ALL=0, EU/order-observer_NotificationObserver_1_*=128, EU/order-query_OrderProjector_1_*=128}
{t=2026-10-09T09:09:40.294187300Z, head=128, head:ALL=0, EU/order-observer_NotificationObserver_1_*=128, EU/order-query_OrderProjector_1_*=128}
{t=2026-10-09T09:09:45.409955300Z, head=128, head:ALL=0, EU/order-observer_NotificationObserver_1_*=128, EU/order-query_OrderProjector_1_*=128}
```

### log excerpts: order-query-apac

```
2026-10-09T09:08:28.138Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-10-09T09:08:59.148Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30393ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30393ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30005ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-10-09T09:08:59.160Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30492ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30492ms (timeout=30000)
```

### stack actions

```
2026-10-09T09:07:56.711467500Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-09T09:07:57.056476700Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-09T09:08:57.318320400Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-09T09:08:57.463513200Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-09T09:08:57.592309Z docker restart lb
```

