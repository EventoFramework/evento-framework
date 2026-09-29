# S06: WAN latency sweep to the ES primary

- Label: `before`
- Gaps exercised: G9, G18
- Started: 2026-09-29T13:14:29.944534Z, finished: 2026-09-29T13:19:18.215719400Z (288 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-before-a9d82f-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{itemsPerOrder=1, durationSeconds=40, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=30.0, completeRatio=0.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 700 acknowledged ops missing; 136 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 471 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 470 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 19403 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 700 acknowledged ops missing; 136 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 471 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 470 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 19403 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=926 ack=700 failed=226 p50=5779ms p95=30414ms p99=30466ms max=30511ms failures={timeout=226} |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 1036,
        "ackedPerSecond" : 2.0,
        "p50" : 34,
        "failed" : 0
      },
      "AM" : {
        "p99" : 1095,
        "ackedPerSecond" : 2.0,
        "p50" : 49,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 652,
        "ackedPerSecond" : 3.0,
        "p50" : 32,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 1110,
        "ackedPerSecond" : 2.0,
        "p50" : 439,
        "failed" : 0
      },
      "AM" : {
        "p99" : 7181,
        "ackedPerSecond" : 2.0,
        "p50" : 4513,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 18496,
        "ackedPerSecond" : 3.0,
        "p50" : 12303,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 4.05,
      "EU" : {
        "p99" : 26500,
        "ackedPerSecond" : 2.0,
        "p50" : 16852,
        "failed" : 0
      },
      "AM" : {
        "p99" : 30469,
        "ackedPerSecond" : 0.575,
        "p50" : 30105,
        "failed" : 34
      },
      "APAC" : {
        "p99" : 30481,
        "ackedPerSecond" : 0.5,
        "p50" : 30111,
        "failed" : 56
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 1.65,
      "EU" : {
        "p99" : 30477,
        "ackedPerSecond" : 0.425,
        "p50" : 30047,
        "failed" : 36
      },
      "AM" : {
        "p99" : 30511,
        "ackedPerSecond" : 0.0,
        "p50" : 30213,
        "failed" : 40
      },
      "APAC" : {
        "p99" : 30302,
        "ackedPerSecond" : 0.0,
        "p50" : 30208,
        "failed" : 60
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 32,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 194
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 950,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 1,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 5,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 1,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 5,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 7,
      "APAC/order-query_OrderProjector_1_CINA" : 11,
      "APAC/order-query_OrderProjector_1_INDIA" : 3,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 7,
      "APAC/order-saga_PaymentSaga_1_CINA" : 11,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 3,
      "EU/order-observer_NotificationObserver_1_*" : 1,
      "EU/order-query_OrderProjector_1_*" : 1,
      "EU/order-query_OrderProjector_1_AFRICA" : 2,
      "EU/order-query_OrderProjector_1_EUROPA" : 4,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 2,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 4
    }
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +5,9 s: scenario start
- +6,0 s: RTT to ES primary = 0 ms
- +6,2 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +47,0 s: load done: 280 requests
- +47,1 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={p99=1036, ackedPerSecond=2.0, p50=34, failed=0}, AM={p99=1095, ackedPerSecond=2.0, p50=49, failed=0}, APAC={p99=652, ackedPerSecond=3.0, p50=32, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +47,1 s: RTT to ES primary = 50 ms
- +47,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +112,4 s: load done: 280 requests
- +112,4 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={p99=1110, ackedPerSecond=2.0, p50=439, failed=0}, AM={p99=7181, ackedPerSecond=2.0, p50=4513, failed=0}, APAC={p99=18496, ackedPerSecond=3.0, p50=12303, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +112,4 s: RTT to ES primary = 150 ms
- +112,4 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +195,9 s: load done: 213 requests
- +195,9 s: RTT 150 ms: {esAppendsPerSecond=4.05, EU={p99=26500, ackedPerSecond=2.0, p50=16852, failed=0}, AM={p99=30469, ackedPerSecond=0.575, p50=30105, failed=34}, APAC={p99=30481, ackedPerSecond=0.5, p50=30111, failed=56}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +195,9 s: RTT to ES primary = 300 ms
- +195,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +266,6 s: load done: 153 requests
- +266,6 s: RTT 300 ms: {esAppendsPerSecond=1.65, EU={p99=30477, ackedPerSecond=0.425, p50=30047, failed=36}, AM={p99=30511, ackedPerSecond=0.0, p50=30213, failed=40}, APAC={p99=30302, ackedPerSecond=0.0, p50=30208, failed=60}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +266,7 s: WAN baseline restored
- +266,7 s: invariants: converging up to 180 s
- +286,4 s: cleanup start
- +288,3 s: cleanup done

### stack actions

```
2026-09-29T13:14:35.901309400Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T13:14:35.926808700Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T13:14:35.935019300Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T13:14:35.938820500Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T13:15:17.035837500Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T13:15:17.040256500Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T13:15:17.047448400Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T13:15:17.051089800Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T13:16:22.336468700Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T13:16:22.340730200Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T13:16:22.348221100Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T13:16:22.350962400Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T13:17:45.857830300Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T13:17:45.859995800Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T13:17:45.864953700Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T13:17:45.867261800Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T13:18:56.577012100Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-09-29T13:18:56.586995Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-09-29T13:18:56.590538700Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-09-29T13:18:56.594143900Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

