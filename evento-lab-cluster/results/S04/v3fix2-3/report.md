# S04: Server returns after failover

- Label: `v3fix2-3`
- Gaps exercised: G7, G17
- Started: 2026-10-20T17:51:26.596964400Z, finished: 2026-10-20T17:56:04.214244700Z (277 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s04-v3fix2-3-2c77e2-`
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

- +7,9 s: scenario start
- +7,9 s: variant A: kill + restart
- +55,2 s: probe A-outage: 3/3 acknowledged {}
- +88,1 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +151,0 s: probe A-after: 3/3 acknowledged {}
- +151,5 s: variant B: pause 60 s
- +212,8 s: probe B-paused: 3/3 acknowledged {}
- +212,9 s: unpaused; settling 60 s
- +275,2 s: probe B-after: 3/3 acknowledged {}
- +275,6 s: cleanup start
- +277,6 s: cleanup done

### log excerpts: evento-eu

```
20261020175323942 INFO  1 --- [nioEventLoopGroup-3-9                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020175326258 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020175338162 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-10-20T17:51:34.524980300Z docker kill evento-apac
2026-10-20T17:52:21.817190100Z docker start evento-apac
2026-10-20T17:53:58.058498900Z docker pause evento-apac
2026-10-20T17:54:59.365989800Z docker unpause evento-apac
```

