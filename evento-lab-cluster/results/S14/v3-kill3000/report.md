# S14: Retry storm with idempotency keys

- Label: `v3-kill3000`
- Gaps exercised: G5, G4
- Started: 2026-10-20T16:29:10.466439900Z, finished: 2026-10-20T16:30:12.233692100Z (61 s)
- Verdicts: {INCONCLUSIVE=3, AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s14-v3-kill3000-0fca9c-`
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
| a retried command is stored once | **INCONCLUSIVE** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 0 of 0 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **INCONCLUSIVE** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 0 of 0 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **INCONCLUSIVE** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 0 failures took >= 25 s |

- _a retried command is stored once_: No command was caught in the ambiguous window; tune S14_KILL_DELAY_MS.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "sent" : 24,
    "kinds" : { },
    "failed" : 0,
    "failedAfter25s" : 0
  },
  "failedButStoredBeforeRetry" : 0,
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
  "storedThenRetriedAcked" : 0
}
```

## Timeline

- +7,8 s: scenario start
- +14,9 s: pre-created 8 CINA orders for the add-item probes
- +18,3 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +21,3 s: retried 0 failed commands via EUROPA: {}
- +27,0 s: cleanup start
- +61,8 s: cleanup done

### stack actions

```
2026-10-20T16:29:28.333738900Z docker kill evento-apac
2026-10-20T16:29:37.515508600Z docker start evento-apac (restore)
```

