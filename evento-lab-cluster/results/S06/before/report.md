# S06: WAN latency sweep to the ES primary

- Label: `before`
- Gaps exercised: G9, G18
- Started: 2026-09-29T08:52:48.246069100Z, finished: 2026-09-29T09:00:18.375847200Z (450 s)
- Verdicts: {AS_EXPECTED=4, UNEXPECTED=2}

## Parameters

- orderIdPrefix: `s06-before-4e572e-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- proxiesTowardsEsPrimary: `[am-to-es-pg-eu, apac-to-es-pg-eu]`
- rttsMs: `[0, 50, 150, 300]`
- wanBaselinePresent: `true`
- load: `{ratePerMinutePerRegion=30.0, completeRatio=0.0, itemsPerOrder=1, durationSeconds=40, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| I1 across the sweep | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 703 acknowledged ops missing; 201 unacknowledged ops were stored anyway |
| I2 across the sweep | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 across the sweep | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 537 aggregates invalid |
| I4 across the sweep | **AS_EXPECTED** | order_view_<r> contains only context R |  | 0 foreign row(s) in 537 |
| I5 across the sweep | **UNEXPECTED** | order_view_global = union of order_view_<r> (same ids, status, item count, context) |  | 195 missing in global, 0 missing regionally, 30 mismatched |
| I6 across the sweep | **UNEXPECTED** | read models reach the event store's state within 180 s |  | 225 order(s) still diverging after 180 s |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 703 acknowledged ops missing; 201 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 537 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view_<r> contains only context R | 0 foreign row(s) in 537 |
| I5 | global completeness | **FAIL** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 195 missing in global, 0 missing regionally, 30 mismatched |
| I6 | convergence | **FAIL** | read models reach the event store's state within 180 s | 225 order(s) still diverging after 180 s |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=927 ack=703 failed=224 p50=5978ms p95=30464ms p99=30502ms max=30557ms failures={timeout=224} |

<details><summary>I5: 225 violation(s)</summary>

- s06-before-4e572e-europa-345 global=CREATED/0/EUROPA regional=CREATED/1/EUROPA
- s06-before-4e572e-europa-352 global=CREATED/0/EUROPA regional=CREATED/1/EUROPA
- s06-before-4e572e-europa-359 global=CREATED/0/EUROPA regional=CREATED/1/EUROPA
- s06-before-4e572e-europa-366 global=CREATED/0/EUROPA regional=CREATED/1/EUROPA
- s06-before-4e572e-europa-373 global=CREATED/0/EUROPA regional=CREATED/1/EUROPA
- s06-before-4e572e-europa-380 in order_view_europa but not in global
- s06-before-4e572e-europa-387 in order_view_europa but not in global
- s06-before-4e572e-europa-394 in order_view_europa but not in global
- s06-before-4e572e-europa-401 in order_view_europa but not in global
- s06-before-4e572e-europa-408 in order_view_europa but not in global
- s06-before-4e572e-europa-415 in order_view_europa but not in global
- s06-before-4e572e-europa-421 in order_view_europa but not in global
- s06-before-4e572e-europa-428 in order_view_europa but not in global
- s06-before-4e572e-europa-435 in order_view_europa but not in global
- s06-before-4e572e-europa-442 in order_view_europa but not in global
- s06-before-4e572e-europa-449 in order_view_europa but not in global
- s06-before-4e572e-europa-456 in order_view_europa but not in global
- s06-before-4e572e-europa-463 in order_view_europa but not in global
- s06-before-4e572e-europa-470 in order_view_europa but not in global
- s06-before-4e572e-europa-477 in order_view_europa but not in global
- s06-before-4e572e-europa-484 in order_view_europa but not in global
- s06-before-4e572e-europa-491 in order_view_europa but not in global
- s06-before-4e572e-europa-498 in order_view_europa but not in global
- s06-before-4e572e-europa-505 in order_view_europa but not in global
- s06-before-4e572e-europa-512 in order_view_europa but not in global
- s06-before-4e572e-europa-519 in order_view_europa but not in global
- s06-before-4e572e-europa-526 in order_view_europa but not in global
- s06-before-4e572e-europa-533 in order_view_europa but not in global
- s06-before-4e572e-europa-540 in order_view_europa but not in global
- s06-before-4e572e-europa-547 in order_view_europa but not in global
- s06-before-4e572e-europa-554 in order_view_europa but not in global
- s06-before-4e572e-africa-349 global=CREATED/0/AFRICA regional=CREATED/1/AFRICA
- s06-before-4e572e-africa-356 global=CREATED/0/AFRICA regional=CREATED/1/AFRICA
- s06-before-4e572e-africa-363 global=CREATED/0/AFRICA regional=CREATED/1/AFRICA
- s06-before-4e572e-africa-370 global=CREATED/0/AFRICA regional=CREATED/1/AFRICA
- s06-before-4e572e-africa-377 global=CREATED/0/AFRICA regional=CREATED/1/AFRICA
- s06-before-4e572e-africa-384 in order_view_africa but not in global
- s06-before-4e572e-africa-391 in order_view_africa but not in global
- s06-before-4e572e-africa-398 in order_view_africa but not in global
- s06-before-4e572e-africa-405 in order_view_africa but not in global
- s06-before-4e572e-africa-412 in order_view_africa but not in global
- s06-before-4e572e-africa-419 in order_view_africa but not in global
- s06-before-4e572e-africa-423 in order_view_africa but not in global
- s06-before-4e572e-africa-430 in order_view_africa but not in global
- s06-before-4e572e-africa-437 in order_view_africa but not in global
- s06-before-4e572e-africa-444 in order_view_africa but not in global
- s06-before-4e572e-africa-451 in order_view_africa but not in global
- s06-before-4e572e-africa-458 in order_view_africa but not in global
- s06-before-4e572e-africa-465 in order_view_africa but not in global
- s06-before-4e572e-africa-472 in order_view_africa but not in global
- ... 175 more

</details>

<details><summary>I6: 225 violation(s)</summary>

- s06-before-4e572e-africa-349 global CREATED/0/910 != es CREATED/1/951
- s06-before-4e572e-africa-356 global CREATED/0/918 != es CREATED/1/963
- s06-before-4e572e-africa-363 global CREATED/0/924 != es CREATED/1/975
- s06-before-4e572e-africa-370 global CREATED/0/930 != es CREATED/1/981
- s06-before-4e572e-africa-377 global CREATED/0/936 != es CREATED/1/985
- s06-before-4e572e-africa-384 missing in global
- s06-before-4e572e-africa-391 missing in global
- s06-before-4e572e-africa-398 missing in global
- s06-before-4e572e-africa-405 missing in global
- s06-before-4e572e-africa-412 missing in global
- s06-before-4e572e-africa-419 missing in global
- s06-before-4e572e-africa-423 missing in global
- s06-before-4e572e-africa-430 missing in global
- s06-before-4e572e-africa-437 missing in global
- s06-before-4e572e-africa-444 missing in global
- s06-before-4e572e-africa-451 missing in global
- s06-before-4e572e-africa-458 missing in global
- s06-before-4e572e-africa-465 missing in global
- s06-before-4e572e-africa-472 missing in global
- s06-before-4e572e-africa-479 missing in global
- s06-before-4e572e-africa-486 missing in global
- s06-before-4e572e-africa-493 missing in global
- s06-before-4e572e-africa-500 missing in global
- s06-before-4e572e-africa-507 missing in global
- s06-before-4e572e-africa-514 missing in global
- s06-before-4e572e-africa-521 missing in global
- s06-before-4e572e-africa-528 missing in global
- s06-before-4e572e-africa-535 missing in global
- s06-before-4e572e-africa-542 missing in global
- s06-before-4e572e-africa-549 missing in global
- s06-before-4e572e-africa-556 missing in global
- s06-before-4e572e-america-305 global CREATED/0/892 != es CREATED/1/972
- s06-before-4e572e-america-312 global CREATED/0/900 != es CREATED/1/1000
- s06-before-4e572e-america-319 global CREATED/0/907 != es CREATED/1/1023
- s06-before-4e572e-america-326 global CREATED/0/920 != es CREATED/1/1056
- s06-before-4e572e-america-333 global CREATED/0/931 != es CREATED/1/1072
- s06-before-4e572e-america-340 missing in global
- s06-before-4e572e-america-347 missing in global
- s06-before-4e572e-america-354 missing in global
- s06-before-4e572e-america-361 missing in global
- s06-before-4e572e-america-368 missing in global
- s06-before-4e572e-america-375 missing in global
- s06-before-4e572e-america-382 missing in global
- s06-before-4e572e-america-389 missing in global
- s06-before-4e572e-america-396 missing in global
- s06-before-4e572e-america-403 missing in global
- s06-before-4e572e-america-410 missing in global
- s06-before-4e572e-america-417 missing in global
- s06-before-4e572e-america-425 missing in global
- s06-before-4e572e-america-432 missing in global
- ... 175 more

</details>

## Metrics

```json
{
  "throughputCurve" : {
    "0ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 115,
        "ackedPerSecond" : 2.0,
        "p50" : 24,
        "failed" : 0
      },
      "AM" : {
        "p99" : 129,
        "ackedPerSecond" : 2.0,
        "p50" : 25,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 134,
        "ackedPerSecond" : 3.0,
        "p50" : 25,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : null
    },
    "50ms" : {
      "esAppendsPerSecond" : 7.0,
      "EU" : {
        "p99" : 1283,
        "ackedPerSecond" : 2.0,
        "p50" : 649,
        "failed" : 0
      },
      "AM" : {
        "p99" : 7389,
        "ackedPerSecond" : 2.0,
        "p50" : 4433,
        "failed" : 0
      },
      "APAC" : {
        "p99" : 17926,
        "ackedPerSecond" : 3.0,
        "p50" : 11793,
        "failed" : 0
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 5.0
    },
    "150ms" : {
      "esAppendsPerSecond" : 4.025,
      "EU" : {
        "p99" : 28492,
        "ackedPerSecond" : 2.0,
        "p50" : 15971,
        "failed" : 0
      },
      "AM" : {
        "p99" : 30495,
        "ackedPerSecond" : 0.55,
        "p50" : 30125,
        "failed" : 34
      },
      "APAC" : {
        "p99" : 30495,
        "ackedPerSecond" : 0.475,
        "p50" : 30218,
        "failed" : 56
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 1.6666666666666667
    },
    "300ms" : {
      "esAppendsPerSecond" : 1.775,
      "EU" : {
        "p99" : 30491,
        "ackedPerSecond" : 0.55,
        "p50" : 30242,
        "failed" : 34
      },
      "AM" : {
        "p99" : 30301,
        "ackedPerSecond" : 0.0,
        "p50" : 30223,
        "failed" : 40
      },
      "APAC" : {
        "p99" : 30557,
        "ackedPerSecond" : 0.0,
        "p50" : 30153,
        "failed" : 60
      },
      "predictedPre-G9RemoteCellAppendsPerSecond" : 0.8333333333333334
    }
  },
  "failedRequestsByError" : {
    "timeout: AddOrderItemCommand: request expired after 30022ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30047ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30074ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30083ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30089ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30092ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30102ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30148ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30166ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30175ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30176ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30189ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30219ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30221ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30228ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30236ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30239ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30279ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30288ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30298ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30318ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30386ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30391ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30413ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30429ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30440ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30441ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30456ms (timeout=30000)" : 1,
    "timeout: AddOrderItemCommand: request expired after 30485ms (timeout=30000)" : 2,
    "timeout: AddOrderItemCommand: request expired after 30491ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30002ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30003ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30004ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30005ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30007ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30009ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30012ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30013ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30014ms (timeout=30000)" : 6,
    "timeout: CreateOrderCommand: request expired after 30015ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30101ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30107ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30108ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30109ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30110ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30117ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30118ms (timeout=30000)" : 8,
    "timeout: CreateOrderCommand: request expired after 30119ms (timeout=30000)" : 6,
    "timeout: CreateOrderCommand: request expired after 30120ms (timeout=30000)" : 7,
    "timeout: CreateOrderCommand: request expired after 30121ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30122ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30208ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30210ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30211ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30212ms (timeout=30000)" : 6,
    "timeout: CreateOrderCommand: request expired after 30213ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30214ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30217ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30218ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30220ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30224ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30225ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30226ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30231ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30232ms (timeout=30000)" : 5,
    "timeout: CreateOrderCommand: request expired after 30233ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30234ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30235ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30245ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30247ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30248ms (timeout=30000)" : 5,
    "timeout: CreateOrderCommand: request expired after 30249ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30250ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30251ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30260ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30261ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30262ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30263ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30264ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30265ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30338ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30342ms (timeout=30000)" : 6,
    "timeout: CreateOrderCommand: request expired after 30343ms (timeout=30000)" : 7,
    "timeout: CreateOrderCommand: request expired after 30344ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30456ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30457ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30458ms (timeout=30000)" : 5,
    "timeout: CreateOrderCommand: request expired after 30459ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30461ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30479ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30480ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30481ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30482ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30484ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30485ms (timeout=30000)" : 5,
    "timeout: CreateOrderCommand: request expired after 30486ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30487ms (timeout=30000)" : 4,
    "timeout: CreateOrderCommand: request expired after 30488ms (timeout=30000)" : 5,
    "timeout: CreateOrderCommand: request expired after 30489ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30490ms (timeout=30000)" : 3,
    "timeout: CreateOrderCommand: request expired after 30491ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30492ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30494ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30496ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30497ms (timeout=30000)" : 1,
    "timeout: CreateOrderCommand: request expired after 30498ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30499ms (timeout=30000)" : 2,
    "timeout: CreateOrderCommand: request expired after 30501ms (timeout=30000)" : 1
  }
}
```

## Notes

- Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.

## Timeline

- +3,8 s: scenario start
- +3,9 s: RTT to ES primary = 0 ms
- +4,0 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +44,8 s: load done: 280 requests
- +44,8 s: RTT 0 ms: {esAppendsPerSecond=7.0, EU={p99=115, ackedPerSecond=2.0, p50=24, failed=0}, AM={p99=129, ackedPerSecond=2.0, p50=25, failed=0}, APAC={p99=134, ackedPerSecond=3.0, p50=25, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=null}
- +44,8 s: RTT to ES primary = 50 ms
- +44,8 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +109,9 s: load done: 280 requests
- +109,9 s: RTT 50 ms: {esAppendsPerSecond=7.0, EU={p99=1283, ackedPerSecond=2.0, p50=649, failed=0}, AM={p99=7389, ackedPerSecond=2.0, p50=4433, failed=0}, APAC={p99=17926, ackedPerSecond=3.0, p50=11793, failed=0}, predictedPre-G9RemoteCellAppendsPerSecond=5.0}
- +109,9 s: RTT to ES primary = 150 ms
- +109,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +193,9 s: load done: 211 requests
- +193,9 s: RTT 150 ms: {esAppendsPerSecond=4.025, EU={p99=28492, ackedPerSecond=2.0, p50=15971, failed=0}, AM={p99=30495, ackedPerSecond=0.55, p50=30125, failed=34}, APAC={p99=30495, ackedPerSecond=0.475, p50=30218, failed=56}, predictedPre-G9RemoteCellAppendsPerSecond=1.6666666666666667}
- +193,9 s: RTT to ES primary = 300 ms
- +193,9 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @30.0/min for 40 s
- +267,9 s: load done: 156 requests
- +267,9 s: RTT 300 ms: {esAppendsPerSecond=1.775, EU={p99=30491, ackedPerSecond=0.55, p50=30242, failed=34}, AM={p99=30301, ackedPerSecond=0.0, p50=30223, failed=40}, APAC={p99=30557, ackedPerSecond=0.0, p50=30153, failed=60}, predictedPre-G9RemoteCellAppendsPerSecond=0.8333333333333334}
- +267,9 s: WAN baseline restored
- +267,9 s: invariants: converging up to 180 s
- +449,0 s: cleanup start
- +450,1 s: cleanup done

### stack actions

```
2026-09-29T08:52:52.061408800Z set am-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T08:52:52.092754900Z set am-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T08:52:52.100799300Z set apac-to-es-pg-eu/wan-latency-up latency=0 jitter=0
2026-09-29T08:52:52.104565300Z set apac-to-es-pg-eu/wan-latency-down latency=0 jitter=0
2026-09-29T08:53:33.038941600Z set am-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T08:53:33.041570700Z set am-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T08:53:33.046292900Z set apac-to-es-pg-eu/wan-latency-up latency=25 jitter=5
2026-09-29T08:53:33.048958600Z set apac-to-es-pg-eu/wan-latency-down latency=25 jitter=5
2026-09-29T08:54:38.123705600Z set am-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T08:54:38.125452200Z set am-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T08:54:38.130216400Z set apac-to-es-pg-eu/wan-latency-up latency=75 jitter=5
2026-09-29T08:54:38.132368600Z set apac-to-es-pg-eu/wan-latency-down latency=75 jitter=5
2026-09-29T08:56:02.156512500Z set am-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T08:56:02.159195Z set am-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T08:56:02.163145600Z set apac-to-es-pg-eu/wan-latency-up latency=150 jitter=5
2026-09-29T08:56:02.165828Z set apac-to-es-pg-eu/wan-latency-down latency=150 jitter=5
2026-09-29T08:57:16.116092500Z restore am-to-es-pg-eu/wan-latency-up {latency=40, jitter=5}
2026-09-29T08:57:16.120590700Z restore am-to-es-pg-eu/wan-latency-down {latency=40, jitter=5}
2026-09-29T08:57:16.122195100Z restore apac-to-es-pg-eu/wan-latency-up {latency=75, jitter=5}
2026-09-29T08:57:16.124934900Z restore apac-to-es-pg-eu/wan-latency-down {latency=75, jitter=5}
```

