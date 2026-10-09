# S14: Retry storm with idempotency keys

- Label: `after-inbox`
- Gaps exercised: G5, G4
- Started: 2026-10-09T09:13:34.638547Z, finished: 2026-10-09T09:14:41.529830100Z (66 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-inbox-c6b898-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `3000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 1 of 7 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **AS_EXPECTED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 1 of 1 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 7 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failedAfter25s" : 0,
    "sent" : 24,
    "kinds" : {
      "broker_changed" : 7
    },
    "failed" : 7
  },
  "failedButStoredBeforeRetry" : 1,
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
  "storedThenRetriedAcked" : 1
}
```

## Timeline

- +8,6 s: scenario start
- +20,3 s: pre-created 8 CINA orders for the add-item probes
- +23,9 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +32,6 s: retried 7 failed commands via EUROPA: {}
- +38,0 s: cleanup start
- +66,9 s: cleanup done

### stack actions

```
2026-10-09T09:13:57.916088200Z docker kill evento-apac
2026-10-09T09:14:12.757041700Z docker start evento-apac (restore)
```

