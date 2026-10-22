# S14: Retry storm with idempotency keys

- Label: `v3e`
- Gaps exercised: G5, G4
- Started: 2026-10-22T16:36:58.961402Z, finished: 2026-10-22T16:38:55.977363100Z (117 s)
- Verdicts: {INCONCLUSIVE=2, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s14-v3e-e09c49-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}`
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
    "failedAfter25s" : 0,
    "sent" : 24,
    "kinds" : {
      "broker_changed" : 24
    },
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
  "acknowledgedButNotStored" : 0,
  "storedThenRetriedAcked" : 0
}
```

## Timeline

- +7,8 s: placement: 7 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +58,9 s: placement restored, ready={gateway-africa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +59,0 s: scenario start
- +65,0 s: pre-created 8 CINA orders for the add-item probes
- +65,6 s: killed evento-apac 150 ms after firing 24 commands at CINA
- +76,8 s: retried 24 failed commands via EUROPA: {}
- +82,2 s: cleanup start
- +117,0 s: cleanup done

### stack actions

```
2026-10-22T16:37:06.751845100Z docker restart gateway-africa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
2026-10-22T16:38:04.087159500Z docker kill evento-apac
2026-10-22T16:38:21.212488Z docker start evento-apac (restore)
```

