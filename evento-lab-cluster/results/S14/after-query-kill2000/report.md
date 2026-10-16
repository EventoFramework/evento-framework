# S14: Retry storm with idempotency keys

- Label: `after-query-kill2000`
- Gaps exercised: G5, G4
- Started: 2026-10-15T14:32:56.039053500Z, finished: 2026-10-15T14:35:00.613661700Z (124 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-query-kill2000-022bc4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `2000`
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
    "failed" : 18,
    "failedAfter25s" : 0,
    "sent" : 24,
    "kinds" : {
      "broker_changed" : 18
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

- +8,2 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +67,5 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +67,5 s: scenario start
- +78,6 s: pre-created 8 CINA orders for the add-item probes
- +81,1 s: killed evento-apac 2000 ms after firing 24 commands at CINA
- +90,6 s: retried 18 failed commands via EUROPA: {}
- +96,0 s: cleanup start
- +124,6 s: cleanup done

### stack actions

```
2026-10-15T14:33:04.195493700Z docker restart gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
2026-10-15T14:34:16.691684Z docker kill evento-apac
2026-10-15T14:34:32.137075400Z docker start evento-apac (restore)
```

