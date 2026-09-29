# S07: Region partition (APAC off the WAN)

- Label: `after`
- Gaps exercised: G22, G25, G11
- Started: 2026-09-29T15:43:04.245666200Z, finished: 2026-09-29T15:45:26.056313500Z (141 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s07-after-e28f15-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=1, durationSeconds=120, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=4.0, completeRatio=0.6}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EU and AM keep working during the APAC partition | **AS_EXPECTED** | >= 95% of EU/AM commands acknowledged while APAC is cut off |  | 100,0% of 41; failures {} |
| global consumer lanes during the partition | **AS_EXPECTED** | a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over | the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25) | longest checkpoint stall per lane (s): {EU/order-observer_NotificationObserver_1_*=5, EU/order-query_OrderProjector_1_*=5} |
| I6 convergence after healing | **AS_EXPECTED** | read models reach the event store's state within 240 s |  | converged in 203 ms |
| no duplicate projection/saga side effects (I2) | **KNOWN_DEFECT_REPRODUCED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | two replicas process one lane after the partition; the stale one re-applies (G11) | 3 duplicate finding(s) |

- _global consumer lanes during the partition_: A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.

## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 116 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **FAIL** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | 3 duplicate finding(s) |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 44 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 44 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 240 s | converged in 203 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=128 ack=116 failed=12 p50=710ms p95=4002ms p99=44999ms max=45004ms failures={client_timeout=2, http_504=10} |

<details><summary>I2: 3 violation(s)</summary>

- s07-after-e28f15-india-8: 2 x PaymentProcessedEvent
- payment for s07-after-e28f15-cina-14 executed 2 times
- payment for s07-after-e28f15-india-8 executed 2 times

</details>

## Metrics

```json
{
  "nonApacDuringPartition" : {
    "count" : 41,
    "acked" : 41,
    "failed" : 0,
    "p50" : 585,
    "p95" : 846,
    "p99" : 866,
    "max" : 866,
    "kinds" : { }
  },
  "apacDuringPartition" : {
    "count" : 12,
    "acked" : 0,
    "failed" : 12,
    "p50" : 4002,
    "p95" : 45004,
    "p99" : 45004,
    "max" : 45004,
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
    "esHead" : 1555,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 4,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 4,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 5,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 7,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 7,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 22,
      "EU/order-query_OrderProjector_1_EUROPA" : 19,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 22,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 19
    }
  }
}
```

## Timeline

- +7,5 s: scenario start
- +7,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @4.0/min for 120 s
- +28,1 s: APAC partitioned (net-apac detached from toxiproxy and lb)
- +90,1 s: partition healed
- +128,5 s: load done: 128 requests
- +139,1 s: invariants: converging up to 240 s
- +139,6 s: cleanup start
- +141,8 s: cleanup done

### global lane checkpoints (every 5 s)

```
{t=2026-09-29T15:43:11.787553300Z, head=1410, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1410, EU/order-query_OrderProjector_1_*=1410}
{t=2026-09-29T15:43:17.101485400Z, head=1431, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1430, EU/order-query_OrderProjector_1_*=1430}
{t=2026-09-29T15:43:22.214362500Z, head=1432, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1432, EU/order-query_OrderProjector_1_*=1432}
{t=2026-09-29T15:43:27.331435400Z, head=1433, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1432, EU/order-query_OrderProjector_1_*=1433}
{t=2026-09-29T15:43:32.471555500Z, head=1455, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1454, EU/order-query_OrderProjector_1_*=1454}
{t=2026-09-29T15:43:37.574806800Z, head=1455, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1455, EU/order-query_OrderProjector_1_*=1455}
{t=2026-09-29T15:43:42.676759200Z, head=1459, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1455, EU/order-query_OrderProjector_1_*=1455}
{t=2026-09-29T15:43:47.783203800Z, head=1469, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1469, EU/order-query_OrderProjector_1_*=1469}
{t=2026-09-29T15:43:52.886329600Z, head=1469, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1469, EU/order-query_OrderProjector_1_*=1469}
{t=2026-09-29T15:43:57.997497500Z, head=1475, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1469, EU/order-query_OrderProjector_1_*=1469}
{t=2026-09-29T15:44:03.105363Z, head=1481, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1481, EU/order-query_OrderProjector_1_*=1481}
{t=2026-09-29T15:44:08.205414200Z, head=1481, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1481, EU/order-query_OrderProjector_1_*=1481}
{t=2026-09-29T15:44:13.309616400Z, head=1489, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1483, EU/order-query_OrderProjector_1_*=1484}
{t=2026-09-29T15:44:18.416487400Z, head=1495, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1495, EU/order-query_OrderProjector_1_*=1495}
{t=2026-09-29T15:44:23.515194300Z, head=1495, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1495, EU/order-query_OrderProjector_1_*=1495}
{t=2026-09-29T15:44:28.636437Z, head=1501, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1499, EU/order-query_OrderProjector_1_*=1499}
{t=2026-09-29T15:44:33.736802500Z, head=1505, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1505, EU/order-query_OrderProjector_1_*=1505}
{t=2026-09-29T15:44:38.860010700Z, head=1505, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1505, EU/order-query_OrderProjector_1_*=1505}
{t=2026-09-29T15:44:44.050441900Z, head=1518, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1512, EU/order-query_OrderProjector_1_*=1512}
{t=2026-09-29T15:44:49.235210100Z, head=1528, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1528, EU/order-query_OrderProjector_1_*=1528}
{t=2026-09-29T15:44:54.343529200Z, head=1528, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1528, EU/order-query_OrderProjector_1_*=1528}
{t=2026-09-29T15:44:59.501913900Z, head=1546, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1543, EU/order-query_OrderProjector_1_*=1543}
{t=2026-09-29T15:45:04.610076600Z, head=1555, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1555, EU/order-query_OrderProjector_1_*=1555}
{t=2026-09-29T15:45:09.709946600Z, head=1555, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1555, EU/order-query_OrderProjector_1_*=1555}
{t=2026-09-29T15:45:14.824268700Z, head=1555, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1555, EU/order-query_OrderProjector_1_*=1555}
{t=2026-09-29T15:45:19.937374200Z, head=1555, head:ALL=0, EU/order-observer_NotificationObserver_1_*=1555, EU/order-query_OrderProjector_1_*=1555}
```

### log excerpts: order-saga-apac

```
2026-09-29T15:44:01.591Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={ProcessPaymentCommand=1}
2026-09-29T15:44:32.603Z  WARN 1 --- [order-saga] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=2 byType={ProcessPaymentCommand=1}
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30233ms (timeout=30000)
Caused by: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30233ms (timeout=30000)
java.util.concurrent.ExecutionException: com.evento.transport.RequestTimeoutException: ProcessPaymentCommand: request expired after 30003ms (timeout=30000)
```

### stack actions

```
2026-09-29T15:43:31.893983Z docker network disconnect net-apac toxiproxy (aliases=[toxi-apac])
2026-09-29T15:43:32.144904200Z docker network disconnect net-apac lb (aliases=[bus-apac] ip=172.28.3.10)
2026-09-29T15:44:32.401375600Z docker network connect --alias toxi-apac net-apac toxiproxy
2026-09-29T15:44:32.518620400Z docker network connect --alias bus-apac --ip 172.28.3.10 net-apac lb
2026-09-29T15:44:32.757341100Z docker restart lb
```

