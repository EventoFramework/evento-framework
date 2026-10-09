# S18: Residual window: the server loses the event store after the payment ran

- Label: `after-inbox`
- Gaps exercised: G5
- Started: 2026-10-09T09:29:04.350044700Z, finished: 2026-10-09T09:30:13.015306500Z (68 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s18-after-inbox-aa2225-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\.claude\worktrees\agent-ad8c96fea1b7ef6fc\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- orders: `6`
- cutSeconds: `20`
- regions: `[CINA, INDIA, AUSTRALIA_NZ]`
- esLink: `apac-to-es-pg-eu`
- outcomeStoreEnv: `jdbc (default)`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| the residual window was hit | **AS_EXPECTED** | at least one payment committed while its server could not store its event |  | 3 of 3 payments committed before the cut had no event at the heal: [s18-after-inbox-aa2225-slowpay-india-5, s18-after-inbox-aa2225-slowpay-australia-nz-3, s18-after-inbox-aa2225-slowpay-cina-1] |
| a payment whose event could not be stored is not executed again | **AS_EXPECTED** | 0 extra executions: the retry is answered from the outcome recorded with the payment (G5 inbox) | the saga's retry runs the payment again (G5 residual window): attempts = 2 | 0 extra execution(s) among the 3 in the window; 0 overall; attempts {s18-after-inbox-aa2225-slowpay-india-5=1, s18-after-inbox-aa2225-slowpay-australia-nz-3=1, s18-after-inbox-aa2225-slowpay-cina-1=1, s18-after-inbox-aa2225-slowpay-cina-4=1, s18-after-inbox-aa2225-slowpay-india-2=1, s18-after-inbox-aa2225-slowpay-australia-nz-6=1} |
| every payment is stored exactly once | **AS_EXPECTED** | one PaymentProcessedEvent per order once the saga's retries are done |  | 0 order(s) without the event after 300 s, 0 with two; events {s18-after-inbox-aa2225-slowpay-cina-1=1, s18-after-inbox-aa2225-slowpay-india-2=1, s18-after-inbox-aa2225-slowpay-australia-nz-3=1, s18-after-inbox-aa2225-slowpay-cina-4=1, s18-after-inbox-aa2225-slowpay-india-5=1, s18-after-inbox-aa2225-slowpay-australia-nz-6=1} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "paidBeforeCut" : 3,
  "eventStoredBeforeCut" : 0,
  "residualWindowHits" : 3,
  "convergedSecondsAfterCut" : 35,
  "paymentAttempts" : {
    "s18-after-inbox-aa2225-slowpay-india-5" : 1,
    "s18-after-inbox-aa2225-slowpay-australia-nz-3" : 1,
    "s18-after-inbox-aa2225-slowpay-cina-1" : 1,
    "s18-after-inbox-aa2225-slowpay-cina-4" : 1,
    "s18-after-inbox-aa2225-slowpay-india-2" : 1,
    "s18-after-inbox-aa2225-slowpay-australia-nz-6" : 1
  },
  "paymentEvents" : {
    "s18-after-inbox-aa2225-slowpay-cina-1" : 1,
    "s18-after-inbox-aa2225-slowpay-india-2" : 1,
    "s18-after-inbox-aa2225-slowpay-australia-nz-3" : 1,
    "s18-after-inbox-aa2225-slowpay-cina-4" : 1,
    "s18-after-inbox-aa2225-slowpay-india-5" : 1,
    "s18-after-inbox-aa2225-slowpay-australia-nz-6" : 1
  },
  "duplicatePaymentExecutions" : 0,
  "duplicatePaymentExecutionsInWindow" : 0,
  "logMarkers" : {
    "evento-apac" : { },
    "evento-eu" : { },
    "evento-am" : { },
    "order-command-apac" : {
      "event=service_outcome_replayed" : 3
    },
    "order-command-eu" : { },
    "order-command-am" : { }
  },
  "serviceOutcomeReplayed" : 3
}
```

## Timeline

- +8,6 s: scenario start
- +23,1 s: created 6 orders with an item in [CINA, INDIA, AUSTRALIA_NZ]
- +24,6 s: completed them: the saga pays each one (the handler answers 4000 ms after its commit)
- +25,1 s: cut apac-to-es-pg-eu with 3 of 6 payments committed, 3 of them still answering: [s18-after-inbox-aa2225-slowpay-india-5, s18-after-inbox-aa2225-slowpay-australia-nz-3, s18-after-inbox-aa2225-slowpay-cina-1]
- +45,4 s: healed apac-to-es-pg-eu after 20 s
- +66,6 s: cleanup start
- +68,7 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-09T09:29:55.850Z  INFO 1 --- [order-command] [    virtual-120] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_INDIA:13:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-09T09:29:28.722884Z
2026-10-09T09:29:56.265Z  INFO 1 --- [order-command] [    virtual-122] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_AUSTRALIA_NZ:16:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-09T09:29:28.984296Z
2026-10-09T09:29:56.562Z  INFO 1 --- [order-command] [    virtual-124] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_CINA:14:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-09T09:29:29.197065Z
```

### stack actions

```
2026-10-09T09:29:29.431470200Z disable apac-to-es-pg-eu
2026-10-09T09:29:49.711947500Z enable apac-to-es-pg-eu
2026-10-09T09:30:10.974040700Z enable apac-to-es-pg-eu
```

