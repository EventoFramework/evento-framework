# S04: Server returns after failover

- Label: `v3c`
- Gaps exercised: G7, G17
- Started: 2026-10-22T12:59:23.235877900Z, finished: 2026-10-22T13:03:59.412637700Z (276 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3c-d3edb6-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| A: catalog after the killed server returns | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the returning/old server's NodeLeft clears the moved bundle's rows (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| A: APAC commands after the return | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |
| B: catalog after a half-closed socket heals | **KNOWN_DEFECT_REPRODUCED** | every bundle, handler and consumer still registered (I7) | the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7) | 0 bundle(s), 0 handler(s), 3 consumer(s) missing; 0 bundle(s) without handler rows |
| B: APAC commands after unpause | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I7-A | catalog matches live bundles (variant A) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I7-B | catalog matches live bundles (variant B) | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 3 consumer(s) missing; 0 bundle(s) without handler rows |

<details><summary>I7-B: 3 violation(s)</summary>

- no core__consumer row for order-query_OrderProjector_1_CINA*
- no core__consumer row for order-query_OrderProjector_1_INDIA*
- no core__consumer row for order-query_OrderProjector_1_AUSTRALIA_NZ*

</details>

## Metrics

```json
{
  "A.probesDuringOutage" : "3/3 acknowledged {}",
  "B.probesDuringPause" : "3/3 acknowledged {}",
  "logMarkers" : {
    "evento-eu" : {
      "Skipping unregister of bundle" : 4
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 4
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +9,0 s: scenario start
- +9,0 s: variant A: kill + restart
- +55,7 s: probe A-outage: 3/3 acknowledged {}
- +86,6 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +149,4 s: probe A-after: 3/3 acknowledged {}
- +149,7 s: variant B: pause 60 s
- +211,1 s: probe B-paused: 3/3 acknowledged {}
- +211,2 s: unpaused; settling 60 s
- +273,5 s: probe B-after: 3/3 acknowledged {}
- +274,1 s: cleanup start
- +276,2 s: cleanup done

### log excerpts: evento-eu

```
20261022130121618 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261022130121867 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261022130331671 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### stack actions

```
2026-10-22T12:59:32.240933800Z docker kill evento-apac
2026-10-22T13:00:18.919748800Z docker start evento-apac
2026-10-22T13:01:52.953881700Z docker pause evento-apac
2026-10-22T13:02:54.306000100Z docker unpause evento-apac
```

