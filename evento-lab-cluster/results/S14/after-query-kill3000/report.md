# S14: Retry storm with idempotency keys

- Label: `after-query-kill3000`
- Gaps exercised: G5, G4
- Started: 2026-10-15T14:31:44.847768Z, finished: 2026-10-15T14:32:49.197083400Z (64 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-query-kill3000-75525d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `3000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 3 of 10 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **AS_EXPECTED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 3 of 3 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 10 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failed" : 10,
    "kinds" : {
      "broker_changed" : 10
    },
    "sent" : 24,
    "failedAfter25s" : 0
  },
  "failedButStoredBeforeRetry" : 3,
  "storedCopiesPerKind" : {
    "create-auto-id" : {
      "stored1" : 8
    },
    "create-client-id" : {
      "stored1" : 8
    },
    "add-item" : {
      "stored1" : 8
    }
  },
  "acknowledgedButNotStored" : 0,
  "storedThenRetriedAcked" : 3
}
```

## Timeline

- +8,1 s: scenario start
- +18,3 s: pre-created 8 CINA orders for the add-item probes
- +21,8 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +30,2 s: retried 10 failed commands via EUROPA: {}
- +35,7 s: cleanup start
- +64,3 s: cleanup done

### stack actions

```
2026-10-15T14:32:06.192105100Z docker kill evento-apac
2026-10-15T14:32:20.657938800Z docker start evento-apac (restore)
```

