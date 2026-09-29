# S01: Cross-cell routing miss

- Label: `before`
- Gaps exercised: G1
- Started: 2026-09-29T08:46:40.231816700Z, finished: 2026-09-29T08:47:10.439557Z (30 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-before-bbc9c6-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 37 ms |

- _AMERICA command with no handler in its cell_: The cell architecture exists because of this: every cell must host a full handler set.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "americaKinds" : {
    "no_handler" : 5
  },
  "americaErrors" : [ "no handler for CreateOrderCommand" ],
  "americaMaxLatencyMs" : 37,
  "logMarkers" : {
    "evento-am" : { },
    "region-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 1.3
}
```

## Timeline

- +3,8 s: scenario start
- +5,2 s: stopped cell-core-am
- +16,0 s: sent 5 CreateOrder from AMERICA and EUROPA
- +16,2 s: cleanup start
- +30,2 s: cleanup done

### log excerpts: region-america

```
2026-09-29T08:46:55.519Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-53] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-1 region=AMERICA kind=no_handler latencyMs=7 idempotencyKey=ab3b9b54-9f0d-4122-957b-5df5018a9684 error=no handler for CreateOrderCommand
2026-09-29T08:46:55.747Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-54] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-3 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=a325f3c9-fbbe-40d0-a8c0-5d327d7b41c2 error=no handler for CreateOrderCommand
2026-09-29T08:46:55.869Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-55] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=97ed59d5-a76b-49af-8c41-b952d54dba17 error=no handler for CreateOrderCommand
2026-09-29T08:46:55.519Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-53] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-1 region=AMERICA kind=no_handler latencyMs=7 idempotencyKey=ab3b9b54-9f0d-4122-957b-5df5018a9684 error=no handler for CreateOrderCommand
2026-09-29T08:46:55.747Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-54] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-3 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=a325f3c9-fbbe-40d0-a8c0-5d327d7b41c2 error=no handler for CreateOrderCommand
2026-09-29T08:46:55.869Z  WARN 1 --- [evento-lab-cluster-app] [mcat-handler-55] c.e.l.c.web.OrderGatewayController       : event=gateway_command_failed op=create orderId=s01-before-bbc9c6-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=97ed59d5-a76b-49af-8c41-b952d54dba17 error=no handler for CreateOrderCommand
```

### stack actions

```
2026-09-29T08:46:44.071658800Z docker stop cell-core-am
2026-09-29T08:46:56.474058Z docker start cell-core-am (restore)
```

