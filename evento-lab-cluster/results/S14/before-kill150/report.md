# S14: Retry storm with idempotency keys

- Label: `before`
- Gaps exercised: G5, G4
- Started: 2026-09-29T13:55:33.845181600Z, finished: 2026-09-29T13:57:05.163576Z (91 s)
- Verdicts: {INCONCLUSIVE=1, AS_EXPECTED=1, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s14-before-fb74e1-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- probesPerKind: `8`
- killDelayMs: `150`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **INCONCLUSIVE** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 0 of 24 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **KNOWN_DEFECT_REPRODUCED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 24 of 24 failures took >= 25 s |

- _a retried command is stored once_: No command was caught in the ambiguous window; tune S14_KILL_DELAY_MS.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "kinds" : {
      "timeout" : 24
    },
    "sent" : 24,
    "failedAfter25s" : 24,
    "failed" : 24
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
  "acknowledgedButNotStored" : 0
}
```

## Timeline

- +6,3 s: scenario start
- +25,3 s: pre-created 8 CINA orders for the add-item probes
- +25,9 s: killed evento-apac 150 ms after firing 24 commands at CINA
- +61,8 s: retried 24 failed commands via EUROPA: {}
- +67,3 s: cleanup start
- +91,3 s: cleanup done

### stack actions

```
2026-09-29T13:55:59.286933500Z docker kill evento-apac
2026-09-29T13:56:41.220063500Z docker start evento-apac (restore)
```

