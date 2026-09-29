# S14: Retry storm with idempotency keys

- Label: `before-g5n16`
- Gaps exercised: G5, G4
- Started: 2026-09-29T22:06:28.914380400Z, finished: 2026-09-29T22:08:00.918262600Z (92 s)
- Verdicts: {AS_EXPECTED=2, KNOWN_DEFECT_REPRODUCED=2}

## Parameters

- orderIdPrefix: `s14-before-g5n16-8af06a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `2000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 1 of 24 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8, retryRejected409=1}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **KNOWN_DEFECT_REPRODUCED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 0 of 1 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **KNOWN_DEFECT_REPRODUCED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 24 of 24 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "sent" : 24,
    "kinds" : {
      "timeout" : 24
    },
    "failed" : 24,
    "failedAfter25s" : 24
  },
  "failedButStoredBeforeRetry" : 1,
  "storedCopiesPerKind" : {
    "create-auto-id" : {
      "stored1" : 8,
      "retryRejected409" : 1
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

- +7,7 s: scenario start
- +25,8 s: pre-created 8 CINA orders for the add-item probes
- +28,2 s: killed evento-apac 2000 ms after firing 24 commands at CINA
- +62,6 s: retried 24 failed commands via EUROPA: {rejected=1}
- +68,1 s: cleanup start
- +92,0 s: cleanup done

### storedThenRetriedNotAcked

```
create-auto-id 513f3024-f3f4-40ee-b446-53789f5586e5: REJECTED rejected
```

### stack actions

```
2026-09-29T22:06:56.700698400Z docker kill evento-apac
2026-09-29T22:07:37.082251200Z docker start evento-apac (restore)
```

