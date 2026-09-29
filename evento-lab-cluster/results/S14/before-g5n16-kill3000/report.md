# S14: Retry storm with idempotency keys

- Label: `before-g5n16-kill3000`
- Gaps exercised: G5, G4
- Started: 2026-09-29T22:16:25.973921Z, finished: 2026-09-29T22:17:58.136895Z (92 s)
- Verdicts: {AS_EXPECTED=2, KNOWN_DEFECT_REPRODUCED=2}

## Parameters

- orderIdPrefix: `s14-before-g5n16-kill3000-9e867f-`
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
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 2 of 24 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8, retryRejected409=1}, create-client-id={stored1=8, retryRejected409=1}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **KNOWN_DEFECT_REPRODUCED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 0 of 2 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **KNOWN_DEFECT_REPRODUCED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 24 of 24 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failed" : 24,
    "kinds" : {
      "timeout" : 24
    },
    "sent" : 24,
    "failedAfter25s" : 24
  },
  "failedButStoredBeforeRetry" : 2,
  "storedCopiesPerKind" : {
    "create-auto-id" : {
      "stored1" : 8,
      "retryRejected409" : 1
    },
    "create-client-id" : {
      "stored1" : 8,
      "retryRejected409" : 1
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

- +7,7 s: scenario start
- +26,3 s: pre-created 8 CINA orders for the add-item probes
- +29,7 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +62,8 s: retried 24 failed commands via EUROPA: {rejected=2}
- +68,2 s: cleanup start
- +92,2 s: cleanup done

### storedThenRetriedNotAcked

```
create-client-id f07e7976-c831-4333-a730-d131df309a8b: REJECTED rejected
create-auto-id f716dbc1-b403-4179-adb0-56c047af1fc6: REJECTED rejected
```

### stack actions

```
2026-09-29T22:16:55.249353300Z docker kill evento-apac
2026-09-29T22:17:34.209930100Z docker start evento-apac (restore)
```

