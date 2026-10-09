# S18: Residual window: the server loses the event store after the payment ran

- Label: `after-inbox-store-off`
- Gaps exercised: G5
- Started: 2026-10-09T13:03:22.483790300Z, finished: 2026-10-09T13:04:38.669918200Z (76 s)
- Verdicts: {AS_EXPECTED=2, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s18-after-inbox-store-off-0aa1b4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- orders: `6`
- cutSeconds: `20`
- regions: `[CINA, INDIA, AUSTRALIA_NZ]`
- esLink: `apac-to-es-pg-eu`
- outcomeStoreEnv: `off`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| the residual window was hit | **AS_EXPECTED** | at least one payment committed while its server could not store its event |  | 3 of 3 payments committed before the cut had no event at the heal: [s18-after-inbox-store-off-0aa1b4-slowpay-cina-4, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3, s18-after-inbox-store-off-0aa1b4-slowpay-india-5] |
| a payment whose event could not be stored is not executed again | **KNOWN_DEFECT_REPRODUCED** | 0 extra executions: the retry is answered from the outcome recorded with the payment (G5 inbox) | the saga's retry runs the payment again (G5 residual window): attempts = 2 | 3 extra execution(s) among the 3 in the window; 3 overall; attempts {s18-after-inbox-store-off-0aa1b4-slowpay-cina-4=2, s18-after-inbox-store-off-0aa1b4-slowpay-india-5=2, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3=2, s18-after-inbox-store-off-0aa1b4-slowpay-india-2=1, s18-after-inbox-store-off-0aa1b4-slowpay-cina-1=1, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-6=1} |
| every payment is stored exactly once | **AS_EXPECTED** | one PaymentProcessedEvent per order once the saga's retries are done |  | 0 order(s) without the event after 300 s, 0 with two; events {s18-after-inbox-store-off-0aa1b4-slowpay-cina-1=1, s18-after-inbox-store-off-0aa1b4-slowpay-india-2=1, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3=1, s18-after-inbox-store-off-0aa1b4-slowpay-cina-4=1, s18-after-inbox-store-off-0aa1b4-slowpay-india-5=1, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-6=1} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "paidBeforeCut" : 3,
  "eventStoredBeforeCut" : 0,
  "residualWindowHits" : 3,
  "convergedSecondsAfterCut" : 41,
  "paymentAttempts" : {
    "s18-after-inbox-store-off-0aa1b4-slowpay-cina-4" : 2,
    "s18-after-inbox-store-off-0aa1b4-slowpay-india-5" : 2,
    "s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3" : 2,
    "s18-after-inbox-store-off-0aa1b4-slowpay-india-2" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-cina-1" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-6" : 1
  },
  "paymentEvents" : {
    "s18-after-inbox-store-off-0aa1b4-slowpay-cina-1" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-india-2" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-cina-4" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-india-5" : 1,
    "s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-6" : 1
  },
  "duplicatePaymentExecutions" : 3,
  "duplicatePaymentExecutionsInWindow" : 3,
  "logMarkers" : {
    "evento-apac" : { },
    "evento-eu" : { },
    "evento-am" : { },
    "order-command-apac" : { },
    "order-command-eu" : { },
    "order-command-am" : { }
  },
  "serviceOutcomeReplayed" : 0
}
```

## Timeline

- +9,3 s: scenario start
- +24,9 s: created 6 orders with an item in [CINA, INDIA, AUSTRALIA_NZ]
- +26,3 s: completed them: the saga pays each one (the handler answers 4000 ms after its commit)
- +27,2 s: cut apac-to-es-pg-eu with 3 of 6 payments committed, 3 of them still answering: [s18-after-inbox-store-off-0aa1b4-slowpay-cina-4, s18-after-inbox-store-off-0aa1b4-slowpay-australia-nz-3, s18-after-inbox-store-off-0aa1b4-slowpay-india-5]
- +47,6 s: healed apac-to-es-pg-eu after 20 s
- +74,3 s: cleanup start
- +76,2 s: cleanup done

### stack actions

```
2026-10-09T13:03:49.639711800Z disable apac-to-es-pg-eu
2026-10-09T13:04:10.035446900Z enable apac-to-es-pg-eu
2026-10-09T13:04:36.744436900Z enable apac-to-es-pg-eu
```

