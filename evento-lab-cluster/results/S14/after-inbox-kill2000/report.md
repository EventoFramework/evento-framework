# S14: Retry storm with idempotency keys

- Label: `after-inbox-kill2000`
- Gaps exercised: G5, G4
- Started: 2026-10-09T09:18:30.760970600Z, finished: 2026-10-09T09:19:36.364109800Z (65 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-inbox-kill2000-192d01-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `2000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 4 of 16 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **AS_EXPECTED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 4 of 4 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 16 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "sent" : 24,
    "failedAfter25s" : 0,
    "failed" : 16,
    "kinds" : {
      "broker_changed" : 16
    }
  },
  "failedButStoredBeforeRetry" : 4,
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
  "storedThenRetriedAcked" : 4
}
```

## Timeline

- +9,4 s: scenario start
- +20,2 s: pre-created 8 CINA orders for the add-item probes
- +22,7 s: killed evento-apac 2000 ms after firing 24 commands at CINA
- +31,0 s: retried 16 failed commands via EUROPA: {}
- +36,5 s: cleanup start
- +65,6 s: cleanup done

### stack actions

```
2026-10-09T09:18:52.948308400Z docker kill evento-apac
2026-10-09T09:19:07.404832500Z docker start evento-apac (restore)
```

