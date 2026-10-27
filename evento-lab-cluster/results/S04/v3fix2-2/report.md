# S04: Server returns after failover

- Label: `v3fix2-2`
- Gaps exercised: G7, G17
- Started: 2026-10-20T17:42:59.584609100Z, finished: 2026-10-20T17:47:30.411365900Z (270 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-v3fix2-2-ad2fb3-`
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
    "evento-eu" : {
      "Skipping unregister of bundle" : 6
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 6
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,9 s: scenario start
- +7,9 s: variant A: kill + restart
- +55,0 s: probe A-outage: 3/3 acknowledged {}
- +81,5 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +144,2 s: probe A-after: 3/3 acknowledged {}
- +144,6 s: variant B: pause 60 s
- +206,0 s: probe B-paused: 3/3 acknowledged {}
- +206,1 s: unpaused; settling 60 s
- +268,4 s: probe B-after: 3/3 acknowledged {}
- +268,8 s: cleanup start
- +270,8 s: cleanup done

### log excerpts: evento-eu

```
20261020174455990 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020174456468 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020174503293 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-20T17:43:07.476160900Z docker kill evento-apac
2026-10-20T17:43:54.626582Z docker start evento-apac
2026-10-20T17:45:24.196661700Z docker pause evento-apac
2026-10-20T17:46:25.536597700Z docker unpause evento-apac
```

