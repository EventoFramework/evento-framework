# S18: Residual window: the server loses the event store after the payment ran

- Label: `after-inbox`
- Gaps exercised: G5
- Started: 2026-10-09T09:23:15.120298700Z, finished: 2026-10-09T09:24:44.867095200Z (89 s)
- Verdicts: {INCONCLUSIVE=2, AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s18-after-inbox-3e6e37-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- orders: `6`
- cutSeconds: `20`
- region: `CINA`
- esLink: `apac-to-es-pg-eu`
- outcomeStoreEnv: `jdbc (default)`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| the residual window was hit | **INCONCLUSIVE** | at least one payment committed while its server could not store its event |  | 0 of 1 payments committed before the cut had no event at the heal: [] |
| a payment whose event could not be stored is not executed again | **INCONCLUSIVE** | 0 extra executions: the retry is answered from the outcome recorded with the payment (G5 inbox) | the saga's retry runs the payment again (G5 residual window): attempts = 2 | 0 extra execution(s) among the 0 in the window; 0 overall; attempts {s18-after-inbox-3e6e37-slowpay-cina-1=1, s18-after-inbox-3e6e37-slowpay-cina-2=1, s18-after-inbox-3e6e37-slowpay-cina-3=1, s18-after-inbox-3e6e37-slowpay-cina-4=1, s18-after-inbox-3e6e37-slowpay-cina-5=1, s18-after-inbox-3e6e37-slowpay-cina-6=1} |
| every payment is stored exactly once | **AS_EXPECTED** | one PaymentProcessedEvent per order once the saga's retries are done |  | 0 order(s) without the event after 300 s, 0 with two; events {s18-after-inbox-3e6e37-slowpay-cina-1=1, s18-after-inbox-3e6e37-slowpay-cina-2=1, s18-after-inbox-3e6e37-slowpay-cina-3=1, s18-after-inbox-3e6e37-slowpay-cina-4=1, s18-after-inbox-3e6e37-slowpay-cina-5=1, s18-after-inbox-3e6e37-slowpay-cina-6=1} |

- _a payment whose event could not be stored is not executed again_: No payment was caught in the window; raise LAB_PAYMENT_SLOW_MS.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "paidBeforeCut" : 1,
  "eventStoredBeforeCut" : 1,
  "residualWindowHits" : 0,
  "convergedSecondsAfterCut" : 51,
  "paymentAttempts" : {
    "s18-after-inbox-3e6e37-slowpay-cina-1" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-2" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-3" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-4" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-5" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-6" : 1
  },
  "paymentEvents" : {
    "s18-after-inbox-3e6e37-slowpay-cina-1" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-2" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-3" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-4" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-5" : 1,
    "s18-after-inbox-3e6e37-slowpay-cina-6" : 1
  },
  "duplicatePaymentExecutions" : 0,
  "duplicatePaymentExecutionsInWindow" : 0,
  "logMarkers" : {
    "evento-apac" : { },
    "evento-eu" : { },
    "evento-am" : { },
    "order-command-apac" : {
      "event=service_outcome_replayed" : 1
    },
    "order-command-eu" : { },
    "order-command-am" : { }
  },
  "serviceOutcomeReplayed" : 1
}
```

## Timeline

- +8,3 s: scenario start
- +22,9 s: created 6 CINA orders with an item
- +29,1 s: completed them: the saga pays each one (the handler answers 4000 ms after its commit)
- +31,0 s: cut apac-to-es-pg-eu with 1 of 6 payments committed and still answering
- +51,3 s: healed apac-to-es-pg-eu after 20 s
- +87,8 s: cleanup start
- +89,7 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-09T09:24:12.171Z  INFO 1 --- [order-command] [    virtual-120] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_CINA:14:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-09T09:23:46.159835Z
```

### stack actions

```
2026-10-09T09:23:46.129732800Z disable apac-to-es-pg-eu
2026-10-09T09:24:06.417270600Z enable apac-to-es-pg-eu
2026-10-09T09:24:42.965533800Z enable apac-to-es-pg-eu
```

