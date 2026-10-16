# S04: Server returns after failover

- Label: `after-versioning`
- Gaps exercised: G7, G17
- Started: 2026-10-16T00:13:28.350347200Z, finished: 2026-10-16T00:18:01.885456100Z (273 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-after-versioning-b59fdf-`
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
      "Skipping unregister of bundle" : 5
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 11
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +8,0 s: scenario start
- +8,0 s: variant A: kill + restart
- +54,7 s: probe A-outage: 3/3 acknowledged {}
- +81,1 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +145,7 s: probe A-after: 3/3 acknowledged {}
- +146,1 s: variant B: pause 60 s
- +207,4 s: probe B-paused: 3/3 acknowledged {}
- +207,5 s: unpaused; settling 60 s
- +271,1 s: probe B-after: 3/3 acknowledged {}
- +271,6 s: cleanup start
- +273,5 s: cleanup done

### log excerpts: evento-eu

```
20261016001521329 INFO  1 --- [nioEventLoopGroup-3-16                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261016001522161 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261016001523441 INFO  1 --- [nioEventLoopGroup-3-15                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### log excerpts: evento-apac

```
20261016001656817 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261016001658695 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261016001658729 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-10-16T00:13:36.391159700Z docker kill evento-apac
2026-10-16T00:14:23.029303400Z docker start evento-apac
2026-10-16T00:15:54.418653300Z docker pause evento-apac
2026-10-16T00:16:55.727656Z docker unpause evento-apac
```

