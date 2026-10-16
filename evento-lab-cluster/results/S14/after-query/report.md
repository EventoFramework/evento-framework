# S14: Retry storm with idempotency keys

- Label: `after-query`
- Gaps exercised: G5, G4
- Started: 2026-10-15T14:25:03.226900300Z, finished: 2026-10-15T14:26:53.476088800Z (110 s)
- Verdicts: {INCONCLUSIVE=2, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s14-after-query-e98640-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, order-observer=evento-am, order-query-wildcard=evento-am}`
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

- +19,3 s: placement: 3 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, order-observer=evento-am, order-query-wildcard=evento-am}
- +54,7 s: placement restored, ready={gateway-africa=true, order-observer=true, order-query-wildcard=true}
- +54,7 s: scenario start
- +65,0 s: pre-created 8 CINA orders for the add-item probes
- +65,5 s: killed evento-apac 150 ms after firing 24 commands at CINA
- +76,4 s: retried 24 failed commands via EUROPA: {}
- +81,8 s: cleanup start
- +110,2 s: cleanup done

### stack actions

```
2026-10-15T14:25:22.506958300Z docker restart gateway-africa order-observer order-query-wildcard
2026-10-15T14:26:08.339776100Z docker kill evento-apac
2026-10-15T14:26:25.090286Z docker start evento-apac (restore)
```

