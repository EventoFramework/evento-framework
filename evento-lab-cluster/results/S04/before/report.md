# S04: Server returns after failover

- Label: `before`
- Gaps exercised: G7, G17
- Started: 2026-09-29T12:49:34.533512Z, finished: 2026-09-29T12:54:06.846389900Z (272 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s04-before-a5c38d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| A: catalog after the killed server returns | **AS_EXPECTED** | every bundle, handler and consumer still registered (I7) | the returning/old server's NodeLeft clears the moved bundle's rows (G7) | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| A: APAC commands after the return | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |
| B: catalog after a half-closed socket heals | **KNOWN_DEFECT_REPRODUCED** | every bundle, handler and consumer still registered (I7) | the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7) | 0 bundle(s), 0 handler(s), 10 consumer(s) missing; 0 bundle(s) without handler rows |
| B: APAC commands after unpause | **AS_EXPECTED** | acknowledged |  | 3/3 acknowledged {} |


## Invariants

| Id | Invariant | Status | Expected | Observed |
|---|---|---|---|---|
| I7-A | catalog matches live bundles (variant A) | **PASS** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 0 consumer(s) missing; 0 bundle(s) without handler rows |
| I7-B | catalog matches live bundles (variant B) | **FAIL** | core__bundle / core__handler / core__consumer list every live bundle, handler and consumer | 0 bundle(s), 0 handler(s), 10 consumer(s) missing; 0 bundle(s) without handler rows |

<details><summary>I7-B: 10 violation(s)</summary>

- no core__consumer row for order-query_OrderProjector_1_CINA*
- no core__consumer row for order-query_OrderProjector_1_INDIA*
- no core__consumer row for order-query_OrderProjector_1_AUSTRALIA_NZ*
- no core__consumer row for order-saga_PaymentSaga_1_EUROPA*
- no core__consumer row for order-saga_PaymentSaga_1_AFRICA*
- no core__consumer row for order-saga_PaymentSaga_1_AMERICA*
- no core__consumer row for order-saga_PaymentSaga_1_SUD_AMERICA*
- no core__consumer row for order-saga_PaymentSaga_1_CINA*
- no core__consumer row for order-saga_PaymentSaga_1_INDIA*
- no core__consumer row for order-saga_PaymentSaga_1_AUSTRALIA_NZ*

</details>

## Metrics

```json
{
  "A.probesDuringOutage" : "0/3 acknowledged {error=3}",
  "B.probesDuringPause" : "0/3 acknowledged {error=3}",
  "logMarkers" : {
    "evento-eu" : { },
    "evento-am" : { },
    "evento-apac" : {
      "Skipping unregister of bundle" : 6,
      "[BundleService] Unregister called for bundleId=" : 2,
      "duplicate key" : 4,
      "core__bundle_pkey" : 5
    }
  },
  "unregisterCalls" : 2,
  "skippedUnregisters" : 6
}
```

## Notes

- G17: the GUI/admin views of each server only show locally connected bundles; compare http://localhost:13000..13002 by hand when reading this result.

## Timeline

- +7,8 s: scenario start
- +7,8 s: variant A: kill + restart
- +53,3 s: probe A-outage: 0/3 acknowledged {error=3}
- +75,4 s: evento-apac back; settling 60 s (> 45 s read-idle)
- +142,2 s: probe A-after: 3/3 acknowledged {}
- +142,7 s: variant B: pause 60 s
- +202,9 s: probe B-paused: 0/3 acknowledged {error=3}
- +203,0 s: unpaused; settling 60 s
- +269,9 s: probe B-after: 3/3 acknowledged {}
- +270,4 s: cleanup start
- +272,3 s: cleanup done

### log excerpts: evento-apac

```
20260929125259342 INFO  1 --- [nioEventLoopGroup-3-5                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20260929125305624 INFO  1 --- [nioEventLoopGroup-3-15                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20260929125308349 INFO  1 --- [bus-business-2                          ] AutoDiscoveryService                     : Skipping unregister of bundle order-query: another live instance is connected
20260929125259106 INFO  1 --- [nioEventLoopGroup-3-3                   ] BundleService                            : [BundleService] Unregister called for bundleId=order-command
20260929125259219 INFO  1 --- [nioEventLoopGroup-3-7                   ] BundleService                            : [BundleService] Unregister called for bundleId=order-saga
20260929125326296 WARN  1 --- [bus-business-18                         ] error                                    : ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
20260929125326296 WARN  1 --- [bus-business-18                         ] error                                    : ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
  Detail: Key (id)=(order-saga) already exists.] [insert into core__bundle (contains_handlers,description,detail,instance_id,line_prefix,repository_url,updated_at,version,id) values (?,?,?,?,?,?,?,?,?)]; SQL [insert into core__bundle (contains_handlers,description,detail,instance_id,line_prefix,repository_url,updated_at,version,id) values (?,?,?,?,?,?,?,?,?)]; constraint [core__bundle_pkey]
```

### stack actions

```
2026-09-29T12:49:42.347276700Z docker kill evento-apac
2026-09-29T12:50:27.843952300Z docker start evento-apac
2026-09-29T12:51:57.250000800Z docker pause evento-apac
2026-09-29T12:52:57.397442600Z docker unpause evento-apac
```

