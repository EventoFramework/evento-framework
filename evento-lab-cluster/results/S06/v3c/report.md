# S06: WAN latency sweep to the ES primary

- Label: `v3c`
- Gaps exercised: G9, G18
- Started: 2026-10-22T13:08:54.377700300Z, finished: 2026-10-22T13:11:55.866227800Z (181 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-v3c-f9bdd3-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=40, itemsPerOrder=1, completeRatio=0.0, ratePerMinutePerRegion=30.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 257 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 257 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1120 ack=1120 failed=0 p50=447ms p95=3391ms p99=3854ms max=4323ms |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "failed" : 0,
        "p50" : 68,
        "ackedPerSecond" : 2.0,
        "p99" : 530
      },
      "AM" : {
        "failed" : 0,
        "p50" : 69,
        "ackedPerSecond" : 2.0,
        "p99" : 817
      },
      "APAC" : {
        "failed" : 0,
        "p50" : 76,
        "ackedPerSecond" : 3.0,
        "p99" : 316
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "failed" : 0,
        "p50" : 73,
        "ackedPerSecond" : 2.0,
        "p99" : 208
      },
      "AM" : {
        "failed" : 0,
        "p50" : 287,
        "ackedPerSecond" : 2.0,
        "p99" : 496
      },
      "APAC" : {
        "failed" : 0,
        "p50" : 266,
        "ackedPerSecond" : 3.0,
        "p99" : 442
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "failed" : 0,
        "p50" : 635,
        "ackedPerSecond" : 2.0,
        "p99" : 1000
      },
      "AM" : {
        "failed" : 0,
        "p50" : 1107,
        "ackedPerSecond" : 2.0,
        "p99" : 1694
      },
      "APAC" : {
        "failed" : 0,
        "p50" : 1331,
        "ackedPerSecond" : 3.0,
        "p99" : 1941
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "failed" : 0,
        "p50" : 2166,
        "ackedPerSecond" : 2.0,
        "p99" : 3192
      },
      "AM" : {
        "failed" : 0,
        "p50" : 3293,
        "ackedPerSecond" : 2.0,
        "p99" : 4323
      },
      "APAC" : {
        "failed" : 0,
        "p50" : 2897,
        "ackedPerSecond" : 3.0,
        "p99" : 3948
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1255,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 0,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 1,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 4,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 3,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 4,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 3,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 7,
      "EU/order-query_OrderProjector_1_EUROPA" : 5,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 7,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 5
    }
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +9,4 s: scenario start
- +9,7 s: RTT to ES primary = 0 ms
- +9,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +50,8 s: load done: 280 requests
- +50,9 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={failed=0, p50=68, ackedPerSecond=2.0, p99=530}, AM={failed=0, p50=69, ackedPerSecond=2.0, p99=817}, APAC={failed=0, p50=76, ackedPerSecond=3.0, p99=316}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +51,1 s: RTT to ES primary = 50 ms
- +51,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +92,1 s: load done: 280 requests
- +92,1 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={failed=0, p50=73, ackedPerSecond=2.0, p99=208}, AM={failed=0, p50=287, ackedPerSecond=2.0, p99=496}, APAC={failed=0, p50=266, ackedPerSecond=3.0, p99=442}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +92,3 s: RTT to ES primary = 150 ms
- +92,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +133,7 s: load done: 280 requests
- +133,7 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={failed=0, p50=635, ackedPerSecond=2.0, p99=1000}, AM={failed=0, p50=1107, ackedPerSecond=2.0, p99=1694}, APAC={failed=0, p50=1331, ackedPerSecond=3.0, p99=1941}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +133,9 s: RTT to ES primary = 300 ms
- +133,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +178,7 s: load done: 280 requests
- +178,7 s: RTT 300 ms: {esAppendsPerSecond=7.0, EU={failed=0, p50=2166, ackedPerSecond=2.0, p99=3192}, AM={failed=0, p50=3293, ackedPerSecond=2.0, p99=4323}, APAC={failed=0, p50=2897, ackedPerSecond=3.0, p99=3948}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +178,9 s: WAN baseline restored
- +178,9 s: invariants: converging up to 180 s
- +179,5 s: cleanup start
- +181,5 s: cleanup done

### stack actions

```
2026-10-22T13:09:03.816762700Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-22T13:09:03.898806600Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-22T13:09:03.946369800Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-22T13:09:03.991390900Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-22T13:09:45.259051300Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-22T13:09:45.308488600Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-22T13:09:45.357289800Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-22T13:09:45.401151700Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-22T13:10:26.509676600Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-22T13:10:26.557185400Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-22T13:10:26.603775300Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-22T13:10:26.649696600Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-22T13:11:08.060559800Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-22T13:11:08.106801600Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-22T13:11:08.154932500Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-22T13:11:08.199165900Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-22T13:11:53.115311500Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-10-22T13:11:53.120313Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-10-22T13:11:53.163397800Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-10-22T13:11:53.213560500Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

