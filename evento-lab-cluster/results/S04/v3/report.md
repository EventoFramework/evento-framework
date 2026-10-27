# S04: Server returns after failover

- Label: `v3`
- Gaps exercised: G7, G17
- Started: 2026-10-20T15:12:15.504224Z, finished: 2026-10-20T15:16:45.271661900Z (269 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3-2182dc-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| A: catalog after the killed server returns | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the returning/old server's NodeLeft clears the moved bundle's rows (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| A: APAC commands after the return | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |
| B: catalog after a half-closed socket heals | **KNOWN_DEFECT_REPRODUCED** | every bundle, handler and consumer still registered (I7) | the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7) | 0 bundle(s), 0 handler(s), 1 consumer(s) missing; 0 bundle(s) without handler rows |
| B: APAC commands after unpause | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I7-A | catalog matches live bundles (variant A) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I7-B | catalog matches live bundles (variant B) | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 1 consumer(s) missing; 0 bundle(s) without handler rows |

<details><summary>I7-B: 1 violation(s)</summary>

- no core__consumer row for order-query_OrderProjector_1_CINA*

</details>

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
    "evento-apac" : {
      "Skipping unregister of bundle" : 7
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 14
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,6 s: scenario start
- +7,6 s: variant A: kill + restart
- +54,2 s: probe A-outage: 3/3 acknowledged {}
- +80,7 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +143,3 s: probe A-after: 3/3 acknowledged {}
- +143,7 s: variant B: pause 60 s
- +205,1 s: probe B-paused: 3/3 acknowledged {}
- +205,2 s: unpaused; settling 60 s
- +267,3 s: probe B-after: 3/3 acknowledged {}
- +267,9 s: cleanup start
- +269,8 s: cleanup done

### log excerpts: evento-eu

```
20261020151412672 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020151417100 INFO  1 --- [nioEventLoopGroup-3-14                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020151418394 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### log excerpts: evento-apac

```
20261020151543497 INFO  1 --- [bus-business-2                          ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020151547732 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020151549032 INFO  1 --- [nioEventLoopGroup-3-2                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
```

### stack actions

```
2026-10-20T15:12:23.067439800Z docker kill evento-apac
2026-10-20T15:13:09.747827300Z docker start evento-apac
2026-10-20T15:14:39.230360700Z docker pause evento-apac
2026-10-20T15:15:40.561546300Z docker unpause evento-apac
```

