# S00: Baseline, no faults

- Label: `before`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T07:52:07.151627600Z, finished: 2026-09-29T07:55:53.110624600Z (225 s)
- Verdicts: {UNEXPECTED=3, AS_EXPECTED=6}

## Parameters

- orderIdPrefix: `s00-before-bf4af6-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{ratePerMinutePerRegion=20.0, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **UNEXPECTED** | 100% of commands 2xx with no fault injected |  | 226/320 acknowledged, failures {timeout=94} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 226 acknowledged ops missing; 94 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view_<r> contains only context R |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of order_view_<r> (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **UNEXPECTED** | read models reach the event store's state within 90 s |  | 8 order(s) still diverging after 92 s |
| I7 catalog matches live bundles | **UNEXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 1 consumer(s) missing; 6 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 29 payments for 29 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 226 acknowledged ops missing; 94 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view_<r> contains only context R | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **FAIL** | read models reach the event store's state within 90 s | 8 order(s) still diverging after 92 s |
| I7 | catalog matches live bundles | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 1 consumer(s) missing; 6 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=320 ack=226 failed=94 p50=15774ms p95=30404ms p99=30469ms max=30510ms failures={timeout=94} |

<details><summary>I6: 8 violation(s)</summary>

- s00-before-bf4af6-africa-79 global COMPLETED/2/255 != es COMPLETED/2/343
- s00-before-bf4af6-africa-86 global COMPLETED/2/267 != es COMPLETED/2/346
- s00-before-bf4af6-cina-5 global COMPLETED/2/258 != es COMPLETED/2/345
- s00-before-bf4af6-europa-102 global COMPLETED/2/285 != es COMPLETED/2/350
- s00-before-bf4af6-europa-81 global COMPLETED/2/257 != es COMPLETED/2/344
- s00-before-bf4af6-europa-88 global COMPLETED/2/269 != es COMPLETED/2/347
- s00-before-bf4af6-europa-95 global COMPLETED/2/279 != es COMPLETED/2/349
- s00-before-bf4af6-sud-america-20 global COMPLETED/2/276 != es COMPLETED/2/348

</details>

<details><summary>I7: 7 violation(s)</summary>

- bundle query-europa has no core__handler rows
- bundle query-america has no core__handler rows
- bundle query-sud_america has no core__handler rows
- bundle query-cina has no core__handler rows
- bundle query-india has no core__handler rows
- bundle query-australia_nz has no core__handler rows
- no core__consumer row for query-africa_RegionOrderProjector_*

</details>

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 320,
    "acked" : 226,
    "failed" : 94,
    "p50" : 15774,
    "p95" : 30404,
    "p99" : 30469,
    "max" : 30510,
    "kinds" : {
      "timeout" : 94
    }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 67,
      "acked" : 67,
      "failed" : 0,
      "p50" : 11254,
      "p95" : 17561,
      "p99" : 18358,
      "max" : 18358,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 42,
      "acked" : 25,
      "failed" : 17,
      "p50" : 25372,
      "p95" : 30436,
      "p99" : 30474,
      "max" : 30474,
      "kinds" : {
        "timeout" : 17
      }
    },
    "AUSTRALIA_NZ" : {
      "count" : 31,
      "acked" : 12,
      "failed" : 19,
      "p50" : 30373,
      "p95" : 30453,
      "p99" : 30469,
      "max" : 30469,
      "kinds" : {
        "timeout" : 19
      }
    },
    "CINA" : {
      "count" : 32,
      "acked" : 12,
      "failed" : 20,
      "p50" : 30060,
      "p95" : 30291,
      "p99" : 30510,
      "max" : 30510,
      "kinds" : {
        "timeout" : 20
      }
    },
    "EUROPA" : {
      "count" : 76,
      "acked" : 76,
      "failed" : 0,
      "p50" : 11586,
      "p95" : 17902,
      "p99" : 18326,
      "max" : 18326,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 31,
      "acked" : 11,
      "failed" : 20,
      "p50" : 30116,
      "p95" : 30412,
      "p99" : 30461,
      "max" : 30461,
      "kinds" : {
        "timeout" : 20
      }
    },
    "SUD_AMERICA" : {
      "count" : 41,
      "acked" : 23,
      "failed" : 18,
      "p50" : 27531,
      "p95" : 30351,
      "p99" : 30475,
      "max" : 30475,
      "kinds" : {
        "timeout" : 18
      }
    }
  },
  "esEventsAppended" : 295,
  "esAppendsPerSecond" : 4.916666666666667,
  "ackedCommandsPerSecond" : 3.7666666666666666,
  "commandsByOutcome" : {
    "ACK" : 226,
    "ERROR" : 94
  }
}
```

## Timeline

- +4,9 s: scenario start
- +5,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +110,5 s: load done: 320 requests
- +110,5 s: invariants: converging up to 90 s
- +224,8 s: cleanup start
- +226,0 s: cleanup done

### stack actions

```
```

