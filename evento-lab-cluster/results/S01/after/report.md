# S01: Cross-cell routing miss

- Label: `after`
- Gaps exercised: G1
- Started: 2026-09-29T14:16:06.934051Z, finished: 2026-09-29T14:16:37.041375200Z (30 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-after-ef4c01-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 14 ms |

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
  "americaMaxLatencyMs" : 14,
  "logMarkers" : {
    "evento-am" : {
      "Skipping unregister of bundle" : 1
    },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.878
}
```

## Timeline

- +8,9 s: scenario start
- +9,5 s: stopped order-command-am
- +20,1 s: sent 5 CreateOrder from AMERICA and EUROPA
- +20,3 s: cleanup start
- +30,1 s: cleanup done

### log excerpts: evento-am

```
20260929141616806 INFO  1 --- [nioEventLoopGroup-3-8                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: gateway-america

```
2026-09-29T14:16:26.465Z  WARN 1 --- [order-gateway] [cat-handler-168] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-1 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=b217924f-e2a7-4256-9eb1-afdfd52b986e error=no handler for CreateOrderCommand
2026-09-29T14:16:26.581Z  WARN 1 --- [order-gateway] [cat-handler-169] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-3 region=AMERICA kind=no_handler latencyMs=4 idempotencyKey=aff1d2b4-4b54-4bb6-bf92-4466d9ac69fe error=no handler for CreateOrderCommand
2026-09-29T14:16:26.709Z  WARN 1 --- [order-gateway] [cat-handler-170] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=c6b180f1-e60c-4f5a-a286-4708e8436557 error=no handler for CreateOrderCommand
2026-09-29T14:16:26.465Z  WARN 1 --- [order-gateway] [cat-handler-168] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-1 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=b217924f-e2a7-4256-9eb1-afdfd52b986e error=no handler for CreateOrderCommand
2026-09-29T14:16:26.581Z  WARN 1 --- [order-gateway] [cat-handler-169] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-3 region=AMERICA kind=no_handler latencyMs=4 idempotencyKey=aff1d2b4-4b54-4bb6-bf92-4466d9ac69fe error=no handler for CreateOrderCommand
2026-09-29T14:16:26.709Z  WARN 1 --- [order-gateway] [cat-handler-170] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-ef4c01-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=c6b180f1-e60c-4f5a-a286-4708e8436557 error=no handler for CreateOrderCommand
```

### stack actions

```
2026-09-29T14:16:15.809775500Z docker stop order-command-am
2026-09-29T14:16:27.274262800Z docker start order-command-am (restore)
```

