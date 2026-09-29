# S06: WAN latency sweep to the ES primary

- Label: `after`
- Gaps exercised: G9, G18
- Started: 2026-09-29T15:39:17.339012700Z, finished: 2026-09-29T15:42:57.100515600Z (219 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-after-ae4eee-`
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
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1071 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 212 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1071 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 212 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1102 ack=1071 failed=31 p50=530ms p95=24948ms p99=30373ms max=30444ms failures={timeout=31} |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 75,
        "failed" : 0,
        "p50" : 23
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 53,
        "failed" : 0,
        "p50" : 22
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 151,
        "failed" : 0,
        "p50" : 29
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 182,
        "failed" : 0,
        "p50" : 75
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 642,
        "failed" : 0,
        "p50" : 429
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 548,
        "failed" : 0,
        "p50" : 434
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 3156,
        "failed" : 0,
        "p50" : 1719
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 4835,
        "failed" : 0,
        "p50" : 2877
      },
      "APAC" : {
        "ackedPerSecond" : 3.0,
        "p99" : 4391,
        "failed" : 0,
        "p50" : 2726
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 6.55,
      "EU" : {
        "ackedPerSecond" : 2.0,
        "p99" : 20014,
        "failed" : 0,
        "p50" : 12997
      },
      "AM" : {
        "ackedPerSecond" : 2.0,
        "p99" : 27399,
        "failed" : 0,
        "p50" : 16498
      },
      "APAC" : {
        "ackedPerSecond" : 1.775,
        "p99" : 30440,
        "failed" : 31,
        "p50" : 22811
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 13,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 18
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 15,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 13,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 13,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 1,
      "APAC/order-query_OrderProjector_1_CINA" : 0,
      "APAC/order-query_OrderProjector_1_INDIA" : 2,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 1,
      "APAC/order-saga_PaymentSaga_1_CINA" : 0,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 2,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 33,
      "EU/order-query_OrderProjector_1_EUROPA" : 35,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 33,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 35
    },
    "esHead" : 1410
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +7,9 s: scenario start
- +7,9 s: RTT to ES primary = 0 ms
- +8,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +48,9 s: load done: 280 requests
- +48,9 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=75, failed=0, p50=23}, AM={ackedPerSecond=2.0, p99=53, failed=0, p50=22}, APAC={ackedPerSecond=3.0, p99=151, failed=0, p50=29}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +48,9 s: RTT to ES primary = 50 ms
- +49,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +89,9 s: load done: 280 requests
- +90,0 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=182, failed=0, p50=75}, AM={ackedPerSecond=2.0, p99=642, failed=0, p50=429}, APAC={ackedPerSecond=3.0, p99=548, failed=0, p50=434}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +90,0 s: RTT to ES primary = 150 ms
- +90,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +135,5 s: load done: 280 requests
- +135,5 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={ackedPerSecond=2.0, p99=3156, failed=0, p50=1719}, AM={ackedPerSecond=2.0, p99=4835, failed=0, p50=2877}, APAC={ackedPerSecond=3.0, p99=4391, failed=0, p50=2726}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +135,5 s: RTT to ES primary = 300 ms
- +135,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +217,4 s: load done: 262 requests
- +217,5 s: RTT 300 ms: {esAppendsPerSecond=6.55, EU={ackedPerSecond=2.0, p99=20014, failed=0, p50=12997}, AM={ackedPerSecond=2.0, p99=27399, failed=0, p50=16498}, APAC={ackedPerSecond=1.775, p99=30440, failed=31, p50=22811}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +217,5 s: WAN baseline restored
- +217,5 s: invariants: converging up to 180 s
- +218,0 s: cleanup start
- +219,8 s: cleanup done

### stack actions

```
2026-09-29T15:39:25.215292400Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T15:39:25.249223800Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T15:39:25.255563200Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T15:39:25.258191700Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T15:40:06.256540700Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T15:40:06.259537900Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T15:40:06.265538400Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T15:40:06.267538500Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T15:40:47.315055100Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T15:40:47.318054Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T15:40:47.325054300Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T15:40:47.328349Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T15:41:32.826099100Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T15:41:32.828795Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T15:41:32.833993Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T15:41:32.835995600Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T15:42:54.804800500Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-09-29T15:42:54.808722600Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-09-29T15:42:54.811598800Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-09-29T15:42:54.814293100Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

