# S14: Retry storm with idempotency keys

- Label: `before`
- Gaps exercised: G5, G4
- Started: 2026-09-29T13:57:12.406612900Z, finished: 2026-09-29T13:58:43.101345400Z (90 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=2, AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s14-before-3fd131-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- probesPerKind: `8`
- killDelayMs: `2000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **KNOWN_DEFECT_REPRODUCED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 1 key(s) stored twice; 1 of 24 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=7, stored2=1}, create-client-id={stored1=8}, add-item={stored1=8}} |
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
  "failedButStoredBeforeRetry" : 1,
  "storedCopiesPerKind" : {
    "create-auto-id" : {
      "stored1" : 7,
      "stored2" : 1
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

- +6,0 s: scenario start
- +24,6 s: pre-created 8 CINA orders for the add-item probes
- +27,0 s: killed evento-apac 2000 ms after firing 24 commands at CINA
- +61,2 s: retried 24 failed commands via EUROPA: {}
- +66,7 s: cleanup start
- +90,7 s: cleanup done

### duplicates

```
create-auto-id key 08356e01-186a-40dd-89db-065adeccdf43 stored 2x: [6cbaaa1d-4469-44fa-94aa-8482039ebebb@76, b58b82c4-36cd-4661-a091-6e30a71ce143@78]
```

### stack actions

```
2026-09-29T13:57:39.047415300Z docker kill evento-apac
2026-09-29T13:58:19.209001700Z docker start evento-apac (restore)
```

