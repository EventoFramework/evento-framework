# S06: WAN latency sweep to the ES primary

- Label: `v3e`
- Gaps exercised: G9, G18
- Started: 2026-10-22T16:06:03.177208700Z, finished: 2026-10-22T16:09:02.462353300Z (179 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-v3e-05b662-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{ratePerMinutePerRegion=30.0, completeRatio=0.0, itemsPerOrder=1, durationSeconds=40, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 213 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1120 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 213 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1120 ack=1120 failed=0 p50=443ms p95=3388ms p99=3957ms max=4583ms |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 464,
        "ackedPerSecond" : 2.0,
        "p50" : 81,
        "failed" : 0
      },
      "AM" : {
        "p99" : 656,
        "ackedPerSecond" : 2.0,
        "p50" : 50,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 274,
        "ackedPerSecond" : 3.0,
        "p50" : 70,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 267,
        "ackedPerSecond" : 2.0,
        "p50" : 137,
        "failed" : 0
      },
      "AM" : {
        "p99" : 517,
        "ackedPerSecond" : 2.0,
        "p50" : 324,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 497,
        "ackedPerSecond" : 3.0,
        "p50" : 307,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 971,
        "ackedPerSecond" : 2.0,
        "p50" : 691,
        "failed" : 0
      },
      "AM" : {
        "p99" : 1539,
        "ackedPerSecond" : 2.0,
        "p50" : 1151,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 2113,
        "ackedPerSecond" : 3.0,
        "p50" : 1289,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 2794,
        "ackedPerSecond" : 2.0,
        "p50" : 2468,
        "failed" : 0
      },
      "AM" : {
        "p99" : 4583,
        "ackedPerSecond" : 2.0,
        "p50" : 3069,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 4154,
        "ackedPerSecond" : 3.0,
        "p50" : 3092,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : { },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1256,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 5,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 0,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 0,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 3,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 3,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 9,
      "EU/order-query_OrderProjector_1_EUROPA" : 12,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 9,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 12
    }
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +7,9 s: scenario start
- +8,1 s: RTT to ES primary = 0 ms
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +49,3 s: load done: 280 requests
- +49,3 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={p99=464, ackedPerSecond=2.0, p50=81, failed=0}, AM={p99=656, ackedPerSecond=2.0, p50=50, failed=0}, APAC={p99=274, ackedPerSecond=3.0, p50=70, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +49,5 s: RTT to ES primary = 50 ms
- +49,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +90,5 s: load done: 280 requests
- +90,6 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={p99=267, ackedPerSecond=2.0, p50=137, failed=0}, AM={p99=517, ackedPerSecond=2.0, p50=324, failed=0}, APAC={p99=497, ackedPerSecond=3.0, p50=307, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +90,7 s: RTT to ES primary = 150 ms
- +90,8 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +132,1 s: load done: 280 requests
- +132,1 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={p99=971, ackedPerSecond=2.0, p50=691, failed=0}, AM={p99=1539, ackedPerSecond=2.0, p50=1151, failed=0}, APAC={p99=2113, ackedPerSecond=3.0, p50=1289, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +132,3 s: RTT to ES primary = 300 ms
- +132,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +176,7 s: load done: 280 requests
- +176,7 s: RTT 300 ms: {esAppendsPerSecond=7.0, EU={p99=2794, ackedPerSecond=2.0, p50=2468, failed=0}, AM={p99=4583, ackedPerSecond=2.0, p50=3069, failed=0}, APAC={p99=4154, ackedPerSecond=3.0, p50=3092, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +176,8 s: WAN baseline restored
- +176,8 s: invariants: converging up to 180 s
- +177,4 s: cleanup start
- +179,3 s: cleanup done

### stack actions

```
2026-10-22T16:06:11.058454100Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-22T16:06:11.120357100Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-22T16:06:11.168197300Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-22T16:06:11.215966700Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-22T16:06:52.497769800Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-22T16:06:52.539496900Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-22T16:06:52.589845300Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-22T16:06:52.635948700Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-22T16:07:33.743188Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-22T16:07:33.787182500Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-22T16:07:33.833177900Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-22T16:07:33.875163800Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-22T16:08:15.280739900Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-22T16:08:15.326319100Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-22T16:08:15.372612100Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-22T16:08:15.414816700Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-22T16:08:59.884231200Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-10-22T16:08:59.887230500Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-10-22T16:08:59.934906800Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-10-22T16:08:59.979215600Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

