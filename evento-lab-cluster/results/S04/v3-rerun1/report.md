# S04: Server returns after failover

- Label: `v3-rerun1`
- Gaps exercised: G7, G17
- Started: 2026-10-20T16:03:18.502889500Z, finished: 2026-10-20T16:07:55.907749900Z (277 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3-rerun1-9f1f7d-`
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
      "Skipping unregister of bundle" : 6
    },
    "evento-am" : { },
    "evento-apac" : {
      "Skipping unregister of bundle" : 6
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 12
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,9 s: scenario start
- +8,0 s: variant A: kill + restart
- +55,1 s: probe A-outage: 3/3 acknowledged {}
- +88,0 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +150,7 s: probe A-after: 3/3 acknowledged {}
- +151,2 s: variant B: pause 60 s
- +212,5 s: probe B-paused: 3/3 acknowledged {}
- +212,6 s: unpaused; settling 60 s
- +274,9 s: probe B-after: 3/3 acknowledged {}
- +275,4 s: cleanup start
- +277,4 s: cleanup done

### log excerpts: evento-eu

```
20261020160515872 INFO  1 --- [nioEventLoopGroup-3-9                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020160522757 INFO  1 --- [nioEventLoopGroup-3-4                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020160529089 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: evento-apac

```
20261020160652019 INFO  1 --- [nioEventLoopGroup-3-12                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020160653309 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020160653568 INFO  1 --- [nioEventLoopGroup-3-9                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
```

### stack actions

```
2026-10-20T16:03:26.455309Z docker kill evento-apac
2026-10-20T16:04:13.600481300Z docker start evento-apac
2026-10-20T16:05:49.690443400Z docker pause evento-apac
2026-10-20T16:06:51.020626500Z docker unpause evento-apac
```

