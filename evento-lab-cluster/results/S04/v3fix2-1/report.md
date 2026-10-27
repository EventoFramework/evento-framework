# S04: Server returns after failover

- Label: `v3fix2-1`
- Gaps exercised: G7, G17
- Started: 2026-10-20T17:34:27.128081Z, finished: 2026-10-20T17:39:04.872393900Z (277 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-v3fix2-1-e8244f-`
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
- +55,2 s: probe A-outage: 3/3 acknowledged {}
- +88,0 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +151,0 s: probe A-after: 3/3 acknowledged {}
- +151,4 s: variant B: pause 60 s
- +212,7 s: probe B-paused: 3/3 acknowledged {}
- +212,8 s: unpaused; settling 60 s
- +275,3 s: probe B-after: 3/3 acknowledged {}
- +275,7 s: cleanup start
- +277,7 s: cleanup done

### log excerpts: evento-eu

```
20261020173624152 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020173626723 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020173627241 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-20T17:34:35.042816Z docker kill evento-apac
2026-10-20T17:35:22.304942600Z docker start evento-apac
2026-10-20T17:36:58.530691600Z docker pause evento-apac
2026-10-20T17:37:59.842229100Z docker unpause evento-apac
```

