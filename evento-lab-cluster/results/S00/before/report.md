# S00: Baseline, no faults

- Label: `before`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T08:20:18.182271600Z, finished: 2026-09-29T08:23:49.972915700Z (211 s)
- Verdicts: {UNEXPECTED=2, AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s00-before-c48984-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=2, completeRatio=0.5, ratePerMinutePerRegion=20.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **UNEXPECTED** | 100% of commands 2xx with no fault injected |  | 217/309 acknowledged, failures {timeout=92} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 217 acknowledged ops missing; 92 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **UNEXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | 2 duplicate finding(s) |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 140 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view_<r> contains only context R |  | 0 foreign row(s) in 140 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of order_view_<r> (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 71055 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 21 payments for 21 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 217 acknowledged ops missing; 92 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **FAIL** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | 2 duplicate finding(s) |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 140 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view_<r> contains only context R | 0 foreign row(s) in 140 |
| I5 | global completeness | **PASS** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 71055 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=309 ack=217 failed=92 p50=15255ms p95=30347ms p99=30463ms max=30506ms failures={timeout=92} |

<details><summary>I2: 2 violation(s)</summary>

- s00-before-c48984-europa-14: 4 x PaymentProcessedEvent
- payment for s00-before-c48984-europa-14 executed 4 times

</details>

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 309,
    "acked" : 217,
    "failed" : 92,
    "p50" : 15255,
    "p95" : 30347,
    "p99" : 30463,
    "max" : 30506,
    "kinds" : {
      "timeout" : 92
    }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 9997,
      "p95" : 16924,
      "p99" : 17229,
      "max" : 17229,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 39,
      "acked" : 22,
      "failed" : 17,
      "p50" : 25938,
      "p95" : 30463,
      "p99" : 30464,
      "max" : 30464,
      "kinds" : {
        "timeout" : 17
      }
    },
    "AUSTRALIA_NZ" : {
      "count" : 31,
      "acked" : 11,
      "failed" : 20,
      "p50" : 30142,
      "p95" : 30374,
      "p99" : 30375,
      "max" : 30375,
      "kinds" : {
        "timeout" : 20
      }
    },
    "CINA" : {
      "count" : 30,
      "acked" : 11,
      "failed" : 19,
      "p50" : 30071,
      "p95" : 30181,
      "p99" : 30506,
      "max" : 30506,
      "kinds" : {
        "timeout" : 19
      }
    },
    "EUROPA" : {
      "count" : 68,
      "acked" : 68,
      "failed" : 0,
      "p50" : 10501,
      "p95" : 17136,
      "p99" : 17644,
      "max" : 17644,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 32,
      "acked" : 13,
      "failed" : 19,
      "p50" : 30161,
      "p95" : 30336,
      "p99" : 30495,
      "max" : 30495,
      "kinds" : {
        "timeout" : 19
      }
    },
    "SUD_AMERICA" : {
      "count" : 41,
      "acked" : 24,
      "failed" : 17,
      "p50" : 24501,
      "p95" : 30243,
      "p99" : 30347,
      "max" : 30347,
      "kinds" : {
        "timeout" : 17
      }
    }
  },
  "esEventsAppended" : 278,
  "esAppendsPerSecond" : 4.633333333333334,
  "ackedCommandsPerSecond" : 3.6166666666666667,
  "commandsByOutcome" : {
    "ACK" : 217,
    "ERROR" : 92
  }
}
```

## Timeline

- +4,1 s: scenario start
- +4,3 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @20.0/min for 60 s
- +105,3 s: load done: 309 requests
- +105,4 s: invariants: converging up to 90 s
- +210,6 s: cleanup start
- +211,8 s: cleanup done

### stack actions

```
```

