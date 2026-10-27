# S04: Server returns after failover

- Label: `v3e`
- Gaps exercised: G7, G17
- Started: 2026-10-22T15:56:53.065105900Z, finished: 2026-10-22T16:01:22.864996600Z (269 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-v3e-9f5071-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| A: catalog after the killed server returns | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the returning/old server's NodeLeft clears the moved bundle's rows (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| A: APAC commands after the return | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |
| B: catalog after a half-closed socket heals | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| B: APAC commands after unpause | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I7-A | catalog matches live bundles (variant A) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I7-B | catalog matches live bundles (variant B) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |

## Metrics

```json
{
  "A.probesDuringOutage" : "3/3 acknowledged {}",
  "B.probesDuringPause" : "3/3 acknowledged {}",
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 0
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,8 s: scenario start
- +7,8 s: variant A: kill + restart
- +54,4 s: probe A-outage: 3/3 acknowledged {}
- +80,8 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +143,3 s: probe A-after: 3/3 acknowledged {}
- +143,7 s: variant B: pause 60 s
- +205,0 s: probe B-paused: 3/3 acknowledged {}
- +205,1 s: unpaused; settling 60 s
- +267,4 s: probe B-after: 3/3 acknowledged {}
- +267,9 s: cleanup start
- +269,8 s: cleanup done

### stack actions

```
2026-10-22T15:57:00.860184800Z docker kill evento-apac
2026-10-22T15:57:47.456387300Z docker start evento-apac
2026-10-22T15:59:16.724937500Z docker pause evento-apac
2026-10-22T16:00:18.057444800Z docker unpause evento-apac
```

