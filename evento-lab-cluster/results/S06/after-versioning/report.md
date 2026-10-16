# S06: WAN latency sweep to the ES primary

- Label: `after-versioning`
- Gaps exercised: G9, G18
- Started: 2026-10-16T00:22:59.964699800Z, finished: 2026-10-16T00:26:45.374295900Z (225 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-after-versioning-d78632-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{durationSeconds=40, itemsPerOrder=1, completeRatio=0.0, ratePerMinutePerRegion=30.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1073 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 3395 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1073 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 3395 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1104 ack=1073 failed=31 p50=543ms p95=24600ms p99=30317ms max=30380ms failures={timeout=31} |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 65,
        "ackedPerSecond" : 2.0,
        "p99" : 187,
        "failed" : 0
      },
      "AM" : {
        "p50" : 68,
        "ackedPerSecond" : 2.0,
        "p99" : 526,
        "failed" : 0
      },
      "APAC" : {
        "p50" : 67,
        "ackedPerSecond" : 3.0,
        "p99" : 172,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 77,
        "ackedPerSecond" : 2.0,
        "p99" : 131,
        "failed" : 0
      },
      "AM" : {
        "p50" : 455,
        "ackedPerSecond" : 2.0,
        "p99" : 593,
        "failed" : 0
      },
      "APAC" : {
        "p50" : 425,
        "ackedPerSecond" : 3.0,
        "p99" : 559,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 1851,
        "ackedPerSecond" : 2.0,
        "p99" : 3337,
        "failed" : 0
      },
      "AM" : {
        "p50" : 2794,
        "ackedPerSecond" : 2.0,
        "p99" : 4675,
        "failed" : 0
      },
      "APAC" : {
        "p50" : 2862,
        "ackedPerSecond" : 3.0,
        "p99" : 4435,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 6.6,
      "EU" : {
        "p50" : 12862,
        "ackedPerSecond" : 2.0,
        "p99" : 19396,
        "failed" : 0
      },
      "AM" : {
        "p50" : 16027,
        "ackedPerSecond" : 2.0,
        "p99" : 27335,
        "failed" : 0
      },
      "APAC" : {
        "p50" : 24021,
        "ackedPerSecond" : 1.825,
        "p99" : 30380,
        "failed" : 31
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 15,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 16
  },
  "consumerLagAfterConvergenceWait" : {
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 16,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 16,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 15,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 2,
      "APAC/order-query_OrderProjector_1_CINA" : 1,
      "APAC/order-query_OrderProjector_1_INDIA" : 0,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 2,
      "APAC/order-saga_PaymentSaga_1_CINA" : 1,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 0,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 40,
      "EU/order-query_OrderProjector_1_EUROPA" : 37,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 40,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 37
    },
    "esHead" : 1226
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +8,2 s: scenario start
- +8,4 s: RTT to ES primary = 0 ms
- +8,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +49,2 s: load done: 280 requests
- +49,3 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={p50=65, ackedPerSecond=2.0, p99=187, failed=0}, AM={p50=68, ackedPerSecond=2.0, p99=526, failed=0}, APAC={p50=67, ackedPerSecond=3.0, p99=172, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +50,0 s: RTT to ES primary = 50 ms
- +50,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +90,9 s: load done: 280 requests
- +90,9 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={p50=77, ackedPerSecond=2.0, p99=131, failed=0}, AM={p50=455, ackedPerSecond=2.0, p99=593, failed=0}, APAC={p50=425, ackedPerSecond=3.0, p99=559, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +91,1 s: RTT to ES primary = 150 ms
- +91,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +136,8 s: load done: 280 requests
- +136,8 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={p50=1851, ackedPerSecond=2.0, p99=3337, failed=0}, AM={p50=2794, ackedPerSecond=2.0, p99=4675, failed=0}, APAC={p50=2862, ackedPerSecond=3.0, p99=4435, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +137,0 s: RTT to ES primary = 300 ms
- +137,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +219,7 s: load done: 264 requests
- +219,7 s: RTT 300 ms: {esAppendsPerSecond=6.6, EU={p50=12862, ackedPerSecond=2.0, p99=19396, failed=0}, AM={p50=16027, ackedPerSecond=2.0, p99=27335, failed=0}, APAC={p50=24021, ackedPerSecond=1.825, p99=30380, failed=31}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +219,8 s: WAN baseline restored
- +219,8 s: invariants: converging up to 180 s
- +223,6 s: cleanup start
- +225,4 s: cleanup done

### stack actions

```
2026-10-16T00:23:08.194506800Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-16T00:23:08.262560400Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-16T00:23:08.309426900Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-16T00:23:08.354144400Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-16T00:23:49.771429500Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-16T00:23:49.816432700Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-16T00:23:49.862439600Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-16T00:23:49.904245200Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-16T00:24:30.886195600Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-16T00:24:30.930216900Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-16T00:24:30.976701Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-16T00:24:31.022679400Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-16T00:25:16.744458400Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-16T00:25:16.788568500Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-16T00:25:16.834089100Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-16T00:25:16.879586100Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-16T00:26:39.664670Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-10-16T00:26:39.668668800Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-10-16T00:26:39.716168800Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-10-16T00:26:39.761345600Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

