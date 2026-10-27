# S06: WAN latency sweep to the ES primary

- Label: `v3`
- Gaps exercised: G9, G18
- Started: 2026-10-20T15:21:46.497211700Z, finished: 2026-10-20T15:24:49.054911100Z (182 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-v3-4132cf-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{completeRatio=0.0, ratePerMinutePerRegion=30.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=40, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 3429 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 3429 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1120 ack=1120 failed=0 p50=398ms p95=3702ms p99=4225ms max=4542ms |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 489,
        "failed" : 0,
        "p50" : 70
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 544,
        "failed" : 0,
        "p50" : 68
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 226,
        "failed" : 0,
        "p50" : 66
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 186,
        "failed" : 0,
        "p50" : 89
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 456,
        "failed" : 0,
        "p50" : 290
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 396,
        "failed" : 0,
        "p50" : 271
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 968,
        "failed" : 0,
        "p50" : 775
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 1735,
        "failed" : 0,
        "p50" : 1126
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 1896,
        "failed" : 0,
        "p50" : 1307
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 3400,
        "failed" : 0,
        "p50" : 2408
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 4452,
        "failed" : 0,
        "p50" : 3396
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 4536,
        "failed" : 0,
        "p50" : 3308
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 2,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 3,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 2,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 3,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 4,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 4,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 10,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 10
    },
    "esHead" : 1257
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +7,7 s: scenario start
- +7,9 s: RTT to ES primary = 0 ms
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +49,1 s: load done: 280 requests
- +49,1 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=489, failed=0, p50=70}, AM={ackedPerSecond=2.0, p99=544, failed=0, p50=68}, APAC={ackedPerSecond=3.0, p99=226, failed=0, p50=66}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +49,3 s: RTT to ES primary = 50 ms
- +49,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +90,3 s: load done: 280 requests
- +90,3 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=186, failed=0, p50=89}, AM={ackedPerSecond=2.0, p99=456, failed=0, p50=290}, APAC={ackedPerSecond=3.0, p99=396, failed=0, p50=271}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +90,5 s: RTT to ES primary = 150 ms
- +90,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +131,7 s: load done: 280 requests
- +131,7 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=968, failed=0, p50=775}, AM={ackedPerSecond=2.0, p99=1735, failed=0, p50=1126}, APAC={ackedPerSecond=3.0, p99=1896, failed=0, p50=1307}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +131,9 s: RTT to ES primary = 300 ms
- +131,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +176,8 s: load done: 280 requests
- +176,8 s: RTT 300 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=3400, failed=0, p50=2408}, AM={ackedPerSecond=2.0, p99=4452, failed=0, p50=3396}, APAC={ackedPerSecond=3.0, p99=4536, failed=0, p50=3308}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +176,9 s: WAN baseline restored
- +176,9 s: invariants: converging up to 180 s
- +180,7 s: cleanup start
- +182,6 s: cleanup done

### stack actions

```
2026-10-20T15:21:54.206235100Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-20T15:21:54.293305700Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-20T15:21:54.341304800Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-20T15:21:54.385940800Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-20T15:22:35.599910Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-20T15:22:35.643423Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-20T15:22:35.693424500Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-20T15:22:35.739863600Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-20T15:23:16.814214100Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-20T15:23:16.856529700Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-20T15:23:16.908534400Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-20T15:23:16.953176400Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-20T15:23:58.190343800Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-20T15:23:58.234572100Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-20T15:23:58.286083200Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-20T15:23:58.331246400Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-20T15:24:43.306271700Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-10-20T15:24:43.310274Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-10-20T15:24:43.356035300Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-10-20T15:24:43.399827900Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

