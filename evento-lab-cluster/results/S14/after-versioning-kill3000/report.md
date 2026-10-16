# S14: Retry storm with idempotency keys

- Label: `after-versioning-kill3000`
- Gaps exercised: G5, G4
- Started: 2026-10-16T00:54:40.920485800Z, finished: 2026-10-16T00:56:43.290794600Z (122 s)
- Verdicts: {AS_EXPECTED=4}

## Parameters

- orderIdPrefix: `s14-after-versioning-kill3000-af7e1a-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- probesPerKind: `8`
- killDelayMs: `3000`
- retryVia: `EUROPA`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| a retried command is stored once | **AS_EXPECTED** | exactly one stored event per idempotency key (key persisted with the event, G5 fix) | a command stored before its reply was lost is stored again by the retry (G5) | 0 key(s) stored twice; 1 of 12 failed first attempts were stored before the retry; per kind {create-auto-id={stored1=8}, create-client-id={stored1=8}, add-item={stored1=8}} |
| the retry of a stored command is acknowledged with the stored event | **AS_EXPECTED** | every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay) | the retry is stored again, or refused with 409 by the aggregate (create with a client id) | 1 of 1 stored-then-retried commands acknowledged |
| acknowledged commands are stored | **AS_EXPECTED** | every 2xx has its event |  | 0 missing |
| in-flight commands fail fast when their server dies | **AS_EXPECTED** | typed failure right away (G4 fix) | fail only after the 30 s request timeout (G4) | 0 of 12 failures took >= 25 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "firstAttempt" : {
    "failedAfter25s" : 0,
    "sent" : 24,
    "kinds" : {
      "broker_changed" : 12
    },
    "failed" : 12
  },
  "failedButStoredBeforeRetry" : 1,
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
  "storedThenRetriedAcked" : 1
}
```

## Timeline

- +7,9 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +65,6 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +65,6 s: scenario start
- +76,0 s: pre-created 8 CINA orders for the add-item probes
- +79,5 s: killed evento-apac 3000 ms after firing 24 commands at CINA
- +88,5 s: retried 12 failed commands via EUROPA: {}
- +94,0 s: cleanup start
- +122,4 s: cleanup done

### stack actions

```
2026-10-16T00:54:48.832985900Z docker restart gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
2026-10-16T00:55:59.949829Z docker kill evento-apac
2026-10-16T00:56:14.960028500Z docker start evento-apac (restore)
```

