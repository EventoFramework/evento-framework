# S04: Server returns after failover

- Label: `after-query`
- Gaps exercised: G7, G17
- Started: 2026-10-15T13:45:13.488130100Z, finished: 2026-10-15T13:49:47.859082100Z (274 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-after-query-1cccfa-`
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
    "evento-apac" : {
      "Skipping unregister of bundle" : 7
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 13
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +8,2 s: scenario start
- +8,2 s: variant A: kill + restart
- +54,9 s: probe A-outage: 3/3 acknowledged {}
- +81,3 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +145,9 s: probe A-after: 3/3 acknowledged {}
- +146,3 s: variant B: pause 60 s
- +207,6 s: probe B-paused: 3/3 acknowledged {}
- +207,8 s: unpaused; settling 60 s
- +271,8 s: probe B-after: 3/3 acknowledged {}
- +272,4 s: cleanup start
- +274,4 s: cleanup done

### log excerpts: evento-eu

```
20261015134707628 INFO  1 --- [nioEventLoopGroup-3-1                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261015134708070 INFO  1 --- [nioEventLoopGroup-3-15                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261015134713792 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: evento-apac

```
20261015134842141 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261015134842311 INFO  1 --- [nioEventLoopGroup-3-14                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261015134843575 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-15T13:45:21.732206900Z docker kill evento-apac
2026-10-15T13:46:08.397417900Z docker start evento-apac
2026-10-15T13:47:39.779372600Z docker pause evento-apac
2026-10-15T13:48:41.130857500Z docker unpause evento-apac
```

