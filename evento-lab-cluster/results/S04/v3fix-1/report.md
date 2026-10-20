# S04: Server returns after failover

- Label: `v3fix-1`
- Gaps exercised: G7, G17
- Started: 2026-10-20T17:12:03.953195200Z, finished: 2026-10-20T17:16:41.335697800Z (277 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3fix-1-694308-`
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
      "Skipping unregister of bundle" : 4
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 10
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,8 s: scenario start
- +7,8 s: variant A: kill + restart
- +54,9 s: probe A-outage: 3/3 acknowledged {}
- +87,8 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +150,7 s: probe A-after: 3/3 acknowledged {}
- +151,1 s: variant B: pause 60 s
- +212,5 s: probe B-paused: 3/3 acknowledged {}
- +212,6 s: unpaused; settling 60 s
- +274,9 s: probe B-after: 3/3 acknowledged {}
- +275,4 s: cleanup start
- +277,4 s: cleanup done

### log excerpts: evento-eu

```
20261020171403342 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020171406639 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020171407453 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: evento-apac

```
20261020171537385 INFO  1 --- [nioEventLoopGroup-3-9                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020171538515 INFO  1 --- [nioEventLoopGroup-3-3                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020171538724 INFO  1 --- [nioEventLoopGroup-3-15                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-10-20T17:12:11.730763800Z docker kill evento-apac
2026-10-20T17:12:58.889759500Z docker start evento-apac
2026-10-20T17:14:35.075346900Z docker pause evento-apac
2026-10-20T17:15:36.405687100Z docker unpause evento-apac
```

