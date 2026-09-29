# S14: Retry storm with idempotency keys

- Label: `after-g5n16`
- Gaps exercised: G5, G4
- Started: 2026-09-29T20:57:16.967422900Z, finished: 2026-09-29T20:58:26.928772600Z (69 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-g5n16-7f085b-`
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
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 4 of 18 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **AS_EXPECTED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 4 of 4 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 18 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failedAfter25s" : 0,
    "failed" : 18,
    "kinds" : {
      "broker_changed" : 18
    },
    "sent" : 24
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

- +7,9 s: scenario start
- +22,4 s: pre-created 8 CINA orders for the add-item probes
- +25,8 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +35,6 s: retried 18 failed commands via EUROPA: {}
- +41,1 s: cleanup start
- +70,0 s: cleanup done

### stack actions

```
2026-09-29T20:57:42.333473900Z docker kill evento-apac
2026-09-29T20:57:58.132144500Z docker start evento-apac (restore)
```

