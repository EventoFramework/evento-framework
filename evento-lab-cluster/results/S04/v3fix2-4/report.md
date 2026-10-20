# S04: Server returns after failover

- Label: `v3fix2-4`
- Gaps exercised: G7, G17
- Started: 2026-10-20T18:00:07.744878700Z, finished: 2026-10-20T18:04:44.841496Z (277 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-v3fix2-4-e0e227-`
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
      "Skipping unregister of bundle" : 7
    },
    "evento-am" : { },
    "evento-apac" : { }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 7
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,6 s: scenario start
- +7,7 s: variant A: kill + restart
- +54,9 s: probe A-outage: 3/3 acknowledged {}
- +87,8 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +150,5 s: probe A-after: 3/3 acknowledged {}
- +150,8 s: variant B: pause 60 s
- +212,2 s: probe B-paused: 3/3 acknowledged {}
- +212,3 s: unpaused; settling 60 s
- +274,6 s: probe B-after: 3/3 acknowledged {}
- +275,2 s: cleanup start
- +277,1 s: cleanup done

### log excerpts: evento-eu

```
20261020180205319 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020180209481 INFO  1 --- [nioEventLoopGroup-3-9                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020180212461 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### stack actions

```
2026-10-20T18:00:15.398401500Z docker kill evento-apac
2026-10-20T18:01:02.602081900Z docker start evento-apac
2026-10-20T18:02:38.581685Z docker pause evento-apac
2026-10-20T18:03:39.929612200Z docker unpause evento-apac
```

