# S14: Retry storm with idempotency keys

- Label: `after`
- Gaps exercised: G5, G4
- Started: 2026-09-29T15:56:25.852864800Z, finished: 2026-09-29T15:58:21.538041300Z (115 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s14-after-dd7065-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `2000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **KNOWN_DEFECT_REPRODUCED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 8 key(s) stored twice; 9 of 13 failed first attempts were stored before the retry; per kind {create-auto-id={stored2=4, stored1=4}, create-client-id={stored1=8, retryRejected409=1}, add-item={stored2=4, stored1=4}} |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 13 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failed" : 13,
    "kinds" : {
      "broker_changed" : 13
    },
    "sent" : 24,
    "failedAfter25s" : 0
  },
  "failedButStoredBeforeRetry" : 9,
  "storedCopiesPerKind" : {
    "create-auto-id" : {
      "stored2" : 4,
      "stored1" : 4
    },
    "create-client-id" : {
      "stored1" : 8,
      "retryRejected409" : 1
    },
    "add-item" : {
      "stored2" : 4,
      "stored1" : 4
    }
  },
  "acknowledgedButNotStored" : 0
}
```

## Timeline

- +7,8 s: placement: 7 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +58,6 s: placement restored, ready={gateway-africa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +58,7 s: scenario start
- +69,8 s: pre-created 8 CINA orders for the add-item probes
- +72,2 s: killed evento-apac 2000 ms after firing 24 commands at CINA
- +81,8 s: retried 13 failed commands via EUROPA: {rejected=1}
- +87,5 s: cleanup start
- +115,7 s: cleanup done

### duplicates

```
create-auto-id key 2f303c2f-ec33-47de-a452-413ffb506965 stored 2x: [03ff4630-ecd3-47c6-91a4-b19c4eb822dd@3126, 9476ef8e-06d1-4a3b-b04a-2c1f84369158@3138]
create-auto-id key b98f6f3d-9604-4503-8692-c08074ee98c2 stored 2x: [70ad24ce-99ac-4ecd-8379-7df35f26cede@3124, 2c8a07d8-38fe-47eb-895a-b958bf772581@3137]
create-auto-id key c6f05f81-d2d1-4bc2-a873-219604476942 stored 2x: [5bb1393f-97a0-4d53-9111-e9d5cb2315e0@3122, a7d6aad1-6c22-428c-a9c0-3c60b4939fca@3136]
create-auto-id key 3e659830-629a-41e4-8c07-ad0050de5e55 stored 2x: [9d0ecb68-52ea-4e9a-901d-721cefcdf6af@3127, 88d24654-acc9-4d7e-9088-a6e5728a7bab@3142]
add-item key c0ed1ac8-a5ab-43bf-b41a-7b48569e85d5 stored 2x: [s14-after-dd7065-item-cina-1@3125, s14-after-dd7065-item-cina-1@3133]
add-item key bfee1e1e-d2ff-4528-830c-7f945aebcd66 stored 2x: [s14-after-dd7065-item-cina-2@3128, s14-after-dd7065-item-cina-2@3141]
add-item key 2e65f42a-eaa3-4348-902f-cd1b07f72386 stored 2x: [s14-after-dd7065-item-cina-5@3130, s14-after-dd7065-item-cina-5@3132]
add-item key 888dd6e9-8761-46ea-a3a6-ce504f726be1 stored 2x: [s14-after-dd7065-item-cina-6@3129, s14-after-dd7065-item-cina-6@3131]
```

### stack actions

```
2026-09-29T15:56:33.625717Z docker restart gateway-africa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
2026-09-29T15:57:37.613641500Z docker kill evento-apac
2026-09-29T15:57:53.458351800Z docker start evento-apac (restore)
```

