# S18: Residual window: the server loses the event store after the payment ran

- Label: `after-query`
- Gaps exercised: G5
- Started: 2026-10-15T14:27:00.822334Z, finished: 2026-10-15T14:29:07.883929300Z (127 s)
- Verdicts: {AS_EXPECTED=3}

## Parameters

- orderIdPrefix: `s18-after-query-48ab13-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- orders: `6`
- cutSeconds: `20`
- regions: `[CINA, INDIA, AUSTRALIA_NZ]`
- esLink: `apac-to-es-pg-eu`
- outcomeStoreEnv: `jdbc (default)`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| the residual window was hit | **AS_EXPECTED** | at least one payment committed while its server could not store its event |  | 2 of 2 payments committed before the cut had no event at the heal: [s18-after-query-48ab13-slowpay-australia-nz-3, s18-after-query-48ab13-slowpay-india-2] |
| a payment whose event could not be stored is not executed again | **AS_EXPECTED** | 0 extra executions: the retry is answered from the outcome recorded with the payment (G5 inbox) | the saga's retry runs the payment again (G5 residual window): attempts = 2 | 0 extra execution(s) among the 2 in the window; 0 overall; attempts {s18-after-query-48ab13-slowpay-australia-nz-3=1, s18-after-query-48ab13-slowpay-india-2=1, s18-after-query-48ab13-slowpay-cina-1=1, s18-after-query-48ab13-slowpay-india-5=1, s18-after-query-48ab13-slowpay-cina-4=1, s18-after-query-48ab13-slowpay-australia-nz-6=1} |
| every payment is stored exactly once | **AS_EXPECTED** | one PaymentProcessedEvent per order once the saga's retries are done |  | 0 order(s) without the event after 300 s, 0 with two; events {s18-after-query-48ab13-slowpay-cina-1=1, s18-after-query-48ab13-slowpay-india-2=1, s18-after-query-48ab13-slowpay-australia-nz-3=1, s18-after-query-48ab13-slowpay-cina-4=1, s18-after-query-48ab13-slowpay-india-5=1, s18-after-query-48ab13-slowpay-australia-nz-6=1} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "paidBeforeCut" : 2,
  "eventStoredBeforeCut" : 0,
  "residualWindowHits" : 2,
  "convergedSecondsAfterCut" : 35,
  "paymentAttempts" : {
    "s18-after-query-48ab13-slowpay-australia-nz-3" : 1,
    "s18-after-query-48ab13-slowpay-india-2" : 1,
    "s18-after-query-48ab13-slowpay-cina-1" : 1,
    "s18-after-query-48ab13-slowpay-india-5" : 1,
    "s18-after-query-48ab13-slowpay-cina-4" : 1,
    "s18-after-query-48ab13-slowpay-australia-nz-6" : 1
  },
  "paymentEvents" : {
    "s18-after-query-48ab13-slowpay-cina-1" : 1,
    "s18-after-query-48ab13-slowpay-india-2" : 1,
    "s18-after-query-48ab13-slowpay-australia-nz-3" : 1,
    "s18-after-query-48ab13-slowpay-cina-4" : 1,
    "s18-after-query-48ab13-slowpay-india-5" : 1,
    "s18-after-query-48ab13-slowpay-australia-nz-6" : 1
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

- +7,8 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +66,3 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +66,3 s: scenario start
- +81,1 s: created 6 orders with an item in [CINA, INDIA, AUSTRALIA_NZ]
- +82,5 s: completed them: the saga pays each one (the handler answers 4000 ms after its commit)
- +83,9 s: cut apac-to-es-pg-eu with 2 of 6 payments committed, 2 of them still answering: [s18-after-query-48ab13-slowpay-australia-nz-3, s18-after-query-48ab13-slowpay-india-2]
- +104,2 s: healed apac-to-es-pg-eu after 20 s
- +125,3 s: cleanup start
- +127,1 s: cleanup done

### log excerpts: order-command-apac

```
2026-10-15T14:28:51.171Z  INFO 1 --- [order-command] [    virtual-120] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_AUSTRALIA_NZ:55:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-15T14:28:23.261375Z
2026-10-15T14:28:51.867Z  INFO 1 --- [order-command] [    virtual-122] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_INDIA:58:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-15T14:28:24.491865Z
2026-10-15T14:28:52.101Z  INFO 1 --- [order-command] [    virtual-124] c.e.application.manager.ServiceManager   : event=service_outcome_replayed bundle=order-command commandType=ProcessPaymentCommand idempotencyKey=evento:order-saga_PaymentSaga_1_CINA:54:ProcessPaymentCommand:0 stage=before_handler recordedAt=2026-10-15T14:28:24.659878Z
```

### stack actions

```
2026-10-15T14:27:08.642558400Z docker restart gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
2026-10-15T14:28:24.654781300Z disable apac-to-es-pg-eu
2026-10-15T14:28:44.951273900Z enable apac-to-es-pg-eu
2026-10-15T14:29:06.074703Z enable apac-to-es-pg-eu
```

