# S06: WAN latency sweep to the ES primary

- Label: `after-query`
- Gaps exercised: G9, G18
- Started: 2026-10-15T13:54:52.461876700Z, finished: 2026-10-15T13:58:33.625826400Z (221 s)
- Verdicts: {AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s06-after-query-d93614-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{itemsPerOrder=1, durationSeconds=40, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=30.0, completeRatio=0.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 1070 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 560 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view of each cell database contains only that cell's contexts |  | 0 foreign row(s) in 560 |
| I5 across the sweep | **AS_EXPECTED** | order_view_global = union of the cells' order_view (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 across the sweep | **AS_EXPECTED** | read models reach the event store's state within 180 s |  | converged in 227 ms |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 1070 acknowledged ops missing; 31 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 560 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view of each cell database contains only that cell's contexts | 0 foreign row(s) in 560 |
| I5 | global completeness | **PASS** | order_view_global = union of the cells' order_view (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 180 s | converged in 227 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=1102 ack=1070 failed=32 p50=539ms p95=25251ms p99=30378ms max=30476ms failures={timeout=32} |

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 65,
        "failed" : 0,
        "p99" : 186,
        "ackedPerSecond" : 2.0
      },
      "AM" : {
        "p50" : 77,
        "failed" : 0,
        "p99" : 511,
        "ackedPerSecond" : 2.0
      },
      "APAC" : {
        "p50" : 64,
        "failed" : 0,
        "p99" : 213,
        "ackedPerSecond" : 3.0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 80,
        "failed" : 0,
        "p99" : 234,
        "ackedPerSecond" : 2.0
      },
      "AM" : {
        "p50" : 429,
        "failed" : 0,
        "p99" : 570,
        "ackedPerSecond" : 2.0
      },
      "APAC" : {
        "p50" : 440,
        "failed" : 0,
        "p99" : 593,
        "ackedPerSecond" : 3.0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p50" : 1907,
        "failed" : 0,
        "p99" : 3384,
        "ackedPerSecond" : 2.0
      },
      "AM" : {
        "p50" : 2873,
        "failed" : 0,
        "p99" : 4664,
        "ackedPerSecond" : 2.0
      },
      "APAC" : {
        "p50" : 2844,
        "failed" : 0,
        "p99" : 4708,
        "ackedPerSecond" : 3.0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 6.525,
      "EU" : {
        "p50" : 12911,
        "failed" : 0,
        "p99" : 19890,
        "ackedPerSecond" : 2.0
      },
      "AM" : {
        "p50" : 16580,
        "failed" : 0,
        "p99" : 26680,
        "ackedPerSecond" : 2.0
      },
      "APAC" : {
        "p50" : 22823,
        "failed" : 32,
        "p99" : 30472,
        "ackedPerSecond" : 1.75
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after <n>ms (timeout=30000)" : 14,
    "timeout: CreateOrderCommand: request expired after <n>ms (timeout=30000)" : 18
  },
  "consumerLagAfterConvergenceWait" : {
    "esHead" : 1223,
    "lag" : {
      "AM/order-query_OrderProjector_1_AMERICA" : 14,
      "AM/order-query_OrderProjector_1_SUD_AMERICA" : 15,
      "AM/order-saga_PaymentSaga_1_AMERICA" : 14,
      "AM/order-saga_PaymentSaga_1_SUD_AMERICA" : 15,
      "APAC/order-query_OrderProjector_1_AUSTRALIA_NZ" : 0,
      "APAC/order-query_OrderProjector_1_CINA" : 2,
      "APAC/order-query_OrderProjector_1_INDIA" : 1,
      "APAC/order-saga_PaymentSaga_1_AUSTRALIA_NZ" : 0,
      "APAC/order-saga_PaymentSaga_1_CINA" : 2,
      "APAC/order-saga_PaymentSaga_1_INDIA" : 1,
      "EU/order-observer_NotificationObserver_1_*" : 0,
      "EU/order-query_OrderProjector_1_*" : 0,
      "EU/order-query_OrderProjector_1_AFRICA" : 33,
      "EU/order-query_OrderProjector_1_EUROPA" : 34,
      "EU/order-saga_PaymentSaga_1_AFRICA" : 33,
      "EU/order-saga_PaymentSaga_1_EUROPA" : 34
    }
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +7,9 s: scenario start
- +8,2 s: RTT to ES primary = 0 ms
- +8,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +49,3 s: load done: 280 requests
- +49,3 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={p50=65, failed=0, p99=186, ackedPerSecond=2.0}, AM={p50=77, failed=0, p99=511, ackedPerSecond=2.0}, APAC={p50=64, failed=0, p99=213, ackedPerSecond=3.0}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +49,5 s: RTT to ES primary = 50 ms
- +49,5 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +90,3 s: load done: 280 requests
- +90,4 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={p50=80, failed=0, p99=234, ackedPerSecond=2.0}, AM={p50=429, failed=0, p99=570, ackedPerSecond=2.0}, APAC={p50=440, failed=0, p99=593, ackedPerSecond=3.0}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +90,5 s: RTT to ES primary = 150 ms
- +90,6 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +136,5 s: load done: 280 requests
- +136,5 s: RTT 150 ms: {esAppendsPerSecond=7.0, EU={p50=1907, failed=0, p99=3384, ackedPerSecond=2.0}, AM={p50=2873, failed=0, p99=4664, ackedPerSecond=2.0}, APAC={p50=2844, failed=0, p99=4708, ackedPerSecond=3.0}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +136,7 s: RTT to ES primary = 300 ms
- +136,7 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +218,6 s: load done: 262 requests
- +218,6 s: RTT 300 ms: {esAppendsPerSecond=6.525, EU={p50=12911, failed=0, p99=19890, ackedPerSecond=2.0}, AM={p50=16580, failed=0, p99=26680, ackedPerSecond=2.0}, APAC={p50=22823, failed=32, p99=30472, ackedPerSecond=1.75}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +218,7 s: WAN baseline restored
- +218,7 s: invariants: converging up to 180 s
- +219,4 s: cleanup start
- +221,2 s: cleanup done

### stack actions

```
2026-10-15T13:55:00.414858100Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-15T13:55:00.478444900Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-15T13:55:00.529506100Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-10-15T13:55:00.574203300Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-10-15T13:55:41.763645900Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-15T13:55:41.807643Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-15T13:55:41.854243Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-10-15T13:55:41.903751900Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-10-15T13:56:22.822318500Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-15T13:56:22.869318800Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-15T13:56:22.915335200Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-10-15T13:56:22.961405100Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-10-15T13:57:08.975715300Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-15T13:57:09.019440Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-15T13:57:09.065032900Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-10-15T13:57:09.111400300Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-10-15T13:58:31.056957600Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-10-15T13:58:31.060958900Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-10-15T13:58:31.107198300Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-10-15T13:58:31.151199800Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

