# S00: Baseline, no faults

- Label: `before-rate3`
- Gaps exercised: G9 (reference)
- Started: 2026-09-29T08:24:34.858670200Z, finished: 2026-09-29T08:25:41.102863200Z (66 s)
- Verdicts: {AS_EXPECTED=9}

## Parameters

- orderIdPrefix: `s00-before-rate3-5347b4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{ratePerMinutePerRegion=3.0, completeRatio=0.5, itemsPerOrder=2, durationSeconds=60, regions=[EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| every command acknowledged | **AS_EXPECTED** | 100% of commands 2xx with no fault injected |  | 75/75 acknowledged, failures {} |
| I1 no lost events | **AS_EXPECTED** | every 2xx-acknowledged command has its event in es__events |  | 0 of 75 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 no duplicate events or side effects | **AS_EXPECTED** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 |  | no duplicates |
| I3 no aggregate fork | **AS_EXPECTED** | every story is valid under the aggregate's rules; sequence numbers unique |  | 0 of 21 aggregates invalid |
| I4 regional isolation | **AS_EXPECTED** | order_view_<r> contains only context R |  | 0 foreign row(s) in 21 |
| I5 global completeness | **AS_EXPECTED** | order_view_global = union of order_view_<r> (same ids, status, item count, context) |  | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 convergence | **AS_EXPECTED** | read models reach the event store's state within 90 s |  | converged in 91 ms |
| I7 catalog matches live bundles | **AS_EXPECTED** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer |  | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| payment saga completes every order | **AS_EXPECTED** | one PaymentProcessed per completed order within 60 s |  | 12 payments for 12 completed orders |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I1 | no lost events | **PASS** | every 2xx-acknowledged command has its event in es__events | 0 of 75 acknowledged ops missing; 0 unacknowledged ops were stored anyway |
| I2 | no duplicate events or side effects | **PASS** | one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1 | no duplicates |
| I3 | no aggregate fork | **PASS** | every story is valid under the aggregate's rules; sequence numbers unique | 0 of 21 aggregates invalid |
| I4 | regional isolation | **PASS** | order_view_<r> contains only context R | 0 foreign row(s) in 21 |
| I5 | global completeness | **PASS** | order_view_global = union of order_view_<r> (same ids, status, item count, context) | 0 missing in global, 0 missing regionally, 0 mismatched |
| I6 | convergence | **PASS** | read models reach the event store's state within 90 s | converged in 91 ms |
| I7 | catalog matches live bundles | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I8 | command latency per region | **OBSERVED** | recorded as a baseline (p50/p95/p99), not a pass/fail | n=75 ack=75 failed=0 p50=2409ms p95=4592ms p99=4883ms max=4883ms |

## Metrics

```json
{
  "latencyOverall" : {
    "count" : 75,
    "acked" : 75,
    "failed" : 0,
    "p50" : 2409,
    "p95" : 4592,
    "p99" : 4883,
    "max" : 4883,
    "kinds" : { }
  },
  "latencyByRegion" : {
    "AFRICA" : {
      "count" : 10,
      "acked" : 10,
      "failed" : 0,
      "p50" : 172,
      "p95" : 588,
      "p99" : 588,
      "max" : 588,
      "kinds" : { }
    },
    "AMERICA" : {
      "count" : 11,
      "acked" : 11,
      "failed" : 0,
      "p50" : 2369,
      "p95" : 2592,
      "p99" : 2592,
      "max" : 2592,
      "kinds" : { }
    },
    "AUSTRALIA_NZ" : {
      "count" : 12,
      "acked" : 12,
      "failed" : 0,
      "p50" : 3678,
      "p95" : 4717,
      "p99" : 4717,
      "max" : 4717,
      "kinds" : { }
    },
    "CINA" : {
      "count" : 10,
      "acked" : 10,
      "failed" : 0,
      "p50" : 3649,
      "p95" : 4447,
      "p99" : 4447,
      "max" : 4447,
      "kinds" : { }
    },
    "EUROPA" : {
      "count" : 11,
      "acked" : 11,
      "failed" : 0,
      "p50" : 178,
      "p95" : 1269,
      "p99" : 1269,
      "max" : 1269,
      "kinds" : { }
    },
    "INDIA" : {
      "count" : 11,
      "acked" : 11,
      "failed" : 0,
      "p50" : 3583,
      "p95" : 4883,
      "p99" : 4883,
      "max" : 4883,
      "kinds" : { }
    },
    "SUD_AMERICA" : {
      "count" : 10,
      "acked" : 10,
      "failed" : 0,
      "p50" : 2443,
      "p95" : 2655,
      "p99" : 2655,
      "max" : 2655,
      "kinds" : { }
    }
  },
  "esEventsAppended" : 87,
  "esAppendsPerSecond" : 1.45,
  "ackedCommandsPerSecond" : 1.25,
  "commandsByOutcome" : {
    "ACK" : 75
  }
}
```

## Timeline

- +3,9 s: scenario start
- +4,1 s: load start [EUROPA, AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @3.0/min for 60 s
- +64,7 s: load done: 75 requests
- +64,8 s: invariants: converging up to 90 s
- +65,1 s: cleanup start
- +66,2 s: cleanup done

### stack actions

```
```

