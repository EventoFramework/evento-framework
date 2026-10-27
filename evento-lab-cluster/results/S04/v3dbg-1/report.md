# S04: Server returns after failover

- Label: `v3dbg-1`
- Gaps exercised: G7, G17
- Started: 2026-10-20T17:21:52.281397100Z, finished: 2026-10-20T17:26:22.683991Z (270 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3dbg-1-e63ab1-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| A: catalog after the killed server returns | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the returning/old server's NodeLeft clears the moved bundle's rows (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| A: APAC commands after the return | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |
| B: catalog after a half-closed socket heals | **KNOWN_DEFECT_REPRODUCED** | every bundle, handler and consumer still registered (I7) | the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7) | 0 bundle(s), 0 handler(s), 2 consumer(s) missing; 0 bundle(s) without handler rows |
| B: APAC commands after unpause | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I7-A | catalog matches live bundles (variant A) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I7-B | catalog matches live bundles (variant B) | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 2 consumer(s) missing; 0 bundle(s) without handler rows |

<details><summary>I7-B: 2 violation(s)</summary>

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
      "Skipping unregister of bundle" : 6
    },
    "evento-am" : { },
    "evento-apac" : {
      "Skipping unregister of bundle" : 3
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 9
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,7 s: scenario start
- +7,7 s: variant A: kill + restart
- +55,0 s: probe A-outage: 3/3 acknowledged {}
- +81,4 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +144,2 s: probe A-after: 3/3 acknowledged {}
- +144,6 s: variant B: pause 60 s
- +205,9 s: probe B-paused: 3/3 acknowledged {}
- +206,0 s: unpaused; settling 60 s
- +267,8 s: probe B-after: 3/3 acknowledged {}
- +268,3 s: cleanup start
- +270,4 s: cleanup done

### log excerpts: evento-eu

```
20261020172350405 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020172351008 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020172358098 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: evento-apac

```
20261020172519798 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020172522377 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020172525048 INFO  1 --- [nioEventLoopGroup-3-12                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### stack actions

```
2026-10-20T17:22:00.001577100Z docker kill evento-apac
2026-10-20T17:22:47.254558300Z docker start evento-apac
2026-10-20T17:24:16.895238200Z docker pause evento-apac
2026-10-20T17:25:18.203460200Z docker unpause evento-apac
```

