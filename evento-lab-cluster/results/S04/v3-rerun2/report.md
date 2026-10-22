# S04: Server returns after failover

- Label: `v3-rerun2`
- Gaps exercised: G7, G17
- Started: 2026-10-20T16:11:54.745579600Z, finished: 2026-10-20T16:16:32.258817900Z (277 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-v3-rerun2-712f44-`
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
      "Skipping unregister of bundle" : 8
    }
  },
  "unregisterCalls" : 0,
  "skippedUnregisters" : 14
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,8 s: scenario start
- +7,8 s: variant A: kill + restart
- +55,1 s: probe A-outage: 3/3 acknowledged {}
- +87,9 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +150,9 s: probe A-after: 3/3 acknowledged {}
- +151,3 s: variant B: pause 60 s
- +212,6 s: probe B-paused: 3/3 acknowledged {}
- +212,7 s: unpaused; settling 60 s
- +275,0 s: probe B-after: 3/3 acknowledged {}
- +275,5 s: cleanup start
- +277,5 s: cleanup done

### log excerpts: evento-eu

```
20261020161353390 INFO  1 --- [nioEventLoopGroup-3-6                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20261020161357696 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020161400525 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: evento-apac

```
20261020161528689 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
20261020161529158 INFO  1 --- [nioEventLoopGroup-3-12                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-saga: another live instance is connected
20261020161529990 INFO  1 --- [nioEventLoopGroup-3-1                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### stack actions

```
2026-10-20T16:12:02.518557100Z docker kill evento-apac
2026-10-20T16:12:49.809833700Z docker start evento-apac
2026-10-20T16:14:26.072718200Z docker pause evento-apac
2026-10-20T16:15:27.379302300Z docker unpause evento-apac
```

