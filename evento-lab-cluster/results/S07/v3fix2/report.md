# S07: Region partition (APAC off the WAN)

- Label: `v3fix2`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-20T18:17:59.980950Z, finished: 2026-10-20T18:21:13.426567900Z (193 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s07-v3fix2-7ec00b-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
- lbRoutingAtStart: `ok`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.6, itemsPerOrder=1, durationSeconds=120}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 44; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=5} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 182 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 116 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 182 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=128 ack=116 failed=12 p50=417ms p95=2754ms p99=44999ms max=45001ms failures={client_timeout=2, http_504=10} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 44,
    "acked" : 44,
    "failed" : 0,
    "p50" : 183,
    "p95" : 522,
    "p99" : 551,
    "max" : 551,
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
    "EU/order-observer_NotificationObserver_1_*" : 0,
    "EU/order-query_OrderProjector_1_*" : 5
  },
  "logMarkers" : {
    "order-command-apac" : { },
    "order-query-cina" : {
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2
    },
    "order-query-apac" : {
      "event=bundle_correlation_expired" : 3,
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
    "esHead" : 317,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 9,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 9,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 14,
      "EU/order-query_OrderProjector_1_EUROPA" : 20,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 14,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 20
    }
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +8,3 s: placement: 7 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +59,6 s: placement restored, ready={gateway-africa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +59,7 s: scenario start
- +59,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +80,3 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +143,9 s: partition healed
- +180,6 s: load done: 128 requests
- +191,2 s: invariants: converging up to 240 s
- +191,7 s: cleanup start
- +193,4 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-20T18:18:59.638675600Z, head=173, head:ALL=0, EU/order-observer_NotificationObserver_1_*=173, EU/order-query_OrderProjector_1_*=173}
{t=2026-10-20T18:19:04.955565Z, head=195, head:ALL=0, EU/order-observer_NotificationObserver_1_*=194, EU/order-query_OrderProjector_1_*=194}
{t=2026-10-20T18:19:10.110881400Z, head=195, head:ALL=0, EU/order-observer_NotificationObserver_1_*=195, EU/order-query_OrderProjector_1_*=195}
{t=2026-10-20T18:19:15.223386500Z, head=198, head:ALL=0, EU/order-observer_NotificationObserver_1_*=195, EU/order-query_OrderProjector_1_*=195}
{t=2026-10-20T18:19:20.334639500Z, head=217, head:ALL=0, EU/order-observer_NotificationObserver_1_*=217, EU/order-query_OrderProjector_1_*=217}
{t=2026-10-20T18:19:25.444475600Z, head=217, head:ALL=0, EU/order-observer_NotificationObserver_1_*=217, EU/order-query_OrderProjector_1_*=217}
{t=2026-10-20T18:19:30.550140900Z, head=222, head:ALL=0, EU/order-observer_NotificationObserver_1_*=219, EU/order-query_OrderProjector_1_*=217}
{t=2026-10-20T18:19:35.657414300Z, head=231, head:ALL=0, EU/order-observer_NotificationObserver_1_*=231, EU/order-query_OrderProjector_1_*=231}
{t=2026-10-20T18:19:40.753391500Z, head=231, head:ALL=0, EU/order-observer_NotificationObserver_1_*=231, EU/order-query_OrderProjector_1_*=231}
{t=2026-10-20T18:19:45.848417200Z, head=238, head:ALL=0, EU/order-observer_NotificationObserver_1_*=235, EU/order-query_OrderProjector_1_*=233}
{t=2026-10-20T18:19:50.954174400Z, head=245, head:ALL=0, EU/order-observer_NotificationObserver_1_*=245, EU/order-query_OrderProjector_1_*=245}
{t=2026-10-20T18:19:56.048236600Z, head=245, head:ALL=0, EU/order-observer_NotificationObserver_1_*=245, EU/order-query_OrderProjector_1_*=245}
{t=2026-10-20T18:20:01.147077400Z, head=254, head:ALL=0, EU/order-observer_NotificationObserver_1_*=252, EU/order-query_OrderProjector_1_*=250}
{t=2026-10-20T18:20:06.263436100Z, head=259, head:ALL=0, EU/order-observer_NotificationObserver_1_*=259, EU/order-query_OrderProjector_1_*=259}
{t=2026-10-20T18:20:11.357133Z, head=259, head:ALL=0, EU/order-observer_NotificationObserver_1_*=259, EU/order-query_OrderProjector_1_*=259}
{t=2026-10-20T18:20:16.457084800Z, head=271, head:ALL=0, EU/order-observer_NotificationObserver_1_*=270, EU/order-query_OrderProjector_1_*=265}
{t=2026-10-20T18:20:21.568208100Z, head=273, head:ALL=0, EU/order-observer_NotificationObserver_1_*=273, EU/order-query_OrderProjector_1_*=273}
{t=2026-10-20T18:20:26.661013900Z, head=273, head:ALL=0, EU/order-observer_NotificationObserver_1_*=273, EU/order-query_OrderProjector_1_*=273}
{t=2026-10-20T18:20:31.752543700Z, head=284, head:ALL=0, EU/order-observer_NotificationObserver_1_*=284, EU/order-query_OrderProjector_1_*=281}
{t=2026-10-20T18:20:36.867307700Z, head=295, head:ALL=0, EU/order-observer_NotificationObserver_1_*=295, EU/order-query_OrderProjector_1_*=295}
{t=2026-10-20T18:20:41.969414500Z, head=295, head:ALL=0, EU/order-observer_NotificationObserver_1_*=295, EU/order-query_OrderProjector_1_*=295}
{t=2026-10-20T18:20:47.076381100Z, head=313, head:ALL=0, EU/order-observer_NotificationObserver_1_*=307, EU/order-query_OrderProjector_1_*=309}
{t=2026-10-20T18:20:52.219484100Z, head=317, head:ALL=0, EU/order-observer_NotificationObserver_1_*=317, EU/order-query_OrderProjector_1_*=317}
{t=2026-10-20T18:20:57.314937900Z, head=317, head:ALL=0, EU/order-observer_NotificationObserver_1_*=317, EU/order-query_OrderProjector_1_*=317}
{t=2026-10-20T18:21:02.408842200Z, head=317, head:ALL=0, EU/order-observer_NotificationObserver_1_*=317, EU/order-query_OrderProjector_1_*=317}
{t=2026-10-20T18:21:07.519852100Z, head=317, head:ALL=0, EU/order-observer_NotificationObserver_1_*=317, EU/order-query_OrderProjector_1_*=317}
```

### log excerpts: order-query-cina

```
2026-10-20T18:20:21.481Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30485ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30485ms (timeout=30000)
```

### log excerpts: order-query-apac

```
2026-10-20T18:19:49.666Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=1 byType={EventFetchRequest=1}
2026-10-20T18:19:50.167Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-20T18:20:21.175Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30026ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30026ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30012ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-10-20T18:19:50.446Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
2026-10-20T18:20:21.453Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30230ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30230ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30315ms (timeout=30000)
```

### stack actions

```
2026-10-20T18:18:08.250639100Z docker restart gateway-africa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
2026-10-20T18:19:19.749177600Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-20T18:19:20.037972100Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-20T18:20:20.286694Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-20T18:20:20.400858200Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-20T18:20:20.535541800Z docker restart lb
```

