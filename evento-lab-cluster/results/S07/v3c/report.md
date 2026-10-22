# S07: Region partition (APAC off the WAN)

- Label: `v3c`
- Gaps exercised: G22, G25, G11
- Started: 2026-10-22T13:16:48.759341800Z, finished: 2026-10-22T13:19:11.788353100Z (143 s)
- Verdicts: {UNEXPECTED=1, AS_EXPECTED=5}

## Parameters

- orderIdPrefix: `s07-v3c-58fa3d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.6}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **UNEXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 62,5% of 16; failures {client_error=2, client_timeout=4} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=0, EU/order-query_OrderProjector_1_*=0} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 199 ms |
| no duplicate projection/saga side effects (I2) | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | no duplicates |
| no command applied twice (events) | **AS_EXPECTED** | 0 events stored twice: retries carry an idempotency key and are replayed (G5) | a retry after an indeterminate failure stores the command again (G5, N14) | 0 duplicate event(s): {duplicateOrderCreated=0, duplicateItemsBeyondSent=0, duplicateOrderCompleted=0, duplicatePaymentProcessed=0, idempotencyKeysStoredTwice=0, duplicateObserverDeliveries=0, duplicatePaymentExecutions=0} |
| no payment executed twice (side effects) | **AS_EXPECTED** | 0: the saga's retry carries the derived key of its first attempt and is replayed by the broker (G5), or, when the payment ran but its event was not stored, by order-command's outcome store (G5 inbox) | the saga's retry of an indeterminate ProcessPayment charges again (N14) | 0 extra payment execution(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.
- _no payment executed twice (side effects)_: The residual window (a payment whose event could not be stored) is closed only with the outcome store: images before the inbox fix, or EVENTO_SERVICE_OUTCOME_STORE=off, still pay twice there (S18 hits it on purpose).

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 65 acknowledged ops missing; 8 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 39 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 39 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 199 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=100 ack=65 failed=35 p50=464ms p95=44999ms p99=45000ms max=45002ms failures={client_error=27, client_timeout=8} |

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 16,
    "acked" : 10,
    "failed" : 6,
    "p50" : 21028,
    "p95" : 45002,
    "p99" : 45002,
    "max" : 45002,
    "kinds" : {
      "client_error" : 2,
      "client_timeout" : 4
    }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 30558,
    "p95" : 45000,
    "p99" : 45000,
    "max" : 45000,
    "kinds" : {
      "client_error" : 8,
      "client_timeout" : 4
    }
  },
  "globalLaneLongestStallDuringPartitionSeconds" : {
    "EU/order-observer_NotificationObserver_1_*" : 0,
    "EU/order-query_OrderProjector_1_*" : 0
  },
  "logMarkers" : {
    "order-command-apac" : { },
    "order-query-cina" : {
      "event=bundle_correlation_expired" : 1,
      "RequestTimeoutException" : 2
    },
    "order-query-apac" : {
      "event=bundle_correlation_expired" : 2,
      "RequestTimeoutException" : 8
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
    "client_error: java.io.IOException: HTTP/1.1 header parser received no bytes" : 27,
    "client_timeout: java.net.http.HttpTimeoutException: request timed out" : 8
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 4214,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 4,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 8,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 8,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 17,
      "EU/order-query_OrderProjector_1_EUROPA" : 20,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 17,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 20
    }
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +9,1 s: scenario start
- +9,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +29,7 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +101,3 s: partition healed
- +129,9 s: load done: 100 requests
- +140,6 s: invariants: converging up to 240 s
- +141,1 s: cleanup start
- +143,0 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-10-22T13:16:57.887937500Z, head=4129, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4129, EU/order-query_OrderProjector_1_*=4129}
{t=2026-10-22T13:17:03.209270200Z, head=4151, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4151, EU/order-query_OrderProjector_1_*=4151}
{t=2026-10-22T13:17:08.318577800Z, head=4151, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4151, EU/order-query_OrderProjector_1_*=4151}
{t=2026-10-22T13:17:13.425218Z, head=4153, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4153, EU/order-query_OrderProjector_1_*=4153}
{t=2026-10-22T13:17:18.543078500Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:23.665507400Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:28.787221600Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:33.925864500Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:39.061971100Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:44.191856100Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:49.307146400Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:54.410158Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:17:59.518261300Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:18:04.632329500Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:18:09.741989400Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:18:14.858880600Z, head=4171, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4171, EU/order-query_OrderProjector_1_*=4171}
{t=2026-10-22T13:18:19.958589500Z, head=4182, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4182, EU/order-query_OrderProjector_1_*=4182}
{t=2026-10-22T13:18:25.073432200Z, head=4182, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4182, EU/order-query_OrderProjector_1_*=4182}
{t=2026-10-22T13:18:30.190342100Z, head=4182, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4182, EU/order-query_OrderProjector_1_*=4182}
{t=2026-10-22T13:18:35.309465600Z, head=4190, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4190, EU/order-query_OrderProjector_1_*=4190}
{t=2026-10-22T13:18:40.419065Z, head=4190, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4190, EU/order-query_OrderProjector_1_*=4190}
{t=2026-10-22T13:18:45.565309200Z, head=4212, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4212, EU/order-query_OrderProjector_1_*=4212}
{t=2026-10-22T13:18:50.676235100Z, head=4214, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4214, EU/order-query_OrderProjector_1_*=4214}
{t=2026-10-22T13:18:55.779790900Z, head=4214, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4214, EU/order-query_OrderProjector_1_*=4214}
{t=2026-10-22T13:19:00.889202700Z, head=4214, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4214, EU/order-query_OrderProjector_1_*=4214}
{t=2026-10-22T13:19:05.990104700Z, head=4214, head:ALL=0, EU/order-observer_NotificationObserver_1_*=4214, EU/order-query_OrderProjector_1_*=4214}
```

### log excerpts: order-query-cina

```
2026-10-22T13:18:19.323Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30089ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30089ms (timeout=30000)
```

### log excerpts: order-query-apac

```
2026-10-22T13:17:48.702Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
2026-10-22T13:18:20.210Z  WARN 1 --- [order-query] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=2 pending=0 byType={EventFetchRequest=2}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30291ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30291ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30189ms (timeout=30000)
```

### log excerpts: order-saga-apac

```
2026-10-22T13:18:19.528Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={EventFetchRequest=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30160ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: EventFetchRequest: request expired after 30160ms (timeout=30000)
```

### stack actions

```
2026-10-22T13:17:18.000845800Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-10-22T13:17:18.265552900Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-10-22T13:18:18.483128Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-10-22T13:18:18.629927100Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-10-22T13:18:18.785703100Z docker restart lb
```

