# S14: Retry storm with idempotency keys

- Label: `after-g5n16-kill150`
- Gaps exercised: G5, G4
- Started: 2026-09-29T21:03:03.016656100Z, finished: 2026-09-29T21:04:10.345776Z (67 s)
- Verdicts: {INCONCLUSIVE=2, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s14-after-g5n16-kill150-ea8143-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `150`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **INCONCLUSIVE** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 0 of 24 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **INCONCLUSIVE** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 0 of 0 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 24 failures took >= 25 s |

- _a retried command is stored once_: No command was caught in the ambiguous window; tune S14_KILL_DELAY_MS.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "sent" : 24,
    "failedAfter25s" : 0,
    "failed" : 24,
    "kinds" : {
      "broker_changed" : 24
    }
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

- +8,0 s: scenario start
- +23,3 s: pre-created 8 CINA orders for the add-item probes
- +23,9 s: killed evento-apac 150 ms after firing 24 commands at CINA
- +33,5 s: retried 24 failed commands via EUROPA: {}
- +39,0 s: cleanup start
- +67,3 s: cleanup done

### stack actions

```
2026-09-29T21:03:26.517139Z docker kill evento-apac
2026-09-29T21:03:42.087776400Z docker start evento-apac (restore)
```

